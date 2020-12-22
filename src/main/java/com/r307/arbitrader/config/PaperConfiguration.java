package com.r307.arbitrader.config;

/**
 * Configuration that governs the application's paper trading
 */
public class PaperConfiguration {

    private Boolean autoFill = false;

    public Boolean isAutoFill() {
        return autoFill;
    }

    public void setAutoFill(Boolean autoFill) {
        this.autoFill = autoFill;
    }
}
