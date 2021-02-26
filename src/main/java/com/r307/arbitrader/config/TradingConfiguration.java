package com.r307.arbitrader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static com.r307.arbitrader.DecimalConstants.USD_SCALE;

/**
 * Configuration that governs the application's trading but isn't specific to one exchange. These settings can
 * be set in application.yaml in the "trading" section.
 */
@ConfigurationProperties("trading")
@Configuration
public class TradingConfiguration {
    private BigDecimal entrySpread;
    private BigDecimal exitTarget;
    private Boolean spreadNotifications = false;
    private BigDecimal fixedExposure;
    private List<ExchangeConfiguration> exchanges = new ArrayList<>();
    private List<String> tradeBlacklist = new ArrayList<>();
    private Long tradeTimeout;
    private PaperConfiguration paper;

    public BigDecimal getEntrySpread() {
        return entrySpread;
    }

    public void setEntrySpread(BigDecimal entrySpread) {
        this.entrySpread = entrySpread;
    }

    public BigDecimal getExitTarget() {
        return exitTarget;
    }

    public void setExitTarget(BigDecimal exitTarget) {
        this.exitTarget = exitTarget;
    }

    public Boolean isSpreadNotifications() {
        return spreadNotifications;
    }

    public void setSpreadNotifications(Boolean spreadNotifications) {
        this.spreadNotifications = spreadNotifications;
    }

    public BigDecimal getFixedExposure() {
        return fixedExposure;
    }

    public void setFixedExposure(BigDecimal fixedExposure) {
        this.fixedExposure = fixedExposure.setScale(USD_SCALE, RoundingMode.HALF_EVEN);
    }

    public List<ExchangeConfiguration> getExchanges() {
        return exchanges;
    }

    public void setExchanges(List<ExchangeConfiguration> exchanges) {
        this.exchanges = exchanges;
    }

    public List<String> getTradeBlacklist() {
        return tradeBlacklist;
    }

    public void setTradeBlacklist(List<String> tradeBlacklist) {
        this.tradeBlacklist = tradeBlacklist;
    }

    public Long getTradeTimeout() {
        return tradeTimeout;
    }

    public void setTradeTimeout(Long tradeTimeout) {
        this.tradeTimeout = tradeTimeout;
    }

    public PaperConfiguration getPaper() {
        return paper;
    }

    public void setPaper(PaperConfiguration paper) {
        this.paper = paper;
    }
}
