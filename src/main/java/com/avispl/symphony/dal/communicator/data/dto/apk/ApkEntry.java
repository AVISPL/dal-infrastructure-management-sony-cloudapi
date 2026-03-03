package com.avispl.symphony.dal.communicator.data.dto.apk;

import java.util.List;

public class ApkEntry {
    private String packageName;
    private String versionCode;
    private String versionName;
    private String appName;
    private List<ApkPermission> permissions;

    /**
     * Retrieves {@link #packageName}
     *
     * @return value of {@link #packageName}
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * Sets {@link #packageName} value
     *
     * @param packageName new value of {@link #packageName}
     */
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    /**
     * Retrieves {@link #versionCode}
     *
     * @return value of {@link #versionCode}
     */
    public String getVersionCode() {
        return versionCode;
    }

    /**
     * Sets {@link #versionCode} value
     *
     * @param versionCode new value of {@link #versionCode}
     */
    public void setVersionCode(String versionCode) {
        this.versionCode = versionCode;
    }

    /**
     * Retrieves {@link #versionName}
     *
     * @return value of {@link #versionName}
     */
    public String getVersionName() {
        return versionName;
    }

    /**
     * Sets {@link #versionName} value
     *
     * @param versionName new value of {@link #versionName}
     */
    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    /**
     * Retrieves {@link #appName}
     *
     * @return value of {@link #appName}
     */
    public String getAppName() {
        return appName;
    }

    /**
     * Sets {@link #appName} value
     *
     * @param appName new value of {@link #appName}
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    /**
     * Retrieves {@link #permissions}
     *
     * @return value of {@link #permissions}
     */
    public List<ApkPermission> getPermissions() {
        return permissions;
    }

    /**
     * Sets {@link #permissions} value
     *
     * @param permissions new value of {@link #permissions}
     */
    public void setPermissions(List<ApkPermission> permissions) {
        this.permissions = permissions;
    }
}
