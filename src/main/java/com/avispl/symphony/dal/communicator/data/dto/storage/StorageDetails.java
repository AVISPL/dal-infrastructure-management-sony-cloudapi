package com.avispl.symphony.dal.communicator.data.dto.storage;

public class StorageDetails {
    private String free;
    private String total;
    private String label;

    /**
     * Retrieves {@link #label}
     *
     * @return value of {@link #label}
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets {@link #label} value
     *
     * @param label new value of {@link #label}
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Retrieves {@link #total}
     *
     * @return value of {@link #total}
     */
    public String getTotal() {
        return total;
    }

    /**
     * Sets {@link #total} value
     *
     * @param total new value of {@link #total}
     */
    public void setTotal(String total) {
        this.total = total;
    }

    /**
     * Retrieves {@link #free}
     *
     * @return value of {@link #free}
     */
    public String getFree() {
        return free;
    }

    /**
     * Sets {@link #free} value
     *
     * @param free new value of {@link #free}
     */
    public void setFree(String free) {
        this.free = free;
    }
}
