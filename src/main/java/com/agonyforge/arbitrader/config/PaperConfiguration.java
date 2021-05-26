package com.agonyforge.arbitrader.config;

import java.math.BigDecimal;

/**
 * Configuration that governs the application's paper trading
 */
public class PaperConfiguration {
    private Boolean active = true;
    private Boolean autoFill = false;
    private BigDecimal initialBalance = new BigDecimal(100);

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

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }
}
