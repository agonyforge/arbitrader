package com.r307.arbitrader.service.model;

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

    BigDecimal longVolume;

    BigDecimal shortVolume;

    BigDecimal longOrderVolume;

    BigDecimal shortOrderVolume;

    /**
     * Instantiate a new EntryTradeVolume
     * @param longMaxExposure the maximum $ to trade on the long exchange
     * @param shortMaxExposure the maxmimum $ to trade on the short exchange
     * @param longPrice the price on the long exchange
     * @param shortPrice the price on the short exchange
     * @param longFee long exchange fee percentage
     * @param shortFee short exchange fee percentage
     * @return a new EntryTradeVolume
     */
    public static EntryTradeVolume getEntryTradeVolume(BigDecimal longMaxExposure, BigDecimal shortMaxExposure, BigDecimal longPrice, BigDecimal shortPrice, BigDecimal longFee, BigDecimal shortFee) {
        return new EntryTradeVolume(longMaxExposure, shortMaxExposure, longPrice, shortPrice,longFee, shortFee);
    }

    /**
     * Instantiate a new ExitTradeVolume
     * @param longVolume the volume to trade on the long exchange
     * @param shortVolume the volumeto trade on the short exchange
     * @return a new ExitTradeVolume
     */
    public static ExitTradeVolume getExitTradeVolume(BigDecimal longVolume, BigDecimal shortVolume) {
        return new ExitTradeVolume(longVolume, shortVolume);
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
    public abstract void adjustOrderVolume(String longExchangeName, String shortExchangeName, FeeComputation longFeeComputation, FeeComputation shortFeeComputation, BigDecimal longFee, BigDecimal shortFee, BigDecimal longAmountStepSize, BigDecimal shortAmountStepSize, int longScale, int shortScale);

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
     * Increase the volume for FeeComputation.CLIENT exchanges so it compensates for the exchange fees
     * @return the increased volume
     */
    static BigDecimal addFees(FeeComputation feeComputation, BigDecimal volume, BigDecimal fee) {
        if (feeComputation.equals(FeeComputation.CLIENT)) {
            BigDecimal totalFee = volume
                .multiply(fee)
                .setScale(BTC_SCALE, RoundingMode.HALF_EVEN);

            return volume.add(totalFee);
        }

        return volume;
    }


    /**
     * Decrease the volume for FeeComputation.CLIENT exchanges so it compensates for the exchange fees
     * @return the decreased volume
     */
    static BigDecimal subtractFees(FeeComputation feeComputation, BigDecimal volume, BigDecimal fee) {
        if (feeComputation.equals(FeeComputation.CLIENT)) {
            BigDecimal totalFee = volume
                .multiply(fee)
                .setScale(BTC_SCALE, RoundingMode.HALF_EVEN);

            return volume.subtract(totalFee);
        }

        return volume;
    }

}
