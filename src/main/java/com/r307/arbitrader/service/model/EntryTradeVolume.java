package com.r307.arbitrader.service.model;

import com.r307.arbitrader.DecimalConstants;
import com.r307.arbitrader.config.FeeComputation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;

public class EntryTradeVolume extends TradeVolume{

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeVolume.class);

     EntryTradeVolume(FeeComputation longFeeComputation, FeeComputation shortFeeComputation, BigDecimal longMaxExposure, BigDecimal shortMaxExposure, BigDecimal longPrice, BigDecimal shortPrice, BigDecimal longFee, BigDecimal shortFee) {
        this.longFeeComputation=longFeeComputation;
        this.shortFeeComputation=shortFeeComputation;
        if(longFeeComputation == FeeComputation.SERVER) {
            this.longFee=longFee;
        } else {
            this.longFee= getFeeAdjustedForBuy(FeeComputation.CLIENT, longFee);
            this.longBaseFee = longFee;
        }
        if(shortFeeComputation == FeeComputation.SERVER) {
         this.shortFee=shortFee;
        } else {
            this.shortFee = getFeeAdjustedForSell(FeeComputation.CLIENT, shortFee);
            this.shortBaseFee = shortFee;
        }
        this.longVolume = getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, this.longFee, this.shortFee);
        this.shortVolume = getShortVolumeFromLong(longVolume, this.longFee, this.shortFee);
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
        BigDecimal targetRatio =getShortToLongVolumeTargetRatio(longFee, shortFee);
        return longVolume.divide(targetRatio, intermediateScale, RoundingMode.HALF_EVEN);
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
    public boolean isMarketNeutral() {
        BigDecimal threshold = BigDecimal.ONE;
        return getMarketNeutralityRating().subtract(BigDecimal.ONE).abs().compareTo(threshold)<=0;
    }

    /**
     * Rate the market neutrality in percentage:
     * 1 means perfect market neutrality
     * 0 means the fees are not compensated
     * 2 means the fees are compensated twice
     * @return the market neutrality rating
     */
    public BigDecimal getMarketNeutralityRating() {
        BigDecimal shortToLongVolumeActualRatio = longVolume.divide(shortVolume, intermediateScale, RoundingMode.HALF_EVEN);
        return (shortToLongVolumeActualRatio.subtract(BigDecimal.ONE)).divide(getShortToLongVolumeTargetRatio(longFee, shortFee).subtract(BigDecimal.ONE), intermediateScale, RoundingMode.HALF_EVEN);
    }

    @Override
    public void adjustOrderVolume(String longExchangeName, String shortExchangeName, BigDecimal longAmountStepSize, BigDecimal shortAmountStepSize, int longScale, int shortScale) {
        BigDecimal tempLongVolume = this.longVolume;
        BigDecimal tempShortVolume = this.shortVolume;

        //First adjust make sure the base volume is market neutral
        this.shortVolume = getShortVolumeFromLong(longVolume, this.longFee, this.shortFee);

        //For exchanges where feeComputation is set to CLIENT:
        //We need to increase the volume of BUY orders and decrease the volume of SELL orders
        //Because the exchange will buy slightly less volume and sell slightly more as a way to pay the fees
        this.longOrderVolume = longVolume.add(getBuyBaseFees(longFeeComputation, longVolume, longBaseFee, false));
        this.shortOrderVolume = shortVolume.subtract(getSellBaseFees(shortFeeComputation, shortVolume, shortBaseFee,false));

        if(longFeeComputation == FeeComputation.CLIENT) {
            if(longAmountStepSize != null) {
                throw new IllegalArgumentException("Long exchange FeeComputation.CLIENT and amountStepSize are not compatible.");
            }
        }
        if(shortFeeComputation == FeeComputation.CLIENT && shortAmountStepSize != null) {
            throw new IllegalArgumentException("Short exchange FeeComputation.CLIENT and amountStepSize are not compatible.");
        }

        if(longFeeComputation == FeeComputation.CLIENT) {
            LOGGER.info("{} fees are computed in the client: {} + {} = {}",
                longExchangeName,
                longVolume,
                longBaseFee,
                longOrderVolume);
        }

        if(shortFeeComputation == FeeComputation.CLIENT) {
            LOGGER.info("{} fees are computed in the client: {} + {} = {}",
                shortExchangeName,
                shortVolume,
                shortBaseFee,
                shortOrderVolume);
        }

        // Before executing the order we adjust the step size for each side of the trade (long and short).
        // When adjusting the order volumes, the underlying longVolume and shortVolume need to be adjusted as well as
        // they are used to ensure market neutrality and estimate profits.
        if(longAmountStepSize != null && shortAmountStepSize != null) {
            //Unhappy scenario
            //It will be hard to find a volume that match the step sizes on both exchanges and the market neutrality
            longOrderVolume = roundByStep(longOrderVolume, longAmountStepSize).setScale(longScale, RoundingMode.HALF_EVEN);
            shortOrderVolume = roundByStep(shortOrderVolume, shortAmountStepSize).setScale(shortScale, RoundingMode.HALF_EVEN);
            LOGGER.info("Both exchanges have an amount step size requirements. Market neutrality rating is {}.",
                getMarketNeutralityRating().setScale(3,RoundingMode.HALF_EVEN));
        } else if (longAmountStepSize != null) {
            adjustShortFromLong(longAmountStepSize, longScale, shortScale);
        } else if (shortAmountStepSize != null) {
            adjustLongFromShort(shortAmountStepSize, longScale, shortScale);
        } else if (longScale <= shortScale) {
            adjustShortFromLong(null, longScale, shortScale);
        } else {
            adjustLongFromShort(null, longScale, shortScale);
        }

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

    /**
     * Adjust the long order volume to the scale, and find the closest short order volume to market neutrality
     *  respecting the short scale
     * @param longAmountStepSize the long exchange's amount step size
     * @param longScale the scale of the volume on the long exchange
     * @param shortScale the scale of the volume on the short exchange
     */
    private void adjustShortFromLong(BigDecimal longAmountStepSize, int longScale, int shortScale) {
        if(longAmountStepSize != null) {
            BigDecimal roundedLongOrderVolume = roundByStep(longOrderVolume, longAmountStepSize);
            LOGGER.debug("Round long order volume by step {}/{} = {}",
                longOrderVolume,
                longAmountStepSize,
                roundedLongOrderVolume);
            this.longOrderVolume = roundedLongOrderVolume;
        }
        this.longOrderVolume = longOrderVolume.setScale(longScale, RoundingMode.HALF_EVEN);

        //Adjust other volumes to respect market neutrality
        BigDecimal longBaseFees = getBuyBaseFees(longFeeComputation, longOrderVolume, longBaseFee, true);
        this.longVolume = longOrderVolume.subtract(longBaseFees).setScale(longScale, RoundingMode.HALF_EVEN);
        LOGGER.debug("Calculate underlying long volume {} - {} = {}",
            longOrderVolume,
            longBaseFees,
            longVolume);

        //Recalculate short volume from long volume
        this.shortVolume = getShortVolumeFromLong(longVolume, longFee, shortFee).setScale(shortScale, RoundingMode.HALF_EVEN);

        BigDecimal shortBaseFees = getSellBaseFees(shortFeeComputation, shortVolume, shortBaseFee, false);
        this.shortOrderVolume = shortVolume.subtract(shortBaseFees);
        LOGGER.debug("Calculate short order volume {} + {} = {}",
            shortVolume,
            shortBaseFees,
            shortOrderVolume);
    }

    /**
     * Adjust the short order volume to the scale, and find the closest long order volume to market neutrality
     *  respecting the long scale
     * @param shortAmountStepSize the short exchange's amount step size
     * @param longScale the scale of the volume on the long exchange
     * @param shortScale the scale of the volume on the short exchange
     */
    private void adjustLongFromShort(BigDecimal shortAmountStepSize, int longScale, int shortScale) {
        if(shortAmountStepSize != null) {
            //Short exchange has a step size, round the short volume
            BigDecimal roundedShortOrderVolume = roundByStep(shortOrderVolume, shortAmountStepSize);
            LOGGER.debug("Round long order volume by step {}/{} = {}",
                shortOrderVolume,
                shortAmountStepSize,
                roundedShortOrderVolume);
            this.shortOrderVolume=roundedShortOrderVolume;
        }
        this.shortOrderVolume = shortOrderVolume.setScale(shortScale, RoundingMode.HALF_EVEN);
        LOGGER.debug("Scale short order volume to {}",
            shortOrderVolume);

        //Adjust other volumes to respect market neutrality
        BigDecimal shortBaseFees =getSellBaseFees(shortFeeComputation, shortOrderVolume, shortBaseFee, true);
        this.shortVolume = shortOrderVolume.subtract(shortBaseFees).setScale(shortScale, RoundingMode.HALF_EVEN);
        LOGGER.debug("Calculate underlying short volume {} - {} = {}",
            shortOrderVolume,
            shortBaseFees,
            shortVolume);

        //Recalculate long volume from short volume
        this.longVolume = getLongVolumeFromShort(shortVolume, longFee, shortFee).setScale(longScale, RoundingMode.HALF_EVEN);

        BigDecimal longBaseFees = getBuyBaseFees(longFeeComputation, longOrderVolume, longBaseFee, false);
        this.longOrderVolume = longVolume.add(longBaseFees);
        LOGGER.debug("Calculate long order volume {} + {} = {}",
            longVolume,
            longBaseFees,
            longOrderVolume);
    }

}
