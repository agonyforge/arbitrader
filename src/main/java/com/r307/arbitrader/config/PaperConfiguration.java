package com.r307.arbitrader.config;

/**
 * Configuration that governs the application's paper trading
 */
public class PaperConfiguration {
    private Boolean active = true;
    private Boolean autoFill = false;

    public Boolean isActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean isAutoFill() {
        return autoFill;
    }

    public void setAutoFill(Boolean autoFill) {
        this.autoFill = autoFill;
    }
}
