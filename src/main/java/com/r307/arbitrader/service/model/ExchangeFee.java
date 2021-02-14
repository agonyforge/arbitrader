package com.r307.arbitrader.service.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * This class is a placeholder for the different fee amounts. The {@link ExchangeFee#tradeFee} which represents the fee
 * charged by the exchange for "long" trades and the {@link ExchangeFee#marginFee} that represents the fee charged by
 * the exchange for "short"/margin trades.
 */
public class ExchangeFee {
    private final BigDecimal tradeFee;
    private final BigDecimal marginFee;

    public ExchangeFee(@NotNull BigDecimal tradeFee, @Nullable BigDecimal marginFee) {
        Objects.requireNonNull(tradeFee);

        this.tradeFee = tradeFee;
        this.marginFee = marginFee;
    }

    /**
     * The fee amount charged for a margin trade, for example when you borrow a coin to sell at a later point.
     * If the exchange is not set as a margin exchange then marginFee may be null. So here we return an Optional
     * to
     * @return an {@link Optional} of type {@link BigDecimal} with the margin fee or empty if no marginFee is set.
     */
    public Optional<BigDecimal> getMarginFee() {
        return Optional.ofNullable(marginFee);
    }

    /**
     * The fee amount charged for a long trade. For example when you buy a coin.
     * @return the trade fee
     */
    public BigDecimal getTradeFee() {
        return tradeFee;
    }

    @Override
    public String toString() {
        return "ExchangeFee{" +
            "tradeFee=" + tradeFee +
            ", marginFee=" + marginFee +
            '}';
    }
}
