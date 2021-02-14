package com.r307.arbitrader.service.model;

import com.r307.arbitrader.config.FeeComputation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;

public class EntryTradeVolume extends TradeVolume{

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeVolume.class);

    //An intermediate scale is necessary to limit rounding errors when queueing BigDecimal.divide calls
    private static final int intermediateScale = BTC_SCALE+4;

     EntryTradeVolume(BigDecimal longMaxExposure, BigDecimal shortMaxExposure, BigDecimal longPrice, BigDecimal shortPrice, BigDecimal longFee, BigDecimal shortFee) {
        this.longVolume = getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee);
        this.shortVolume = getShortVolumeFromLong(longVolume, shortFee, longFee);
        this.longOrderVolume=longVolume;
        this.shortOrderVolume=shortVolume;
    }

    /**
     * Calculates and assign the volume to trade on the long exchange such as:
     * - the total price does not exceed the long exchange maximum exposure
     * - the total price to trade on the short exchange does not exceed the short exchange maximum exposure
     * @see #getShortVolumeFromLong and #getFeeFactor
     * Detailed maths: https://github.com/scionaltera/arbitrader/issues/325
     */
    static BigDecimal getLongVolumeFromExposures(BigDecimal longMaxExposure, BigDecimal shortMaxExposure, BigDecimal longPrice, BigDecimal shortPrice, BigDecimal longFee, BigDecimal shortFee) {
        //volume limit induced by the maximum exposure on the long exchange
        BigDecimal longVolume1 = longMaxExposure.divide(longPrice,intermediateScale,RoundingMode.HALF_EVEN);

        //volume limit induced by the maximum exposure on the short exchange: shortVolume * shortPrice == shortMaxExposure
        //to respect market neutrality: shortVolume = longVolume2 / feeFactor
        BigDecimal longVolume2 = getShortToLongVolumeTargetRatio(longFee, shortFee).multiply(shortMaxExposure).divide(shortPrice,intermediateScale,RoundingMode.HALF_EVEN);
        return longVolume1.min(longVolume2);
    }

    /**
     * Calculates a market neutral volume to trade on the short exchange from the volume to trade on the long exchange
     * @see #getLongVolumeFromShort and #getFeeFactor
     */
    static BigDecimal getShortVolumeFromLong(BigDecimal longVolume, BigDecimal longFee, BigDecimal shortFee) {
        return longVolume.divide(getShortToLongVolumeTargetRatio(longFee, shortFee), intermediateScale, RoundingMode.HALF_EVEN);
    }

    /**
     * Calculates a market neutral volume to trade on the long exchange from the volume to trade on the short exchange
     */
    static BigDecimal getLongVolumeFromShort(BigDecimal shortVolume, BigDecimal longFee, BigDecimal shortFee) {
        return shortVolume.multiply(getShortToLongVolumeTargetRatio(longFee, shortFee));
    }

    /**
     * Set the target ratio between the short and long exchange volume to compensate for the exit fees
     * Trading the same amount on both exchanges is not market neutral. A higher volume traded on the long exchange
     * is required to compensate for the fees that could increase if the price increases.
     * Detailed maths: https://github.com/scionaltera/arbitrader/issues/325
     */
    static BigDecimal getShortToLongVolumeTargetRatio(BigDecimal longFee, BigDecimal shortFee) {
        return (BigDecimal.ONE.add(shortFee)).divide(BigDecimal.ONE.subtract(longFee),intermediateScale, RoundingMode.HALF_EVEN);
    }

    /**
     * Check if the trade is still market neutral enough
     * @return true if the market neutrality rating is between 0 and 2.
     */
    public boolean isMarketNeutral(BigDecimal longFee, BigDecimal shortFee) {
        BigDecimal threshold = BigDecimal.ONE;
        return getMarketNeutralityRating(longFee, shortFee).subtract(BigDecimal.ONE).abs().compareTo(threshold)<=0;
    }

    /**
     * Rate the market neutrality in percentage:
     * 1 means perfect market neutrality
     * 0 means the fees are not compensated
     * 2 means the fees are compensated twice
     * @return the market neutrality rating
     */
    public BigDecimal getMarketNeutralityRating(BigDecimal longFee, BigDecimal shortFee) {
        BigDecimal shortToLongVolumeActualRatio = longVolume.divide(shortVolume, intermediateScale, RoundingMode.HALF_EVEN);
        return (shortToLongVolumeActualRatio.subtract(BigDecimal.ONE)).divide(getShortToLongVolumeTargetRatio(longFee, shortFee).subtract(BigDecimal.ONE), intermediateScale, RoundingMode.HALF_EVEN);
    }

    //TODO test FeeComputation.CLIENT flow
    @Override
    public void adjustOrderVolume(String longExchangeName, String shortExchangeName, FeeComputation longFeeComputation, FeeComputation shortFeeComputation, BigDecimal longFee, BigDecimal shortFee, BigDecimal longAmountStepSize, BigDecimal shortAmountStepSize, int longScale, int shortScale) {
        BigDecimal tempLongVolume = this.longVolume;
        BigDecimal tempShortVolume = this.shortVolume;
        //We need to add fees for exchanges where feeComputation is set to CLIENT
        // The order volumes will be used to pass the orders after step size and rounding
        this.longOrderVolume = addFees(longFeeComputation, longVolume, longFee);
        this.shortOrderVolume = addFees(shortFeeComputation, shortVolume, shortFee);

        if(longFeeComputation == FeeComputation.CLIENT) {
            LOGGER.info("{} fees are computed in the client: {} + {} = {}",
                longExchangeName,
                longVolume,
                longFee.multiply(longVolume),
                longOrderVolume);
        }

        if(shortFeeComputation == FeeComputation.CLIENT) {
            LOGGER.info("{} fees are computed in the client: {} + {} = {}",
                shortExchangeName,
                shortVolume,
                shortFee.multiply(shortVolume),
                shortOrderVolume);
        }

        // Before executing the order we adjust the step size for each side of the trade (long and short).
        // When adjusting the order volumes, the underlying longVolume and shortVolume need to be adjusted as well as
        // they are used to ensure market neutrality and estimate profits.
        if(longAmountStepSize != null && shortAmountStepSize != null) {
            //Unhappy scenario
            //It will be hard to find a volume that match the step sizes on both exchanges and the market neutrality
            longOrderVolume = roundByStep(longOrderVolume, longAmountStepSize);
            shortOrderVolume = roundByStep(shortOrderVolume, shortAmountStepSize);
            LOGGER.info("Both exchanges have a step size requirements. Market neutrality rating is {}.",
                getMarketNeutralityRating(longFee, shortFee).setScale(3,RoundingMode.HALF_EVEN));
        } else if (longAmountStepSize != null) {
            //Long exchange has a step size, round the long volume
            BigDecimal roundedLongOrderVolume = roundByStep (longOrderVolume, longAmountStepSize);
            LOGGER.debug("Round long order volume by step {}/{} = {}",
                longOrderVolume,
                longAmountStepSize,
                roundedLongOrderVolume);
            longOrderVolume = roundedLongOrderVolume;
            //Adjust other volumes to respect market neutrality
            longVolume = subtractFees(longFeeComputation, longOrderVolume, longFee);
            shortVolume = getShortVolumeFromLong(longVolume, longFee, shortFee);
            shortOrderVolume = addFees(shortFeeComputation, shortVolume, shortFee);
        } else if (shortAmountStepSize != null) {
            //Short exchange has a step size, round the short volume
            BigDecimal roundedShortOrderVolume = roundByStep (shortOrderVolume, shortAmountStepSize);
            LOGGER.debug("Round long order volume by step {}/{} = {}",
                shortOrderVolume,
                shortAmountStepSize,
                roundedShortOrderVolume);
            shortOrderVolume = roundedShortOrderVolume;
            //Adjust other volumes to respect market neutrality
            shortVolume = subtractFees(shortFeeComputation, shortOrderVolume, shortFee);
            longVolume = getLongVolumeFromShort(shortVolume, longFee, shortFee);
            longOrderVolume = addFees(longFeeComputation, longVolume, longFee);
        }

        // Round the volumes so they are compatible with the exchanges' scales
        longOrderVolume = longOrderVolume.setScale(longScale, RoundingMode.HALF_EVEN);
        shortOrderVolume = shortOrderVolume.setScale(shortScale,RoundingMode.HALF_EVEN);
        longVolume = subtractFees(longFeeComputation, longOrderVolume, longFee);
        shortVolume = subtractFees(shortFeeComputation, shortOrderVolume, shortFee);

        if(!tempLongVolume.equals(longOrderVolume)) {
            LOGGER.info("{} entry trade volumes adjusted: {} -> {} (order volume: {}) ",
                longExchangeName,
                tempLongVolume,
                longVolume,
                longOrderVolume
            );
        }
        if(!tempLongVolume.equals(longOrderVolume)) {
            LOGGER.info("{} entry trade volumes adjusted: {} -> {} (order volume: {}) ",
                shortExchangeName,
                tempShortVolume,
                shortVolume,
                shortOrderVolume
            );
        }
    }
}
