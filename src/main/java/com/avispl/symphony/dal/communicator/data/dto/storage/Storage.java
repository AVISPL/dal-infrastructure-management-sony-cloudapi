package com.avispl.symphony.dal.communicator.data.dto.storage;

public class Storage {
    private StorageCollection storages;

    /**
     * Retrieves {@link #storages}
     *
     * @return value of {@link #storages}
     */
    public StorageCollection getStorages() {
        return storages;
    }

    /**
     * Sets {@link #storages} value
     *
     * @param storages new value of {@link #storages}
     */
    public void setStorages(StorageCollection storages) {
        this.storages = storages;
    }
}
