package com.avispl.symphony.dal.communicator.data.dto.storage;

import java.util.List;

public class StorageCollection {
    private StorageDetails internal;
    private List<StorageDetails> external;

    /**
     * Retrieves {@link #internal}
     *
     * @return value of {@link #internal}
     */
    public StorageDetails getInternal() {
        return internal;
    }

    /**
     * Sets {@link #internal} value
     *
     * @param internal new value of {@link #internal}
     */
    public void setInternal(StorageDetails internal) {
        this.internal = internal;
    }

    /**
     * Retrieves {@link #external}
     *
     * @return value of {@link #external}
     */
    public List<StorageDetails> getExternal() {
        return external;
    }

    /**
     * Sets {@link #external} value
     *
     * @param external new value of {@link #external}
     */
    public void setExternal(List<StorageDetails> external) {
        this.external = external;
    }
}
