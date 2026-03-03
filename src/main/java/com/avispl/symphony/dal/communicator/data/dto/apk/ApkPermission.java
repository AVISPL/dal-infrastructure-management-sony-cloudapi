package com.avispl.symphony.dal.communicator.data.dto.apk;

public class ApkPermission {
    private String permission;
    private String status;

    /**
     * Retrieves {@link #permission}
     *
     * @return value of {@link #permission}
     */
    public String getPermission() {
        return permission;
    }

    /**
     * Sets {@link #permission} value
     *
     * @param permission new value of {@link #permission}
     */
    public void setPermission(String permission) {
        this.permission = permission;
    }

    /**
     * Retrieves {@link #status}
     *
     * @return value of {@link #status}
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets {@link #status} value
     *
     * @param status new value of {@link #status}
     */
    public void setStatus(String status) {
        this.status = status;
    }
}
