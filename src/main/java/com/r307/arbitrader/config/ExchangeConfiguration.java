package com.r307.arbitrader.config;

import org.knowm.xchange.currency.Currency;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class ExchangeConfiguration {
    private String exchangeClass;
    private String userName;
    private String apiKey;
    private String secretKey;
    private Map<String, String> custom = new HashMap<>();
    private Boolean margin;
    private BigDecimal fee;
    private Currency homeCurrency = Currency.USD;

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

    public Map<String, String> getCustom() {
        return custom;
    }

    public void setCustom(Map<String, String> custom) {
        this.custom = custom;
    }

    public Boolean getMargin() {
        return margin;
    }

    public void setMargin(Boolean margin) {
        this.margin = margin;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public Currency getHomeCurrency() {
        return homeCurrency;
    }

    public void setHomeCurrency(Currency homeCurrency) {
        this.homeCurrency = homeCurrency;
    }
}
