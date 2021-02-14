package com.r307.arbitrader.config;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.r307.arbitrader.config.FeeComputation.SERVER;

/**
 * This class contains any configuration from application.yaml that users can set to change the behavior of a
 * single exchange. An instance of this class is stored on the Exchange so the configuration can be accessed
 * at any time.
 */
public class ExchangeConfiguration {
    private String exchangeClass;
    private String userName;
    private String apiKey;
    private String secretKey;
    private String sslUri;
    private String host;
    private Integer port;
    private Map<String, String> custom = new HashMap<>();
    private List<CurrencyPair> tradingPairs = new ArrayList<>();
    private Boolean margin;
    private List<CurrencyPair> marginExclude = new ArrayList<>();
    private BigDecimal tradeFee;
    private BigDecimal tradeFeeOverride;
    private BigDecimal marginFee;
    private BigDecimal marginFeeOverride;
    private Currency homeCurrency = Currency.USD;
    private Map<String, Integer> ticker = new HashMap<>();
    private List<Object> tickerArguments = new ArrayList<>();
    private FeeComputation feeComputation = SERVER;
    private Boolean active;

    public String getExchangeClass() {
        return exchangeClass;
    }

    public void setExchangeClass(String exchangeClass) {
        this.exchangeClass = exchangeClass;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getSslUri() {
        return sslUri;
    }

    public void setSslUri(String sslUri) {
        this.sslUri = sslUri;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Map<String, String> getCustom() {
        return custom;
    }

    public void setCustom(Map<String, String> custom) {
        this.custom = custom;
    }

    public List<CurrencyPair> getTradingPairs() {
        return tradingPairs;
    }

    public void setTradingPairs(List<CurrencyPair> tradingPairs) {
        this.tradingPairs = tradingPairs;
    }

    public Boolean getMargin() {
        return margin;
    }

    public void setMargin(Boolean margin) {
        this.margin = margin;
    }

    public List<CurrencyPair> getMarginExclude() {
        return marginExclude;
    }

    public void setMarginExclude(List<CurrencyPair> marginExclude) {
        this.marginExclude = marginExclude;
    }

    public BigDecimal getTradeFee() {
        return tradeFee;
    }

    public void setTradeFee(BigDecimal tradeFee) {
        this.tradeFee = tradeFee;
    }

    public BigDecimal getTradeFeeOverride() {
        return tradeFeeOverride;
    }

    public void setTradeFeeOverride(BigDecimal tradeFeeOverride) {
        this.tradeFeeOverride = tradeFeeOverride;
    }

    public Currency getHomeCurrency() {
        return homeCurrency;
    }

    public void setHomeCurrency(Currency homeCurrency) {
        this.homeCurrency = homeCurrency;
    }

    public Map<String, Integer> getTicker() {
        return ticker;
    }

    public void setTicker(Map<String, Integer> ticker) {
        this.ticker = ticker;
    }

    public List<Object> getTickerArguments() {
        return tickerArguments;
    }

    public void setTickerArguments(List<Object> tickerArguments) {
        this.tickerArguments = tickerArguments;
    }

    public FeeComputation getFeeComputation() {
        return feeComputation;
    }

    public void setFeeComputation(FeeComputation feeComputation) {
        this.feeComputation = feeComputation;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public BigDecimal getMarginFee() {
        return marginFee;
    }

    public void setMarginFee(BigDecimal marginFee) {
        this.marginFee = marginFee;
    }

    public BigDecimal getMarginFeeOverride() {
        return marginFeeOverride;
    }

    public void setMarginFeeOverride(BigDecimal marginFeeOverride) {
        this.marginFeeOverride = marginFeeOverride;
    }
}
