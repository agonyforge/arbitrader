package com.r307.arbitrader.service.model;

import com.r307.arbitrader.DecimalConstants;
import com.r307.arbitrader.config.FeeComputation;
import com.r307.arbitrader.service.TradingService;
import org.jetbrains.annotations.NotNull;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;

/**
 * Wrapper around a trade volume to facilitate volume computations
 */
public abstract class TradeVolume {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeVolume.class);

    static final int intermediateScale = DecimalConstants.getIntermediateScale(BTC_SCALE);

    BigDecimal longVolume;

    BigDecimal shortVolume;

    // The order volumes will be used to pass the orders after step size and rounding
    BigDecimal longOrderVolume;

    // The order volumes will be used to pass the orders after step size and rounding
    BigDecimal shortOrderVolume;

    BigDecimal longFee;

    BigDecimal shortFee;

    FeeComputation longFeeComputation;

    FeeComputation shortFeeComputation;

    /**
     * Instantiate a new EntryTradeVolume
     * @param longMaxExposure the maximum $ to trade on the long exchange
     * @param shortMaxExposure the maxmimum $ to trade on the short exchange
     * @param longPrice the price on the long exchange
     * @param shortPrice the price on the short exchange
     * @param longFee the adjusted for FeeComputation long exchange fee percentage
     * @param shortFee the adjusted for FeeComputation short exchange fee percentage
     * @return a new EntryTradeVolume
     */
    public static EntryTradeVolume getEntryTradeVolume(FeeComputation longFeeComputation, FeeComputation shortFeeComputation, BigDecimal longMaxExposure, BigDecimal shortMaxExposure, BigDecimal longPrice, BigDecimal shortPrice, BigDecimal longFee, BigDecimal shortFee) {
        return new EntryTradeVolume(longFeeComputation, shortFeeComputation, longMaxExposure, shortMaxExposure, longPrice, shortPrice,longFee, shortFee);
    }

    /**
     * Instantiate a new ExitTradeVolume
     * @param entryLongOrderVolume the volume to trade on the long exchange
     * @param entryShortOrderVolume the volume to trade on the short exchange
     * @return a new ExitTradeVolume
     */
    public static ExitTradeVolume getExitTradeVolume(FeeComputation longFeeComputation, FeeComputation shortFeeComputation, BigDecimal entryLongOrderVolume, BigDecimal entryShortOrderVolume, BigDecimal longFee, BigDecimal shortFee) {
        return new ExitTradeVolume(longFeeComputation, shortFeeComputation, entryLongOrderVolume, entryShortOrderVolume, longFee, shortFee);
    }

    /**
     * @return the underlying volume before FeeComputation adjustments to order from the long exchange
     */
    public BigDecimal getShortVolume() {
        return shortVolume;
    }

    /**
     * @return the underlying volume before FeeComputation adjustments to order from the short exchange
     */
    public BigDecimal getLongVolume() {
        return longVolume;
    }

    /**
     * @return the volume to order from the long exchange
     */
    public BigDecimal getLongOrderVolume() {
        return longOrderVolume;
    }

    /**
     * @return the volume to order from the short exchange
     */
    public BigDecimal getShortOrderVolume() {
        return shortOrderVolume;
    }

    /**
     * Adjust the trade order volumes so they are inflated/deflated according to the exchanges FeeComputation mode, rounded up by step size and scales
     */
    public abstract void adjustOrderVolume(String longExchangeName, String shortExchangeName, BigDecimal longAmountStepSize, BigDecimal shortAmountStepSize, int longScale, int shortScale);

    /**
     * Get the multiple of "step" that is nearest to the original number.
     *
     * The formula is: step * round(input / step)
     * All the BigDecimals make it really hard to read. We're using setScale() instead of round() because you can't
     * set the precision on round() to zero. You can do it with setScale() and it will implicitly do the rounding.
     *
     * @param input The original number.
     * @param step The step to round by.
     * @return A multiple of step that is the nearest to the original number.
     */
    static BigDecimal roundByStep(BigDecimal input, BigDecimal step) {
        if (step == null)
            return input;
        LOGGER.info("input = {} step = {}", input, step);

        BigDecimal result = input
            .divide(step, RoundingMode.HALF_EVEN)
            .setScale(0, RoundingMode.HALF_EVEN)
            .multiply(step)
            .setScale(step.scale(), RoundingMode.HALF_EVEN);

        LOGGER.info("result = {}", result);

        return result;
    }

    /**
     * Reverse {@link #addBaseFees(FeeComputation, BigDecimal, BigDecimal)} to find retrieve the initial volume
     * @return the initial volume
     */
    static BigDecimal inverseAddBaseFees(FeeComputation feeComputation, BigDecimal volume, BigDecimal fee) {
        if(feeComputation == FeeComputation.SERVER)
            return volume;
        return volume.divide (BigDecimal.ONE.add(fee), BTC_SCALE, RoundingMode.HALF_EVEN);
    }

    /**
     * Return an increased volume such as, after deducting the fee, the result is the initial volume
     * @param feeComputation the fee computation mode
     * @param volume the initial volume
     * @param fee the base fee in percentage
     * @return the increased volume such as increasedVolume = volume * (1 + fee)
     */
    static BigDecimal addBaseFees(FeeComputation feeComputation, BigDecimal volume, BigDecimal fee) {
        if(feeComputation == FeeComputation.SERVER)
            return volume;
        return volume.multiply(BigDecimal.ONE.add(fee));
    }

    /**
     * Reverse {@link #subtractBaseFees(FeeComputation, BigDecimal, BigDecimal)} to find retrieve the initial volume
     * @return the initial volume
     */
    static BigDecimal inverseSubtractBaseFees(FeeComputation feeComputation, BigDecimal volume, BigDecimal fee) {
        if(feeComputation == FeeComputation.SERVER)
            return volume;
        return volume.divide (BigDecimal.ONE.subtract(fee), BTC_SCALE, RoundingMode.HALF_EVEN);
    }

    /**
     * Return an decreased volume such as, after deducting the fee, the result is the initial volume
     * @param feeComputation the fee computation mode
     * @param volume the initial volume
     * @param fee the base fee in percentage
     * @return the decreased volume such as decreasedVolume = volume * (1 - fee)
     */
    static BigDecimal subtractBaseFees(FeeComputation feeComputation, BigDecimal volume, BigDecimal fee) {
        if(feeComputation == FeeComputation.SERVER)
            return volume;
        return volume.multiply(BigDecimal.ONE.subtract(fee));
    }

    static BigDecimal getFeeAdjustedForSell(FeeComputation feeComputation, BigDecimal fee) {
        if(feeComputation == FeeComputation.CLIENT)
            fee = fee.divide(BigDecimal.ONE.add(fee), intermediateScale, RoundingMode.HALF_EVEN);
        return fee;
    }


    static BigDecimal getFeeAdjustedForBuy(FeeComputation feeComputation, BigDecimal fee) {
        if(feeComputation == FeeComputation.CLIENT)
            fee = fee.divide(BigDecimal.ONE.subtract(fee), intermediateScale, RoundingMode.HALF_EVEN);
        return fee;
    }
}
