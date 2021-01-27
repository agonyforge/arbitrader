package com.r307.arbitrader.service.model;

import java.math.BigDecimal;
import java.util.Optional;

public class ExchangeFee {
    private BigDecimal shortFee;
    private BigDecimal longFee;

    public ExchangeFee(BigDecimal shortFee, BigDecimal longFee) {
        this.shortFee = shortFee;
        this.longFee = longFee;
    }

    public Optional<BigDecimal> getShortFee() {
        return Optional.ofNullable(shortFee);
    }

    public void setShortFee(BigDecimal shortFee) {
        this.shortFee = shortFee;
    }

    public BigDecimal getLongFee() {
        return longFee;
    }

    public void setLongFee(BigDecimal longFee) {
        this.longFee = longFee;
    }

    @Override
    public String toString() {
        return "ExchangeFee{" +
            "shortFee=" + shortFee +
            ", longFee=" + longFee +
            '}';
    }
}
