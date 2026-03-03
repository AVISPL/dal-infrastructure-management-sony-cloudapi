package com.avispl.symphony.dal.communicator.data.dto.apk;

import java.util.List;

public class Apk {
    private List<ApkEntry> apks;

    /**
     * Retrieves {@link #apks}
     *
     * @return value of {@link #apks}
     */
    public List<ApkEntry> getApks() {
        return apks;
    }

    /**
     * Sets {@link #apks} value
     *
     * @param apks new value of {@link #apks}
     */
    public void setApks(List<ApkEntry> apks) {
        this.apks = apks;
    }
}
