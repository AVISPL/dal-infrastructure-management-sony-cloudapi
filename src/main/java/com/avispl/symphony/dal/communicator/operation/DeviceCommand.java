package com.avispl.symphony.dal.communicator.operation;

import com.avispl.symphony.dal.communicator.data.Constant;

import java.util.Arrays;
import java.util.Optional;

public enum DeviceCommand {
    MUTE(Constant.Property.MUTE, Constant.URI.OPERATION_MUTE, "mute"),
    PRO_MODE(Constant.Property.PRO_MODE, Constant.URI.OPERATION_PRO_MODE, "mode"),
    REBOOT(Constant.Property.REBOOT, Constant.URI.OPERATION_REBOOT, null),
    SCREEN_STATE(Constant.Property.SCREN_STATE, Constant.URI.OPERATION_SCREEN_STATUS, "screen"),
    SCREEN_ROTATION(Constant.Property.SCREN_ROTATION, Constant.URI.OPERATION_SCREEN_ROTATION, "rotation"),
    EXTERNAL_INPUT(Constant.Property.EXTERNAL_INPUT, Constant.URI.OPERATION_EXTERNAL_INPUT, "port");

    private String propertyName;
    private String commandURI;
    private String commandProperty;

    DeviceCommand(String propertyName, String operationURL, String operationProperty) {
        this.commandURI = operationURL;
        this.propertyName = propertyName;
        this.commandProperty = operationProperty;
    }

    /**
     * Retrieves {@link #propertyName}
     *
     * @return value of {@link #propertyName}
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * Sets {@link #propertyName} value
     *
     * @param propertyName new value of {@link #propertyName}
     */
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    /**
     * Retrieves {@link #commandURI}
     *
     * @return value of {@link #commandURI}
     */
    public String getCommandURI() {
        return commandURI;
    }

    /**
     * Sets {@link #commandURI} value
     *
     * @param commandURI new value of {@link #commandURI}
     */
    public void setCommandURI(String commandURI) {
        this.commandURI = commandURI;
    }

    /**
     * Retrieves {@link #commandProperty}
     *
     * @return value of {@link #commandProperty}
     */
    public String getCommandProperty() {
        return commandProperty;
    }

    /**
     * Sets {@link #commandProperty} value
     *
     * @param commandProperty new value of {@link #commandProperty}
     */
    public void setCommandProperty(String commandProperty) {
        this.commandProperty = commandProperty;
    }

    public static Object filterOperationPropertyValue(DeviceCommand command, Object value) {
        switch (command) {
            case MUTE:
            case SCREEN_STATE:
                return "1".equals(value) ? Constant.Property.ON : Constant.Property.OFF;
            default:
                return value;
        }
    }

    public static DeviceCommand findCommand(String propertyName) {
        Optional<DeviceCommand> command = Arrays.stream(values()).filter(deviceCommand -> propertyName.equals(deviceCommand.getPropertyName())).findFirst();
        if (command.isPresent()) {
            return command.get();
        }
        throw new IllegalArgumentException("Unable to find command with name " + propertyName);
    }
}
