package com.r307.arbitrader.service.model;

import com.r307.arbitrader.config.FeeComputation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ExitTradeVolume extends TradeVolume {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeVolume.class);

    ExitTradeVolume(FeeComputation longFeeComputation, FeeComputation shortFeeComputation, BigDecimal entryLongOrderVolume, BigDecimal entryShortOrderVolume, BigDecimal longFee, BigDecimal shortFee) {
        this.longFeeComputation=longFeeComputation;
        this.shortFeeComputation=shortFeeComputation;
        this.longFee=getFeeAdjustedForSell(longFeeComputation, longFee);
        this.shortFee=getFeeAdjustedForBuy(shortFeeComputation, shortFee);
        //To retrieve the underlying, we need to reverse the addBaseFees operation using the entry trade fees
        BigDecimal longEntryAdjustedFee= getFeeAdjustedForBuy(longFeeComputation, longFee);
        BigDecimal shortEntryAdjustedFee = getFeeAdjustedForSell(shortFeeComputation, shortFee);
        this.longVolume = inverseAddBaseFees(longFeeComputation, entryLongOrderVolume, longEntryAdjustedFee);
        this.shortVolume = inverseSubtractBaseFees(shortFeeComputation, entryShortOrderVolume, shortEntryAdjustedFee);
        this.longOrderVolume=longVolume;
        this.shortOrderVolume=shortVolume;
    }

    @Override
    public void adjustOrderVolume(String longExchangeName, String shortExchangeName, BigDecimal longAmountStepSize, BigDecimal shortAmountStepSize, int longScale, int shortScale) {
        BigDecimal tempLongVolume = this.longVolume;
        BigDecimal tempShortVolume = this.shortVolume;

        //We need to add fees for exchanges where feeComputation is set to CLIENT
        // The order volumes will be used to pass the orders after step size and rounding
        this.longOrderVolume = subtractBaseFees(longFeeComputation, longVolume, longFee);
        this.shortOrderVolume = addBaseFees(shortFeeComputation, shortVolume, shortFee);
        if(longFeeComputation == FeeComputation.CLIENT) {
            LOGGER.info("{} fees are computed in the client: {} - {} = {}",
                longExchangeName,
                longOrderVolume,
                longFee.multiply(longOrderVolume),
                longVolume);
        }

        if(shortFeeComputation == FeeComputation.CLIENT) {
            LOGGER.info("{} fees are computed in the client: {} - {} = {}",
                shortExchangeName,
                shortOrderVolume,
                shortFee.multiply(shortOrderVolume),
                shortVolume);
        }

        //Round by amount step size
        this.longOrderVolume = roundByStep (longOrderVolume, longAmountStepSize).setScale(longScale, RoundingMode.DOWN);
        this.shortOrderVolume = roundByStep (shortOrderVolume, shortAmountStepSize).setScale(shortScale,RoundingMode.UP);
        //we are trying to retrieve the volumes that will indeed be added/subtracted from our balance
        //such as longOrderVolume = subtractBaseFees(longVolume) and shortVolume such as shortOrderVolume = addBaseFees(shortVolume)
        this.longVolume = inverseSubtractBaseFees(longFeeComputation, longOrderVolume, longFee);
        this.shortVolume = inverseAddBaseFees(shortFeeComputation, shortOrderVolume, shortFee);

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
