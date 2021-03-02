package com.r307.arbitrader.service.model;

import com.r307.arbitrader.config.FeeComputation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ExitTradeVolume extends TradeVolume {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeVolume.class);

    ExitTradeVolume(FeeComputation longFeeComputation, FeeComputation shortFeeComputation, BigDecimal entryLongOrderVolume, BigDecimal entryShortOrderVolume, BigDecimal longFee, BigDecimal shortFee, int longScale, int shortScale) {
        this.longFeeComputation=longFeeComputation;
        this.shortFeeComputation=shortFeeComputation;

        if(longFeeComputation == FeeComputation.SERVER) {
            this.longFee=longFee;
            this.longBaseFee=BigDecimal.ZERO;
        } else {
            this.longFee= getFeeAdjustedForBuy(FeeComputation.CLIENT, longFee, longScale);
            this.longBaseFee = longFee;
        }
        if(shortFeeComputation == FeeComputation.SERVER) {
            this.shortFee=shortFee;
            this.shortBaseFee=BigDecimal.ZERO;
        } else {
            this.shortFee = getFeeAdjustedForSell(FeeComputation.CLIENT, shortFee, longScale);
            this.shortBaseFee = shortFee;
        }
        this.longScale=longScale;
        this.shortScale=shortScale;

        this.longVolume = entryLongOrderVolume.subtract(getBuyBaseFees(longFeeComputation, entryLongOrderVolume, longBaseFee, true));
        this.shortVolume = entryShortOrderVolume.add(getSellBaseFees(shortFeeComputation, entryShortOrderVolume, shortBaseFee, true));
        this.longOrderVolume=longVolume;
        this.shortOrderVolume=shortVolume;
    }

    @Override
    public void adjustOrderVolume(String longExchangeName, String shortExchangeName, BigDecimal longAmountStepSize, BigDecimal shortAmountStepSize) {

        if(longFeeComputation == FeeComputation.CLIENT && longAmountStepSize != null) {
            throw new IllegalArgumentException("Long exchange FeeComputation.CLIENT and amountStepSize are not compatible.");
        }

        if(shortFeeComputation == FeeComputation.CLIENT && shortAmountStepSize != null) {
            throw new IllegalArgumentException("Short exchange FeeComputation.CLIENT and amountStepSize are not compatible.");
        }

        if(longFeeComputation == FeeComputation.SERVER) {
            BigDecimal scaledVolume = this.longVolume.setScale(longScale, RoundingMode.HALF_EVEN);
            if(longVolume.scale() > longScale) {
                LOGGER.error("{}: Ordered volume {} does not match the scale {}.",
                    longExchangeName,
                    longOrderVolume,
                    longScale);
                throw new IllegalArgumentException();
            }
            this.longOrderVolume=scaledVolume;
            if(longAmountStepSize != null) {
                BigDecimal roundedVolume = roundByStep(longOrderVolume, longAmountStepSize);
                if (roundedVolume.compareTo(longOrderVolume) != 0) {
                    LOGGER.error("{}: Ordered volume {} does not match amount step size {}.",
                        longExchangeName,
                        longOrderVolume,
                        longAmountStepSize);
                    throw new IllegalArgumentException("Long exchange ordered volume does not match the longAmountStepSize.");
                }
            }
        }

        if(shortFeeComputation == FeeComputation.SERVER) {
            BigDecimal scaledVolume = this.shortVolume.setScale(shortScale, RoundingMode.HALF_EVEN);
            if(shortVolume.scale() > shortScale) {
                LOGGER.error("{}: Ordered volume {} does not match the scale {}.",
                    shortExchangeName,
                    shortOrderVolume,
                    shortScale);
                throw new IllegalArgumentException();
            }
            this.shortOrderVolume=scaledVolume;
            if(shortAmountStepSize != null) {
                BigDecimal roundedVolume = roundByStep(shortOrderVolume,shortAmountStepSize);
                if(roundedVolume.compareTo(shortOrderVolume) != 0) {
                    LOGGER.error("{}: Ordered volume {} does not match amount step size {}.",
                        shortExchangeName,
                        shortOrderVolume,
                        shortAmountStepSize);
                    throw new IllegalArgumentException();
                }
            }
        }

        //We need to add fees for exchanges where feeComputation is set to CLIENT
        // The order volumes will be used to pass the orders after step size and rounding
        BigDecimal longBaseFees = getSellBaseFees(longFeeComputation,longVolume,longBaseFee,false);
        this.longOrderVolume = longVolume.subtract(longBaseFees);
        BigDecimal shortBaseFees = getBuyBaseFees(shortFeeComputation, shortVolume, shortBaseFee, false);
        this.shortOrderVolume = shortVolume.add(shortBaseFees);

        if(longFeeComputation == FeeComputation.CLIENT) {
            LOGGER.info("{} fees are computed in the client: {} - {} = {}",
                longExchangeName,
                longOrderVolume,
                longBaseFees,
                longVolume);
        }

        if(shortFeeComputation == FeeComputation.CLIENT) {
            LOGGER.info("{} fees are computed in the client: {} - {} = {}",
                shortExchangeName,
                shortOrderVolume,
                shortBaseFees,
                shortVolume);
        }
    }
}
