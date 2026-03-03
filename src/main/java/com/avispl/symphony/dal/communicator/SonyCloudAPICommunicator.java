/*
 * Copyright (c) 2023 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;
import com.avispl.symphony.dal.aggregator.parser.AggregatedDeviceProcessor;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMapping;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMappingParser;
import com.avispl.symphony.dal.communicator.data.Constant;
import com.avispl.symphony.dal.communicator.data.dto.*;
import com.avispl.symphony.dal.communicator.data.dto.apk.Apk;
import com.avispl.symphony.dal.communicator.data.dto.apk.ApkEntry;
import com.avispl.symphony.dal.communicator.data.dto.apk.ApkPermission;
import com.avispl.symphony.dal.communicator.data.dto.storage.Storage;
import com.avispl.symphony.dal.communicator.data.dto.storage.StorageCollection;
import com.avispl.symphony.dal.communicator.data.dto.storage.StorageDetails;
import com.avispl.symphony.dal.communicator.operation.DeviceCommand;
import com.avispl.symphony.dal.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.CollectionUtils;

import javax.security.auth.login.FailedLoginException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.avispl.symphony.dal.util.ControllablePropertyFactory.*;

/**
 * Sony Cloud API Communicator.
 * The functionality includes:
 * - Fetching basic device details
 * - Device controls
 * - Device properties and capabilities, based on configured filters
 *
 * @author Maksym.Rossiitsev/AVISPL Team
 * @since 1.0.0
 */
public class SonyCloudAPICommunicator extends RestCommunicator implements Aggregator, Monitorable, Controller {

    private AggregatedDeviceProcessor aggregatedDeviceProcessor;
    /**
     * Adapter metadata, collected from the version.properties
     */
    private Properties adapterProperties;

    /**
     * How much time last monitoring cycle took to finish
     * */
    private Long lastMonitoringCycleDuration;

    /**
     * If the {@link SonyCloudAPICommunicator#deviceMetaDataRetrievalTimeout} is set to a value that is too small -
     * devices list will be fetched too frequently. In order to avoid this - the minimal value is based on this value.
     */
    private static final long defaultMetaDataTimeout = 60 * 1000 / 2;

    /**
     * Device metadata retrieval timeout. The general devices list is retrieved once during this time period.
     */
    private long deviceMetaDataRetrievalTimeout = 60 * 1000 / 2;

    /**
     * */
    private int webhookPort = 8080;

    /**
     * Time period within which the device metadata (basic devices information) cannot be refreshed.
     * Ignored if device list is not yet retrieved or the cached device list is empty {@link SonyCloudAPICommunicator#aggregatedDevices}
     */
    private volatile long validDeviceMetaDataRetrievalPeriodTimestamp;

    /**
     * We don't want the statistics to be collected constantly, because if there's not a big list of devices -
     * new devices statistics loop will be launched before the next monitoring iteration. To avoid that -
     * this variable stores a timestamp which validates it, so when the devices statistics is done collecting, variable
     * is set to currentTime + 30s, at the same time, calling {@link #retrieveMultipleStatistics()} and updating the
     * {@link #aggregatedDevices} resets it to the currentTime timestamp, which will re-activate data collection.
     */
    private volatile long nextDevicesCollectionIterationTimestamp;

    /**
     * This parameter holds timestamp of when we need to stop performing API calls
     * It used when device stop retrieving statistic. Updated each time of called #retrieveMultipleStatistics
     */
    private volatile long validRetrieveStatisticsTimestamp;

    /**
     * Aggregator inactivity timeout. If the {@link SonyCloudAPICommunicator#retrieveMultipleStatistics()}  method is not
     * called during this period of time - device is considered to be paused, thus the Cloud API
     * is not supposed to be called
     */
    private static final long retrieveStatisticsTimeOut = 180000;

    private int executorServiceThreadCount = 8;

    /**
     * Device adapter instantiation timestamp.
     */
    private long adapterInitializationTimestamp;

    private List<String> includeDeviceProperties;

    private String serviceProviderId;
    private String accessKey;

    String lastWebhookReceived = "None";

