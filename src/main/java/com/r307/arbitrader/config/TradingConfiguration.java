package com.r307.arbitrader.config;

import org.knowm.xchange.currency.CurrencyPair;
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
    private List<CurrencyPair> tradingPairs;
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

    public BigDecimal getFixedExposure() {
        return fixedExposure;
    }

    public void setFixedExposure(BigDecimal fixedExposure) {
        this.fixedExposure = fixedExposure.setScale(USD_SCALE, RoundingMode.HALF_EVEN);
    }

    public List<CurrencyPair> getTradingPairs() {
        return tradingPairs;
    }

    public void setTradingPairs(List<String> pairStrings) {
        tradingPairs = new ArrayList<>();
        pairStrings.forEach(pair -> tradingPairs.add(new CurrencyPair(pair)));
    }

    public List<ExchangeConfiguration> getExchanges() {
        return exchanges;
    }

    public void setExchanges(List<ExchangeConfiguration> exchanges) {
        this.exchanges = exchanges;
    }
}
