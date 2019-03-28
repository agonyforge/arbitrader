package com.r307.arbitrader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static com.r307.arbitrader.DecimalConstants.USD_SCALE;

@ConfigurationProperties("trading")
@Configuration
public class TradingConfiguration {
    private BigDecimal entrySpread;
    private BigDecimal exitTarget;
    private BigDecimal fixedExposure;
    private List<ExchangeConfiguration> exchanges = new ArrayList<>();
    private List<String> tradeBlacklist = new ArrayList<>();

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
}