    /**
     * Pool for keeping all the async operations in, to track any operations in progress and cancel them if needed
     */
    private List<Future> devicesExecutionPool = new ArrayList<>();

    /**
     * Devices this aggregator is responsible for
     * Data is cached and retrieved every {@link #defaultMetaDataTimeout}
     */
    private ConcurrentHashMap<String, AggregatedDevice> aggregatedDevices = new ConcurrentHashMap<>();

    /**
     * Indicates whether a device is considered as paused.
     * True by default so if the system is rebooted and the actual value is lost -> the device won't start stats
     * collection unless the {@link SonyCloudAPICommunicator#retrieveMultipleStatistics()} method is called which will change it
     * to a correct value
     */
    private volatile boolean devicePaused = true;

    /**
     * Executor that runs all the async operations, that {@link #deviceDataLoader} is posting and
     * {@link #devicesExecutionPool} is keeping track of
     */
    private ExecutorService executorService;

    /**
     * Runner service responsible for collecting data and posting processes to {@link #devicesExecutionPool}
     */
    private SonyCloudAPIDataLoader deviceDataLoader;

    /**
     * Process that is running constantly and triggers collecting data from Logi Sync Cloud API endpoints, based on the given timeouts and thresholds.
     *
     * @author Maksym.Rossiytsev
     * @since 1.0.0
     */
    public class SonyCloudAPIDataLoader implements Runnable {
        private volatile boolean inProgress;
        public SonyCloudAPIDataLoader() {
            logDebugMessage("Creating new device data loader.");

            inProgress = true;
        }

