package com.avispl.symphony.dal.communicator.data;

import java.util.Arrays;
import java.util.List;

public interface Constant {
    interface URI {
        String BASE_URI = "/rms/v1";
        String DEVICES_URI = "/devices";
        String ONLINE_STATUS_URI = "/%s/online-status";
        String APKS_URI = "/%s/apks";
        String EXTERNAL_INPUT_SIGNALS_URI = "/%s/external-input-signals";
        String EXTERNAL_INPUT_SELECTED_URI = "/%s/selected-external-input";
        String MUTE_STATE_URI = "/%s/mute";
        String PRO_MODE_SETTINGS_URI = "/%s/pro-mode";
        String SCREEN_STATE_URI = "/%s/screen";
        String SCREEN_ROTATION_URI = "/%s/screen-rotation";
        String VOLUME_URI = "/%s/volume";
        String STORAGES_URI = "/%s/storages";

        String OPERATION_MUTE = "/devices/%s/mute/requests";
        String OPERATION_PRO_MODE = "/devices/%s/pro-mode/requests";
        String OPERATION_REBOOT = "/devices/%s/reboot/requests";
        String OPERATION_SCREEN_STATUS = "/devices/%s/screen/requests";
        String OPERATION_SCREEN_ROTATION = "/devices/%s/screen-rotation/requests";
        String OPERATION_EXTERNAL_INPUT = "/devices/%s/selected-external-input/requests";

        String ACCESS_KEY_URI = "/access-keys";
        String ACCESS_KEY_ATTACH_DEVICE = "/access-keys/%s/devices";

        String JSON_DEVICES_URI = "/devices";
        String JSON_DEVICE_ID = "/deviceId";
        String JSON_NETWORK_INTERFACES = "/networkInterfaces";
    }

    interface Property {
        List<String> PRO_SETTINGS_OPTIONS = Arrays.asList("NORMAL", "PRO_SETTINGS", "PRO");
        List<String> EXTERNAL_INPUT_OPTIONS = Arrays.asList("HDMI1", "HDMI2", "HDMI3", "HDMI4", "AV");
        String PRO_MODE = "ProMode";
        String REBOOT = "Reboot";
        String MUTE = "Mute";
        String SCREN_STATE = "ScreenState";
        String SCREN_ROTATION = "ScreenRotation";
        String VOLUME = "Volume";
        String EXTERNAL_INPUT = "ExternalInput";
        String MONITORED_DEVICES_TOTAL = "MonitoredDevicesTotal";
        String MONITORING_CYCLE_DURATION = "LastMonitoringCycleDuration(s)";
        String ADAPTER_VERSION = "AdapterVersion";
        String ADAPTER_BUILD_DATE = "AdapterBuildDate";
        String ADAPTER_UPTIME_MIN = "AdapterUptime(min)";
        String ADAPTER_UPTIME = "AdapterUptime";
        String ON = "ON";
        String OFF = "OFF";
        String EXTERNAL_STORAGE = "ExternalStorage";
        String INTERNAL_STORAGE = "InternalStorage";
        String INTERNAL_STORAGE_FREE = "InternalStorage#Free";
        String INTERNAL_STORAGE_TOTAL = "InternalStorage#Total";
        String EXTERNAL_STORAGE_TOTAL = "ExternalStorage_%s#Total";
        String EXTERNAL_STORAGE_FREE = "ExternalStorage_%s#Free";
        String PERMISSIONS_GRANTED = "PermissionsGranted";
        String PERMISSIONS_DENIED = "PermissionsDenied";
        String APK_GROUP_TEMPLATE = "APK_%s#";
        String APK_APP_NAME = "AppName";
        String APK_PACKAGE_NAME = "PackageName";
        String APK_VERSION_CODE = "VersionCode";
        String APK_VERSION_NAME = "VersionName";
        String GRANTED = "GRANTED";
        String APK = "APK";
    }

    interface PropertyGroups {
        String APK = "apk";
        String EXTERNAL_INPUT = "external-input";
        String MUTE = "mute";
        String PRO_MODE = "pro-mode";
        String SCREEN = "screen";
        String SCREEN_ROTATION = "screen-rotation";
        String VOLUME = "volume";
        String STORAGES = "storages";
    }
}
