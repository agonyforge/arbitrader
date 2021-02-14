package com.r307.arbitrader.service.model;

import com.r307.arbitrader.config.FeeComputation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ExitTradeVolume extends TradeVolume {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeVolume.class);

    ExitTradeVolume(BigDecimal longVolume, BigDecimal shortVolume) {
        this.longVolume=longVolume;
        this.shortVolume= shortVolume;
        this.longOrderVolume=longVolume;
        this.shortOrderVolume=shortVolume;
    }

    //TODO test FeeComputation.CLIENT flow
    @Override
    public void adjustOrderVolume(String longExchangeName, String shortExchangeName,  FeeComputation longFeeComputation, FeeComputation shortFeeComputation, BigDecimal longFee, BigDecimal shortFee, BigDecimal longAmountStepSize, BigDecimal shortAmountStepSize, int longScale, int shortScale) {
        BigDecimal tempLongVolume = this.longVolume;
        BigDecimal tempShortVolume = this.shortVolume;

        //We need to add fees for exchanges where feeComputation is set to CLIENT
        // The order volumes will be used to pass the orders after step size and rounding
        this.longOrderVolume = subtractFees(longFeeComputation, longVolume, longFee);
        this.shortOrderVolume = addFees(shortFeeComputation, shortVolume, shortFee);
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

        this.longOrderVolume = roundByStep (longOrderVolume, longAmountStepSize);
        this.shortOrderVolume = roundByStep (shortOrderVolume, shortAmountStepSize);
        //TODO we are trying to retrieve the longVolume such as longOrderVolume = subtractFees(longVolume)
        this.longVolume = addFees(longFeeComputation, longOrderVolume, longFee);
        //TODO we are trying to retrieve the shortVolume such as shortOrderVolume = addFees(shortVolume)
        this.shortVolume = subtractFees(shortFeeComputation, shortOrderVolume, shortFee);

        // Round the volumes so they are compatible with the exchanges' scales
        longOrderVolume = longOrderVolume.setScale(longScale, RoundingMode.HALF_EVEN);
        shortOrderVolume = shortOrderVolume.setScale(shortScale,RoundingMode.HALF_EVEN);
        //TODO we are trying to retrieve the longVolume such as longOrderVolume = subtractFees(longVolume)
        longVolume = addFees(longFeeComputation, longOrderVolume, longFee);
        //TODO we are trying to retrieve the shortVolume such as shortOrderVolume = addFees(shortVolume)
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