        @Override
        public void run() {
            logDebugMessage("Entering device data loader active stage.");
            mainloop:
            while (inProgress) {
                long startCycle = System.currentTimeMillis();
                try {
                    try {
                        TimeUnit.MILLISECONDS.sleep(500);
                    } catch (InterruptedException e) {
                        // Ignore for now
                    }

                    if (!inProgress) {
                        logDebugMessage("Main data collection thread is not in progress, breaking.");
                        break mainloop;
                    }

                    updateAggregatorStatus();
                    // next line will determine whether Logi Sync Cloud monitoring was paused
                    if (devicePaused) {
                        logDebugMessage("The device communicator is paused, data collector is not active.");
                        continue mainloop;
                    }
                    try {
                        logDebugMessage("Fetching devices list.");
                        fetchDevices();
                    } catch (Exception e) {
                        Throwable cause = e.getCause();
                        logger.error("Error occurred during device list retrieval: " + e.getMessage(), e);
                    }


                    for (AggregatedDevice aggregatedDevice : aggregatedDevices.values()) {
                        if (!inProgress) {
                            logDebugMessage("The data collection thread is not in progress. Breaking the data update loop.");
                            break;
                        }
                        if (executorService == null) {
                            logDebugMessage("Executor service reference is null. Breaking the execution.");
                            break;
                        }
                        devicesExecutionPool.add(executorService.submit(() -> {
                            try {
                                // We need to only work with rooms here
                                fetchOnlineStatus(aggregatedDevice);

                                fetchAPKInformation(aggregatedDevice);
                                fetchInputsInformation(aggregatedDevice);
                                fetchMuteState(aggregatedDevice);
                                fetchProModeSettings(aggregatedDevice);
                                fetchScreenRotation(aggregatedDevice);
                                fetchScreenState(aggregatedDevice);
                                fetchStoragesStatus(aggregatedDevice);
                                fetchVolume(aggregatedDevice);
                            } catch (Exception e) {
                                logger.error(String.format("Exception during Zoom Room '%s' data processing.", aggregatedDevice.getDeviceName()), e);
                            }
                        }));
                    }
                    do {
                        try {
                            TimeUnit.MILLISECONDS.sleep(500);
                        } catch (InterruptedException e) {
                            logger.error("Interrupted exception during main loop execution", e);
                            if (!inProgress) {
                                logDebugMessage("Breaking after the main loop execution");
                                break;
                            }
                        }
                        devicesExecutionPool.removeIf(Future::isDone);
                    } while (!devicesExecutionPool.isEmpty());

                    if (!inProgress) {
                        logDebugMessage("The data collection thread is not in progress. Breaking the loop.");
                        break mainloop;
                    }

                    int aggregatedDevicesCount = aggregatedDevices.size();
                    if (aggregatedDevicesCount == 0) {
                        logDebugMessage("No devices collected in the main data collection thread so far. Continuing.");
                        continue mainloop;
                    }

                    while (nextDevicesCollectionIterationTimestamp > System.currentTimeMillis()) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(1000);
                        } catch (InterruptedException e) {
                            //
                        }
                    }
                    // We don't want to fetch devices statuses too often, so by default it's currentTime + 30s
                    // otherwise - the variable is reset by the retrieveMultipleStatistics() call, which
                    // launches devices detailed statistics collection
                    nextDevicesCollectionIterationTimestamp = System.currentTimeMillis() + 30000;

                    lastMonitoringCycleDuration = (System.currentTimeMillis() - startCycle) / 1000;
                    logDebugMessage("Finished collecting devices statistics cycle at " + new Date() + ", total duration: " + lastMonitoringCycleDuration);
                } catch (Exception e) {
                    logger.error("Unexpected error occurred during main device collection cycle", e);
                }
            }
            logDebugMessage("Main device collection loop is completed, in progress marker: " + inProgress);
            // Finished collecting
        }

        /**
         * Triggers main loop to stop
         */
        public void stop() {
            logDebugMessage("Main device details collection loop is stopped!");
            inProgress = false;
        }

        /**
         * Retrieves {@link #inProgress}
         *
         * @return value of {@link #inProgress}
         */
        public boolean isInProgress() {
            return inProgress;
        }
    }

    public SonyCloudAPICommunicator() throws IOException {
        Map<String, PropertiesMapping> mapping = new PropertiesMappingParser().loadYML("mapping/model-mapping.yml", getClass());
        aggregatedDeviceProcessor = new AggregatedDeviceProcessor(mapping);
        adapterProperties = new Properties();
        adapterProperties.load(getClass().getResourceAsStream("/version.properties"));


        executorService = Executors.newFixedThreadPool(executorServiceThreadCount);
        executorService.submit(deviceDataLoader = new SonyCloudAPIDataLoader());
    }

    /**
     * Retrieves {@link #webhookPort}
     *
     * @return value of {@link #webhookPort}
     */
    public int getWebhookPort() {
        return webhookPort;
    }

    /**
     * Sets {@link #webhookPort} value
     *
     * @param webhookPort new value of {@link #webhookPort}
     */
    public void setWebhookPort(int webhookPort) {
        this.webhookPort = webhookPort;
    }

    /**
     * Retrieves {@link #includeDeviceProperties}
     *
     * @return value of {@link #includeDeviceProperties}
     */
    public String getIncludeDeviceProperties() {
        return String.join(",",includeDeviceProperties);
    }

    /**
     * Sets {@link #includeDeviceProperties} value
     *
     * @param includeDeviceProperties new value of {@link #includeDeviceProperties}
     */
    public void setIncludeDeviceProperties(String includeDeviceProperties) {
        this.includeDeviceProperties = Arrays.stream(includeDeviceProperties.split(",")).map(String::trim).collect(Collectors.toList());;
    }

    /**
     * Retrieves {@link #serviceProviderId}
     *
     * @return value of {@link #serviceProviderId}
     */
    public String getServiceProviderId() {
        return serviceProviderId;
    }

    /**
     * Sets {@link #serviceProviderId} value
     *
     * @param serviceProviderId new value of {@link #serviceProviderId}
     */
    public void setServiceProviderId(String serviceProviderId) {
        this.serviceProviderId = serviceProviderId;
    }

    @Override
    protected void internalInit() throws Exception {
        logDebugMessage("Internal init is called.");
        long currentTimestamp = System.currentTimeMillis();
        adapterInitializationTimestamp = currentTimestamp;
        setBaseUri(Constant.URI.BASE_URI);
        validDeviceMetaDataRetrievalPeriodTimestamp = currentTimestamp;

//        try {
//            // Create an HttpServer instance
//            HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", webhookPort), 0);
//
//            // Create the request handler for webhook requests
//            server.createContext("/webhook", new WebhookHandler());
//
//            // Start the server
//            System.out.println("Starting server on port " + webhookPort);
//            server.start();
//        } catch (IOException e) {
//            System.err.println("Error starting the server: " + e.getMessage());
//        }
//        serviceRunning = true;
        super.internalInit();
    }

    @Override
    public void controlProperty(ControllableProperty controllableProperty) throws Exception {
        String deviceId = controllableProperty.getDeviceId();
        String property = controllableProperty.getProperty();
        Object value = controllableProperty.getValue();

        DeviceCommand deviceCommand = DeviceCommand.findCommand(property);
        String commandProperty = deviceCommand.getCommandProperty();

        String commandURI = deviceCommand.getCommandURI();
        if (commandProperty != null) {
            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put(commandProperty, DeviceCommand.filterOperationPropertyValue(deviceCommand, value));
            doPost(String.format(commandURI, deviceId), requestPayload);
        } else {
            doPost(String.format(commandURI, deviceId), null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void controlProperties(List<ControllableProperty> controllablePropertyList) throws Exception {
        if (CollectionUtils.isEmpty(controllablePropertyList)) {
            throw new IllegalArgumentException("Controllable properties cannot be null or empty");
        }
        for (ControllableProperty controllableProperty : controllablePropertyList) {
            controlProperty(controllableProperty);
        }
    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        ExtendedStatistics extendedStatistics = new ExtendedStatistics();
        Map<String, String> properties = new HashMap<>();
        extendedStatistics.setStatistics(properties);

        Map<String, String> statistics = new HashMap<>();
        Map<String, String> dynamicStatistics = new HashMap<>();

        dynamicStatistics.put(Constant.Property.MONITORED_DEVICES_TOTAL, String.valueOf(aggregatedDevices.size()));
        if (lastMonitoringCycleDuration != null) {
            dynamicStatistics.put(Constant.Property.MONITORING_CYCLE_DURATION, String.valueOf(lastMonitoringCycleDuration));
        }

        statistics.put(Constant.Property.ADAPTER_VERSION, adapterProperties.getProperty("aggregator.version"));
        statistics.put(Constant.Property.ADAPTER_BUILD_DATE, adapterProperties.getProperty("aggregator.build.date"));
        statistics.put("LastWebhookReceived", lastWebhookReceived);

        long adapterUptime = System.currentTimeMillis() - adapterInitializationTimestamp;
        statistics.put(Constant.Property.ADAPTER_UPTIME_MIN, String.valueOf(adapterUptime / (1000*60)));
        statistics.put(Constant.Property.ADAPTER_UPTIME, normalizeUptime(adapterUptime/1000));

        extendedStatistics.setStatistics(statistics);
        extendedStatistics.setDynamicStatistics(dynamicStatistics);
        return Collections.singletonList(extendedStatistics);
    }

    @Override
    public List<AggregatedDevice> retrieveMultipleStatistics() throws Exception {
//        if (latestError != null) {
//            throw latestError;
//        }
        nextDevicesCollectionIterationTimestamp = System.currentTimeMillis();
        updateValidRetrieveStatisticsTimestamp();

        List<AggregatedDevice> aggregatedDeviceList = new ArrayList<>(aggregatedDevices.values());
        for(AggregatedDevice aggregatedDevice: aggregatedDeviceList) {
            aggregatedDevice.setTimestamp(System.currentTimeMillis());
        }

        return aggregatedDeviceList;
    }

    @Override
    public List<AggregatedDevice> retrieveMultipleStatistics(List<String> list) throws Exception {
        return retrieveMultipleStatistics();
    }

    @Override
    protected void authenticate() throws Exception {
        updateAccessKey();
    }

    @Override
    protected HttpHeaders putExtraRequestHeaders(HttpMethod httpMethod, String uri, HttpHeaders headers) throws Exception {
        headers.add("x-api-key", getPassword());
        if (!uri.contains(Constant.URI.ACCESS_KEY_URI)) {
            if (StringUtils.isNullOrEmpty(accessKey)) {
                authenticate();
            }
            headers.add("x-access-key", accessKey);
        }
        return super.putExtraRequestHeaders(httpMethod, uri, headers);
    }

    private void fetchDevices() throws Exception {
        long currentTimestamp = System.currentTimeMillis();
        if (validDeviceMetaDataRetrievalPeriodTimestamp > currentTimestamp) {
            logDebugMessage(String.format("General devices metadata retrieval is in cooldown. %s seconds left",
                    (validDeviceMetaDataRetrievalPeriodTimestamp - currentTimestamp) / 1000));
            return;
        }
        validDeviceMetaDataRetrievalPeriodTimestamp = currentTimestamp + deviceMetaDataRetrievalTimeout;

        JsonNode devicesResponse = doGet(Constant.URI.DEVICES_URI, JsonNode.class);
        List<AggregatedDevice> aggregatedDeviceList = aggregatedDeviceProcessor.extractDevices(devicesResponse);
        List<String> retrievedIds = aggregatedDeviceList.stream().map(AggregatedDevice::getDeviceId).collect(Collectors.toList());
        // Remove rooms that were not populated by the API
        if (retrievedIds.isEmpty()) {
            // If all the devices were not populated for any specific reason (no devices available, filtering, etc)
            aggregatedDevices.clear();
        }
        aggregatedDevices.keySet().removeIf(deviceId -> !retrievedIds.contains(deviceId));
        aggregatedDeviceList.forEach(aggregatedDevice -> aggregatedDevices.put(aggregatedDevice.getDeviceId(), aggregatedDevice));

        for(JsonNode device: devicesResponse.at(Constant.URI.JSON_DEVICES_URI)) {
            String deviceId = device.at(Constant.URI.JSON_DEVICE_ID).asText();
            JsonNode networkInterfaces = device.at(Constant.URI.JSON_NETWORK_INTERFACES);
            AggregatedDevice aggregatedDevice = aggregatedDevices.get(deviceId);

            if (aggregatedDevice == null) {
                continue;
            }
            Map<String, String> deviceProperties = aggregatedDevice.getProperties();
            Map<String, String> properties = new HashMap<>();
            int interfaceCounter = 1;
            for (JsonNode networkInterface: networkInterfaces) {
                aggregatedDeviceProcessor.applyProperties(properties, networkInterface, "NetworkInterface");
                properties.forEach((key, value) -> deviceProperties.put(String.format("NetworkInterface[%s]#%s", interfaceCounter, key), value));
            }
        }
    }

    /**
     *
     * */
    private void updateAccessKey() throws Exception {
        AccessKey key = doPost(Constant.URI.ACCESS_KEY_URI, null, AccessKey.class);
        if (key == null) {
            throw new FailedLoginException("Access key retrieval request failed: no valid response found.");
        }
        accessKey = key.getAccessKey();
       // attachDeviceToAccessKey("F8-4E-17-6D-40-78");
    }

    private void attachDeviceToAccessKey(String deviceId) throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("deviceId", deviceId);
        JsonNode response = doPost(Constant.URI.ACCESS_KEY_ATTACH_DEVICE, request, JsonNode.class);
    }
    /**
     * Retrieve online status of a given device.
     *
     * @param device to fetch online status for
     * @throws Exception if a communication error occurs
     * */
    private void fetchOnlineStatus(AggregatedDevice device) throws Exception {
        OnlineState onlineStatus = doGet(Constant.URI.ONLINE_STATUS_URI, OnlineState.class);
        device.setDeviceOnline(onlineStatus.isOnline());
    }

    /**
     * Retrieve apk information for a given device.
     *
     * @param device to fetch apk details for
     * @throws Exception if a communication error occurs
     * */
    private void fetchAPKInformation(AggregatedDevice device) throws Exception {
        if (!includeDeviceProperties.contains(Constant.PropertyGroups.APK)) {
            logDebugMessage(String.format("includeDeviceProperties does not contain '%s' entry, skipping apks status retrieval.", Constant.PropertyGroups.APK));
            cleanupStaleProperties(device, Constant.Property.APK);
            return;
        }
        Map<String, String> properties = device.getProperties();

        Apk apksResponse = doGet(Constant.URI.APKS_URI, Apk.class);
        List<ApkEntry> apks = apksResponse.getApks();
        if (apks == null) {
            return;
        }
        apks.forEach(apkEntry -> {
            String appName = apkEntry.getAppName();
            String groupName = String.format(Constant.Property.APK_GROUP_TEMPLATE, appName);
            properties.put(groupName + Constant.Property.APK_APP_NAME, appName);
            properties.put(groupName + Constant.Property.APK_PACKAGE_NAME, apkEntry.getPackageName());
            properties.put(groupName + Constant.Property.APK_VERSION_CODE, apkEntry.getVersionCode());
            properties.put(groupName + Constant.Property.APK_VERSION_NAME, apkEntry.getVersionName());

            List<ApkPermission> permissions = apkEntry.getPermissions();
            List<String> grantedPermissions = new ArrayList<>();
            List<String> deniedPermissions = new ArrayList<>();
            permissions.forEach(apkPermission -> {
                String permissionStatus = apkPermission.getStatus();
                String permission = apkPermission.getPermission();
                if (Constant.Property.GRANTED.equalsIgnoreCase(permissionStatus)) {
                    grantedPermissions.add(permission);
                } else {
                    deniedPermissions.add(permission);
                }
            });
            properties.put(groupName + Constant.Property.PERMISSIONS_GRANTED, String.join(",", grantedPermissions));
            properties.put(groupName + Constant.Property.PERMISSIONS_DENIED, String.join(",", deniedPermissions));
        });
    }

    /**
     * Retrieve inputs information for a given device.
     *
     * @param device to fetch inputs information for
     * @throws Exception if a communication error occurs
     * */
    private void fetchInputsInformation(AggregatedDevice device) throws Exception {
        if (!includeDeviceProperties.contains(Constant.PropertyGroups.EXTERNAL_INPUT)) {
            logDebugMessage(String.format("includeDeviceProperties does not contain '%s' entry, skipping external input status retrieval.", Constant.PropertyGroups.EXTERNAL_INPUT));
            cleanupStaleProperties(device, Constant.Property.EXTERNAL_INPUT);
            return;
        }
        ExternalInput externalInput = doGet(Constant.URI.EXTERNAL_INPUT_SELECTED_URI, ExternalInput.class);
        String inputName = externalInput.getPort();

        device.getProperties().put(Constant.Property.EXTERNAL_INPUT, inputName);
        addControllableProperty(device, createDropdown(Constant.Property.EXTERNAL_INPUT, Constant.Property.EXTERNAL_INPUT_OPTIONS, inputName));
    }

    /**
     * Retrieve mute state for a given device.
     *
     * @param device to fetch mute state for
     * @throws Exception if a communication error occurs
     * */
    private void fetchMuteState(AggregatedDevice device) throws Exception {
        if (!includeDeviceProperties.contains(Constant.PropertyGroups.MUTE)) {
            logDebugMessage(String.format("includeDeviceProperties does not contain '%s' entry, skipping mute status retrieval.", Constant.PropertyGroups.MUTE));
            cleanupStaleProperties(device, Constant.Property.MUTE);
            return;
        }
        boolean mute = Constant.Property.ON.equalsIgnoreCase(doGet(Constant.URI.MUTE_STATE_URI, MuteState.class).getMute());
        device.getProperties().put(Constant.Property.MUTE, String.valueOf(mute));
        addControllableProperty(device, createSwitch(Constant.Property.MUTE, mute ? 1 : 0));
    }

    /**
     * Retrieve pro mode settings for a given device.
     *
     * @param device to fetch pro mode settings information for
     * @throws Exception if a communication error occurs
     * */
    private void fetchProModeSettings(AggregatedDevice device) throws Exception {
        if (!includeDeviceProperties.contains(Constant.PropertyGroups.PRO_MODE)) {
            logDebugMessage(String.format("includeDeviceProperties does not contain '%s' entry, skipping pro-mode status retrieval.", Constant.PropertyGroups.PRO_MODE));
            cleanupStaleProperties(device, Constant.Property.PRO_MODE);
            return;
        }
        ProMode proMode = doGet(Constant.URI.PRO_MODE_SETTINGS_URI, ProMode.class);
        String proModeValue = proMode.getMode();

        device.getProperties().put(Constant.Property.PRO_MODE, proModeValue);
        addControllableProperty(device, createDropdown(Constant.Property.PRO_MODE, Constant.Property.PRO_SETTINGS_OPTIONS, proModeValue));
    }

    /**
     * Retrieve screen state for a given device.
     *
     * @param device to fetch screen state for
     * @throws Exception if a communication error occurs
     * */
    private void fetchScreenState(AggregatedDevice device) throws Exception {
        if (!includeDeviceProperties.contains(Constant.PropertyGroups.SCREEN)) {
            logDebugMessage(String.format("includeDeviceProperties does not contain '%s' entry, skipping screen status retrieval.", Constant.PropertyGroups.SCREEN));
            cleanupStaleProperties(device, Constant.Property.SCREN_STATE);
            return;
        }
        boolean screenOn = Constant.Property.ON.equalsIgnoreCase(doGet(Constant.URI.SCREEN_STATE_URI, ScreenState.class).getScreen());
        device.getProperties().put(Constant.Property.SCREN_STATE, String.valueOf(screenOn));
        addControllableProperty(device, createSwitch(Constant.Property.SCREN_STATE, screenOn ? 1 : 0));
    }

    /**
     * Retrieve screen rotation value for a given device.
     *
     * @param device to fetch screen rotation for
     * @throws Exception if a communication error occurs
     * */
    private void fetchScreenRotation(AggregatedDevice device) throws Exception {
        if (!includeDeviceProperties.contains(Constant.PropertyGroups.SCREEN_ROTATION)) {
            logDebugMessage(String.format("includeDeviceProperties does not contain '%s' entry, skipping screen rotation status retrieval.", Constant.PropertyGroups.SCREEN_ROTATION));
            cleanupStaleProperties(device, Constant.Property.SCREN_ROTATION);
            return;
        }
        ScreenRotation screenRotation = doGet(Constant.URI.SCREEN_ROTATION_URI, ScreenRotation.class);
        int rotationValue = screenRotation.getRotation();
        device.getProperties().put(Constant.Property.SCREN_ROTATION, String.valueOf(rotationValue));
        addControllableProperty(device, createSlider(Constant.Property.SCREN_ROTATION, 0F, 360F, Float.valueOf(rotationValue)));
    }

    /**
     * Retrieve volume information for a given device.
     *
     * @param device to fetch volume for
     * @throws Exception if a communication error occurs
     * */
    private void fetchVolume(AggregatedDevice device) throws Exception {
        if (!includeDeviceProperties.contains(Constant.PropertyGroups.VOLUME)) {
            logDebugMessage(String.format("includeDeviceProperties does not contain '%s' entry, skipping volume status retrieval.", Constant.PropertyGroups.VOLUME));
            cleanupStaleProperties(device, Constant.Property.VOLUME);
            return;
        }
        Volume volume = doGet(Constant.URI.VOLUME_URI, Volume.class);
        int volumeValue = volume.getVolume();
        device.getProperties().put(Constant.Property.VOLUME, String.valueOf(volumeValue));
        addControllableProperty(device, createSlider(Constant.Property.VOLUME, 0F, 100F, Float.valueOf(volumeValue)));
    }

    /**
     * Retrieve storages information for both external and internal storages.
     *
     * @param device to fetch storages details for
     * @throws Exception if a communication error occurs
     * */
    private void fetchStoragesStatus(AggregatedDevice device) throws Exception {
        if (!includeDeviceProperties.contains(Constant.PropertyGroups.STORAGES)) {
            logDebugMessage(String.format("includeDeviceProperties does not contain '%s' entry, skipping storages status retrieval.", Constant.PropertyGroups.STORAGES));
            cleanupStaleProperties(device, Constant.Property.INTERNAL_STORAGE);
            cleanupStaleProperties(device, Constant.Property.EXTERNAL_STORAGE);
            return;
        }
        Map<String, String> properties = device.getProperties();

        Storage storageResponse = doGet(Constant.URI.STORAGES_URI, Storage.class);
        if (storageResponse == null) {
            return;
        }
        StorageCollection storageCollection = storageResponse.getStorages();
        if (storageCollection == null) {
            return;
        }
        StorageDetails internalStorage = storageCollection.getInternal();
        List<StorageDetails> externalStorage = storageCollection.getExternal();
        if (internalStorage != null) {
            properties.put(Constant.Property.INTERNAL_STORAGE_FREE, internalStorage.getFree());
            properties.put(Constant.Property.INTERNAL_STORAGE_TOTAL, internalStorage.getTotal());
        }
        if (externalStorage != null) {
            externalStorage.forEach(storageDetails -> {
                String label = storageDetails.getLabel();
                properties.put(String.format(Constant.Property.EXTERNAL_STORAGE_FREE, label), storageDetails.getFree());
                properties.put(String.format(Constant.Property.EXTERNAL_STORAGE_TOTAL, label), storageDetails.getTotal());
            });
        }
    }

    /**
     * Add new controllable property to a device, if there's no such property yet.
     *
     * @param device to add controllable property to
     * @param controllableProperty to add to the device
     * */
    private void addControllableProperty(AggregatedDevice device, AdvancedControllableProperty controllableProperty) {
        List<AdvancedControllableProperty> controllableProperties = device.getControllableProperties();
        Optional<AdvancedControllableProperty> existingControl = controllableProperties.stream().filter(control -> control.getName().equals(controllableProperty.getName())).findAny();
        if (!existingControl.isPresent()) {
            controllableProperties.add(controllableProperty);
        }
    }

    /**
     * Remove properties and controls that are not relevant anymore
     *
     * @param device device for which properties must be removed
     * @param propertyPrefix property prefix to use as property identifier
     * */
    private void cleanupStaleProperties(AggregatedDevice device, String propertyPrefix) {
        List<AdvancedControllableProperty> controllableProperties = device.getControllableProperties();
        Map<String, String> properties = device.getProperties();

        properties.keySet().removeIf(s -> s.startsWith(propertyPrefix));
        controllableProperties.removeIf(controllableProperty -> controllableProperty.getName().startsWith(propertyPrefix));
    }

    /**
     * Logging debug message with checking if it's enabled first
     *
     * @param message to log
     * */
    private void logDebugMessage(String message) {
        if (logger.isDebugEnabled()) {
            logger.debug(message);
        }
    }

    /**
     * Update the status of the device.
     * The device is considered as paused if did not receive any retrieveMultipleStatistics()
     * calls during {@link SonyCloudAPICommunicator#validRetrieveStatisticsTimestamp}
     */
    private synchronized void updateAggregatorStatus() {
        // If the adapter is destroyed out of order, we need to make sure the device isn't paused here
        if (validRetrieveStatisticsTimestamp > 0L) {
            devicePaused = validRetrieveStatisticsTimestamp < System.currentTimeMillis();
        } else {
            devicePaused = false;
        }
    }

    /**
     * Update statistics retrieval timestamp
     * */
    private synchronized void updateValidRetrieveStatisticsTimestamp() {
        validRetrieveStatisticsTimestamp = System.currentTimeMillis() + retrieveStatisticsTimeOut;
        updateAggregatorStatus();
    }

    /**
     * Uptime is received in seconds, need to normalize it and make it human-readable, like
     * 1 day(s) 5 hour(s) 12 minute(s) 55 minute(s)
     * Incoming parameter is may have a decimal point, so in order to safely process this - it's rounded first.
     * We don't need to add a segment of time if it's 0.
     *
     * @param uptimeSeconds value in seconds
     * @return string value of format 'x day(s) x hour(s) x minute(s) x minute(s)'
     */
    private String normalizeUptime(long uptimeSeconds) {
        StringBuilder normalizedUptime = new StringBuilder();

        long seconds = uptimeSeconds % 60;
        long minutes = uptimeSeconds % 3600 / 60;
        long hours = uptimeSeconds % 86400 / 3600;
        long days = uptimeSeconds / 86400;

        if (days > 0) {
            normalizedUptime.append(days).append(" day(s) ");
        }
        if (hours > 0) {
            normalizedUptime.append(hours).append(" hour(s) ");
        }
        if (minutes > 0) {
            normalizedUptime.append(minutes).append(" minute(s) ");
        }
        if (seconds > 0) {
            normalizedUptime.append(seconds).append(" second(s)");
        }
        return normalizedUptime.toString().trim();
    }
}
