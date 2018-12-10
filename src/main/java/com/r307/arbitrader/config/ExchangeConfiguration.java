package com.r307.arbitrader.config;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class ExchangeConfiguration {
    private String exchangeClass;
    private String apiKey;
    private String secretKey;
    private Map<String, String> custom = new HashMap<>();
    private Boolean margin;
    private BigDecimal makerFee;
    private BigDecimal takerFee;

    public String getExchangeClass() {
        return exchangeClass;
    }

    public void setExchangeClass(String exchangeClass) {
        this.exchangeClass = exchangeClass;
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

    public BigDecimal getMakerFee() {
        return makerFee;
    }

    public void setMakerFee(BigDecimal makerFee) {
        this.makerFee = makerFee;
    }

    public BigDecimal getTakerFee() {
        return takerFee;
    }

    public void setTakerFee(BigDecimal takerFee) {
        this.takerFee = takerFee;
    }
}
