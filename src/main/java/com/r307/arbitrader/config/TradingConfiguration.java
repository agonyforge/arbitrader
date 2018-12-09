package com.r307.arbitrader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties("trading")
@Configuration
public class TradingConfiguration {
    private BigDecimal entrySpread;
    private BigDecimal exitTarget;
    private List<ExchangeConfiguration> exchanges;

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

    public List<ExchangeConfiguration> getExchanges() {
        return exchanges;
    }

    public void setExchanges(List<ExchangeConfiguration> exchanges) {
        this.exchanges = exchanges;
    }
}
