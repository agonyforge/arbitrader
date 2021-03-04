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

    //The underlying volume to trade on the long exchange
    BigDecimal longVolume;

    //The underlying volume to trade on the short exchange
    BigDecimal shortVolume;

    // The order volumes will be used to pass the orders after step size and rounding
    // This is different from longVolume only for feeComputation.CLIENT exchanges
    BigDecimal longOrderVolume;

    // The order volumes will be used to pass the orders after step size and rounding
    // This is different from shortVolume only for feeComputation.CLIENT exchanges
    BigDecimal shortOrderVolume;

    //The long exchange fees in percentage
    BigDecimal longFee;

    //The short exchange fees in percentage
    BigDecimal shortFee;

    //The long exchange crypto fees (for FeeComputation.CLIENT exchanges) in percentage
    BigDecimal longBaseFee;

    //The short exchange crypto fees (for FeeComputation.CLIENT exchanges) in percentage
    BigDecimal shortBaseFee;

    //The long exchange FeeComputation
    FeeComputation longFeeComputation;

    //The long exchange FeeComputation
    FeeComputation shortFeeComputation;

    //The long exchange volume scale
    int longScale;

    //The short exchange volume scale
    int shortScale;

    /**
     * Instantiate a new EntryTradeVolume
     * @param longFeeComputation the long exchange FeeComputation
     * @param shortFeeComputation the short exchange FeeComputation
     * @param longMaxExposure the maximum $ to trade on the long exchange
     * @param shortMaxExposure the maxmimum $ to trade on the short exchange
     * @param longPrice the price on the long exchange
     * @param shortPrice the price on the short exchange
     * @param longFee the long exchange fee percentage
     * @param shortFee the short exchange fee percentage
     * @param exitSpread the exit target spread
     * @param longScale the long exchange volume scale
     * @param shortScale the short exchange volume scale
     * @return a new EntryTradeVolume
     */
    public static EntryTradeVolume getEntryTradeVolume(FeeComputation longFeeComputation, FeeComputation shortFeeComputation, BigDecimal longMaxExposure, BigDecimal shortMaxExposure, BigDecimal longPrice, BigDecimal shortPrice, BigDecimal longFee, BigDecimal shortFee, BigDecimal exitSpread, int longScale, int shortScale) {
        return new EntryTradeVolume(longFeeComputation, shortFeeComputation, longMaxExposure, shortMaxExposure, longPrice, shortPrice,longFee, shortFee, exitSpread, longScale, shortScale);
    }

    /**
     * Instantiate a new ExitTradeVolume
     * @param longFeeComputation the long exchange FeeComputation
     * @param shortFeeComputation the short exchange FeeComputation
     * @param entryLongOrderVolume the volume to trade on the long exchange
     * @param entryShortOrderVolume the volume to trade on the short exchange
     * @param longFee the long exchange fee percentage
     * @param shortFee the short exchange fee percentage
     * @param longScale the long exchange volume scale
     * @param shortScale the short exchange volume scale
     * @return a new ExitTradeVolume
     */
    public static ExitTradeVolume getExitTradeVolume(FeeComputation longFeeComputation, FeeComputation shortFeeComputation, BigDecimal entryLongOrderVolume, BigDecimal entryShortOrderVolume, BigDecimal longFee, BigDecimal shortFee, int longScale, int shortScale) {
        return new ExitTradeVolume(longFeeComputation, shortFeeComputation, entryLongOrderVolume, entryShortOrderVolume, longFee, shortFee, longScale, shortScale);
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

    public BigDecimal getLongFee() {return longFee;}

    public BigDecimal getLongBaseFee() {return longBaseFee;}

    public BigDecimal getShortFee() {return shortFee;}

    public BigDecimal getShortBaseFee() {return shortBaseFee;}
    /**
     * Adjust the trade order volumes so they are inflated/deflated according to the exchanges FeeComputation mode, rounded up by step size and scales
     */
    public abstract void adjustOrderVolume(String longExchangeName, String shortExchangeName, BigDecimal longAmountStepSize, BigDecimal shortAmountStepSize);

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
     * Get a buy order base fees for a volume and a FeeComputation
     * @param feeComputation the FeeComputation
     * @param volume the volume
     * @param baseFee the fee percentage to apply on an order volume
     * @param orderVolume true if the volume is an order volume, false if it is an underlying volume
     * @return the fees in crypto
     */
    static BigDecimal getBuyBaseFees(FeeComputation feeComputation, BigDecimal volume, BigDecimal baseFee, boolean orderVolume) {
        BigDecimal result = BigDecimal.ZERO.setScale(volume.scale(), RoundingMode.HALF_EVEN);
        if(feeComputation == FeeComputation.CLIENT) {
            verifyFeeComputationClientFees(baseFee);
            if (orderVolume) {
                result =  volume.multiply(baseFee).setScale(volume.scale(), RoundingMode.HALF_EVEN);
                LOGGER.debug("Calculate buy order base fees from underlying volume {}, with fee percentage {}: {}",
                    volume,
                    baseFee,
                    result);
            } else {
                result = volume.multiply(baseFee).divide(BigDecimal.ONE.subtract(baseFee), volume.scale(), RoundingMode.HALF_EVEN);
                LOGGER.debug("Calculate buy order base fees from order volume {}, with fee percentage {}: {}",
                    volume,
                    baseFee,
                    result);
            }
        }
        return result;
    }

    /**
     * Get a sell order base fees for a volume and a FeeComputation
     * @param feeComputation the FeeComputation
     * @param volume the volume
     * @param baseFee the fee percentage to apply on an order volume
     * @param orderVolume true if the volume is an order volume, false if it is an underlying volume
     * @return the fees in crypto
     */
    static BigDecimal getSellBaseFees(FeeComputation feeComputation, BigDecimal volume, BigDecimal baseFee, boolean orderVolume) {
        BigDecimal result = BigDecimal.ZERO.setScale(volume.scale(), RoundingMode.HALF_EVEN);
        if(feeComputation == FeeComputation.CLIENT) {
            verifyFeeComputationClientFees(baseFee);
            if (orderVolume) {
                result =  volume.multiply(baseFee).setScale(volume.scale(), RoundingMode.HALF_EVEN);
                LOGGER.debug("Calculate sell order base fees from underlying volume {}, with fee percentage {}: {}",
                    volume,
                    baseFee,
                    result);
            } else {
                result = volume.multiply(baseFee).divide(BigDecimal.ONE.add(baseFee), volume.scale(), RoundingMode.HALF_EVEN);
                LOGGER.debug("Calculate sell order base fees from order volume {}, with fee percentage {}: {}",
                    volume,
                    baseFee,
                    result);
            }
        }
        return result;
    }

    /**
     * Throws an IllegalArgumentException if the feePercentage could trigger rounding issues when calculating the base fees
     * The error margin when calculating a volume order from an underlying volume is maximum scale/2
     * It means an error margin on the base fees of `scale/2 * fee`
     * We want to make sure that this error cannot cause rounding issues: error margin <scale/2 *0.01
     * ie fee < 0.01
     * @param feePercentage the crypto fee percentage
     */
    static private void verifyFeeComputationClientFees(BigDecimal feePercentage) {
        //If a FeeComputation.CLIENT has fees higher than 1%, the base fee scaling could cause issues
        //The error margin when calculating a volume order from an underlying volume is maximum scale/2
        //It means an error margin on the base fees of `scale/2 * fee`
        //We want to make sure that this error cannot cause rounding issues: error margin <scale/2 *0.01
        //ie fee < 0.01
        if(feePercentage.compareTo(new BigDecimal("0.01"))>=0) {
            LOGGER.error("FeeComputation.CLIENT fee percentage too high: {}",
                feePercentage);
            throw new IllegalArgumentException();
        }
    }

    /**
     * Retrieve a feePercentage equivalent to a FeeComputation.SERVER fee percentage for a Sell order.
     * This is only used to facilitate the TradeVolume market neutrality rating calculation.
     *
     * @param feeComputation the FeeComputation type
     * @param baseFee the crypto fee percentage
     * @return the FeeComputaiton.SERVER equivalent fee percentage
     */
    static BigDecimal getFeeAdjustedForSell(FeeComputation feeComputation, BigDecimal baseFee, int scale) {
        BigDecimal result = baseFee;
        if(feeComputation == FeeComputation.CLIENT)
            result = baseFee.divide(BigDecimal.ONE.add(baseFee), getIntermediateScale(scale), RoundingMode.HALF_EVEN);
        return result;
    }

    /**
     * Retrieve a feePercentage equivalent to a FeeComputation.SERVER fee percentage for a buy order.
     * This is only used to facilitate the TradeVolume market neutrality rating calculation.
     *
     * @param feeComputation the FeeComputation type
     * @param baseFee the crypto fee percentage
     * @return he FeeComputaiton.SERVER equivalent fee percentage
     */
    static BigDecimal getFeeAdjustedForBuy(FeeComputation feeComputation, BigDecimal baseFee, int scale) {
        BigDecimal result = baseFee;
        if(feeComputation == FeeComputation.CLIENT)
            result = baseFee.divide(BigDecimal.ONE.subtract(baseFee), getIntermediateScale(scale), RoundingMode.HALF_EVEN);
        return result;
    }

    //An intermediate scale is necessary to limit rounding errors when queueing BigDecimal.divide calls
    public static int getIntermediateScale(int scale) {
        return scale+4;
    }
}
