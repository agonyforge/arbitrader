package com.r307.arbitrader.service.model;

import com.r307.arbitrader.config.FeeComputation;
import com.r307.arbitrader.service.TradingService;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;
import static org.junit.Assert.*;

public class TradeVolumeTest {

    @Test
    public void isMarketNeutralAfterConstructor() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.001");

        boolean result = EntryTradeVolume.getEntryTradeVolume(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee).isMarketNeutral(longFee, shortFee);

        assertTrue("After construction a trade volume is expected to be perfectly market neutral",  result);
    }

    @Test
    public void isMarketNeutralZERO() {
        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longVolume=new BigDecimal("100");
        BigDecimal longFee=new BigDecimal("0.05");
        BigDecimal shortFee=new BigDecimal("0.01");

        boolean result = tradeVolume.isMarketNeutral(longFee, shortFee);

        assertTrue(result);
    }

    @Test
    public void isMarketNeutralBelow() {
        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longVolume=new BigDecimal("0.99");
        BigDecimal longFee=new BigDecimal("0.05");
        BigDecimal shortFee=new BigDecimal("0.00");

        boolean result = tradeVolume.isMarketNeutral(longFee, shortFee);

        assertFalse(result);
    }

    @Test
    public void isMarketNeutralTWO() {
        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
        tradeVolume.longVolume=new BigDecimal("110");
        tradeVolume.shortVolume=new BigDecimal("100");
        BigDecimal longFee=new BigDecimal("0.05");
        BigDecimal shortFee=new BigDecimal("0.0");

        boolean result = tradeVolume.isMarketNeutral(longFee, shortFee);

        assertTrue(result);
    }

    @Test
    public void isMarketNeutralAbove() {
        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
        tradeVolume.longVolume=new BigDecimal("111");
        tradeVolume.shortVolume=new BigDecimal("100");
        BigDecimal longFee=new BigDecimal("0.05");
        BigDecimal shortFee=new BigDecimal("0.0");

        boolean result = tradeVolume.isMarketNeutral(longFee, shortFee);

        assertFalse(result);
    }

    @Test
    public void getMarketNeutralityRatingAfterConstructor() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.001");

        BigDecimal result = EntryTradeVolume.getEntryTradeVolume(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee).getMarketNeutralityRating(longFee, shortFee);

        assertEquals("After construction a trade volume is expected to be perfectly market neutral", new BigDecimal("1").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getMarketNeutralityRatingZERO() {
        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
        tradeVolume.longVolume=new BigDecimal("100");
        tradeVolume.shortVolume=new BigDecimal("100");
        BigDecimal longFee=new BigDecimal("0.05");
        BigDecimal shortFee=new BigDecimal("0.01");

        BigDecimal result = tradeVolume.getMarketNeutralityRating(longFee, shortFee);

        assertEquals(new BigDecimal("0").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getMarketNeutralityRatingONE() {
        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
        tradeVolume.longVolume=new BigDecimal("105");
        tradeVolume.shortVolume=new BigDecimal("100");
        BigDecimal longFee=new BigDecimal("0.0");
        BigDecimal shortFee=new BigDecimal("0.05");

        BigDecimal result = tradeVolume.getMarketNeutralityRating(longFee, shortFee);

        assertEquals(new BigDecimal("1").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getMarketNeutralityRatingTWO() {
        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
        tradeVolume.longVolume=new BigDecimal("110");
        tradeVolume.shortVolume=new BigDecimal("100");
        BigDecimal longFee=new BigDecimal("0.0");
        BigDecimal shortFee=new BigDecimal("0.05");

        BigDecimal result = tradeVolume.getMarketNeutralityRating(longFee, shortFee);

        assertEquals(new BigDecimal("2").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getMarketNeutralityRatingTHREE() {
        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
        tradeVolume.longVolume=new BigDecimal("115");
        tradeVolume.shortVolume=new BigDecimal("100");
        BigDecimal longFee=new BigDecimal("0.0");
        BigDecimal shortFee=new BigDecimal("0.05");

        BigDecimal result = tradeVolume.getMarketNeutralityRating(longFee, shortFee);

        assertEquals(new BigDecimal("3").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getMarketNeutralityRating() {
        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
        tradeVolume.longVolume=new BigDecimal("103");
        tradeVolume.shortVolume=new BigDecimal("100");
        BigDecimal longFee=new BigDecimal("0.05");
        BigDecimal shortFee=new BigDecimal("0.01");

        BigDecimal result = tradeVolume.getMarketNeutralityRating(longFee, shortFee);

        assertEquals(new BigDecimal("0.475000000000001").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }


    @Test
    public void getShortToLongVolumeTargetRatioNoFee() {
        BigDecimal longFee = new BigDecimal("0");
        BigDecimal shortFee = new BigDecimal("0");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee);

        assertEquals(new BigDecimal("1").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortToLongVolumeTargetRatio005and001() {
        BigDecimal longFee = new BigDecimal("0.05");
        BigDecimal shortFee = new BigDecimal("0.01");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee);

        assertEquals(new BigDecimal("1.06315789473684").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortToLongVolumeTargetRatio01and001() {
        BigDecimal longFee = new BigDecimal("0.1");
        BigDecimal shortFee = new BigDecimal("0.01");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee);

        assertEquals(new BigDecimal("1.12222222222222").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortToLongVolumeTargetRatio001and005() {
        BigDecimal longFee = new BigDecimal("0.01");
        BigDecimal shortFee = new BigDecimal("0.05");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee);

        assertEquals(new BigDecimal("1.06060606060606").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortToLongVolumeTargetRatio0001and00026() {
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.0026");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee);

        assertEquals(new BigDecimal("1.0036036036036").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortToLongVolumeTargetRatio0005and00026() {
        BigDecimal longFee = new BigDecimal("0.005");
        BigDecimal shortFee = new BigDecimal("0.0026");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee);

        assertEquals(new BigDecimal("1.00763819095477").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortToLongVolumeTargetRatio001and002() {
        BigDecimal longFee = new BigDecimal("0.01");
        BigDecimal shortFee = new BigDecimal("0.002");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee);

        assertEquals(new BigDecimal("1.01212121212121").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortToLongVolumeTargetRatio0001and0003() {
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.003");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee);

        assertEquals(new BigDecimal("1.004004004004").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }



    @Test
    public void getShortVolumeFromLongNoFee() {
        BigDecimal longVolume = new BigDecimal ("100");
        BigDecimal longFee = new BigDecimal("0");
        BigDecimal shortFee = new BigDecimal("0");

        BigDecimal result = EntryTradeVolume.getShortVolumeFromLong(longVolume, longFee, shortFee);

        assertEquals(new BigDecimal("100").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortVolumeFromLongHighVolume() {
        BigDecimal longVolume = new BigDecimal ("200");
        BigDecimal longFee = new BigDecimal("0.01");
        BigDecimal shortFee = new BigDecimal("0.01");

        BigDecimal result = EntryTradeVolume.getShortVolumeFromLong(longVolume, longFee, shortFee);

        assertEquals(new BigDecimal("196.039603960396").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortVolumeFromLongHighLongFee() {
        BigDecimal longVolume = new BigDecimal ("100");
        BigDecimal longFee = new BigDecimal("0.05");
        BigDecimal shortFee = new BigDecimal("0.001");

        BigDecimal result = EntryTradeVolume.getShortVolumeFromLong(longVolume, longFee, shortFee);

        assertEquals(new BigDecimal("94.9050949050949").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortVolumeFromLongHighShortFee() {
        BigDecimal longVolume = new BigDecimal ("100");
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.05");

        BigDecimal result = EntryTradeVolume.getShortVolumeFromLong(longVolume, longFee, shortFee);

        assertEquals(new BigDecimal("95.1428571428571").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getLongVolumeFromShortNoFee() {
        BigDecimal shortVolume = new BigDecimal ("100");
        BigDecimal longFee = new BigDecimal("0");
        BigDecimal shortFee = new BigDecimal("0");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromShort(shortVolume, longFee, shortFee);

        assertEquals(new BigDecimal("100").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getLongVolumeFromShortHighVolume() {
        BigDecimal shortVolume = new BigDecimal ("200");
        BigDecimal longFee = new BigDecimal("0.01");
        BigDecimal shortFee = new BigDecimal("0.01");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromShort(shortVolume, longFee, shortFee);

        assertEquals(new BigDecimal("204.040404040404").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getLongVolumeFromShortHighLongFee() {
        BigDecimal shortVolume = new BigDecimal ("100");
        BigDecimal longFee = new BigDecimal("0.05");
        BigDecimal shortFee = new BigDecimal("0.001");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromShort(shortVolume, longFee, shortFee);

        assertEquals(new BigDecimal("105.368421052632").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getLongVolumeFromShortHighShortFee() {
        BigDecimal shortVolume = new BigDecimal ("100");
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.05");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromShort(shortVolume, longFee, shortFee);

        assertEquals(new BigDecimal("105.105105105105").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getLongVolumeFromExposures() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.001");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee);

        assertEquals(new BigDecimal("0.095428762095429").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getLongVolumeFromExposuresHighLongFee() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        BigDecimal longFee = new BigDecimal("0.005");
        BigDecimal shortFee = new BigDecimal("0.001");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee);

        assertEquals(new BigDecimal("0.095812395309883").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getLongVolumeFromExposuresHighShortFee() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.005");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee);

        assertEquals(new BigDecimal("0.095810095810096").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getLongVolumeFromExposuresHighShortExposure() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("150");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.001");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee);

        assertEquals(new BigDecimal("0.105263157894737").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getLongVolumeFromExposuresHighLongExposure() {
        BigDecimal longMaxExposure = new BigDecimal("150");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.001");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee);

        assertEquals(new BigDecimal("0.095428762095429").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }


    @Test
    public void getLongVolumeFromExposuresLowLongPrice() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("500");
        BigDecimal shortPrice = new BigDecimal("1050");
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.001");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee);

        assertEquals(new BigDecimal("0.095428762095429").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getLongVolumeFromExposuresHighShortPrice() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1500");
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.001");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee);

        assertEquals(new BigDecimal("0.0668001334668").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void addFeesFeeComputationServer() {
        FeeComputation feeComputation = FeeComputation.SERVER;
        BigDecimal volume = new BigDecimal("100");
        BigDecimal fee = new BigDecimal("0.001");

        BigDecimal result = TradeVolume.addFees(feeComputation, volume, fee);

        assertEquals(new BigDecimal("100"), result);
    }

    @Test
    public void addFeesFeeComputationClient() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("100");
        BigDecimal fee = new BigDecimal("0.001");

        BigDecimal result = TradeVolume.addFees(feeComputation, volume, fee);

        assertEquals(new BigDecimal("100.1").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void subtractFeesFeeComputationServer() {
        FeeComputation feeComputation = FeeComputation.SERVER;
        BigDecimal volume = new BigDecimal("100");
        BigDecimal fee = new BigDecimal("0.001");

        BigDecimal result = TradeVolume.subtractFees(feeComputation, volume, fee);

        assertEquals(new BigDecimal("100").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void subtractFeesFeeComputationClient() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("100");
        BigDecimal fee = new BigDecimal("0.01");

        BigDecimal result = TradeVolume.subtractFees(feeComputation, volume, fee);

        assertEquals(new BigDecimal("99").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void testRoundByNullStep() {
        BigDecimal input = new BigDecimal("64.00");
        BigDecimal step = null;

        BigDecimal result = TradeVolume.roundByStep(input, step);

        assertEquals(new BigDecimal("64.00").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void testRoundByFives64() {
        BigDecimal input = new BigDecimal("64.00");
        BigDecimal step = new BigDecimal("5.00");

        BigDecimal result = TradeVolume.roundByStep(input, step);

        assertEquals(new BigDecimal("65.00"), result);
    }

    @Test
    public void testRoundByFives65() {
        BigDecimal input = new BigDecimal("65.00");
        BigDecimal step = new BigDecimal("5.00");

        BigDecimal result = TradeVolume.roundByStep(input, step);

        assertEquals(new BigDecimal("65.00"), result);
    }

    @Test
    public void testRoundByFives66() {
        BigDecimal input = new BigDecimal("66.00");
        BigDecimal step = new BigDecimal("5.00");

        BigDecimal result = TradeVolume.roundByStep(input, step);

        assertEquals(new BigDecimal("65.00"), result);
    }

    @Test
    public void testRoundByTens64() {
        BigDecimal input = new BigDecimal("64.00");
        BigDecimal step = new BigDecimal("10.00");

        BigDecimal result = TradeVolume.roundByStep(input, step);

        assertEquals(new BigDecimal("60.00"), result);
    }

    /*
     * Using HALF_EVEN mode, we round to the nearest neighbor
     * but if there is a tie we prefer the neighbor that is even,
     * so this goes down to 60 instead of up to 70.
     */
    @Test
    public void testRoundByTens65() {
        BigDecimal input = new BigDecimal("65.00");
        BigDecimal step = new BigDecimal("10.00");

        BigDecimal result = TradeVolume.roundByStep(input, step);

        assertEquals(new BigDecimal("60.00"), result);
    }

    @Test
    public void testRoundByTens66() {
        BigDecimal input = new BigDecimal("66.00");
        BigDecimal step = new BigDecimal("10.00");

        BigDecimal result = TradeVolume.roundByStep(input, step);

        assertEquals(new BigDecimal("70.00"), result);
    }

    /*
     * Using HALF_EVEN mode, we round to the nearest neighbor
     * but if there is a tie we prefer the neighbor that is even,
     * so this goes up to 80 instead of down to 70.
     */
    @Test
    public void testRoundByTens75() {
        BigDecimal input = new BigDecimal("75.00");
        BigDecimal step = new BigDecimal("10.00");

        BigDecimal result = TradeVolume.roundByStep(input, step);

        assertEquals(new BigDecimal("80.00"), result);
    }

    @Test
    public void testRoundByDecimals34() {
        BigDecimal input = new BigDecimal("0.034379584992664");
        BigDecimal step = new BigDecimal("0.01");

        BigDecimal result = TradeVolume.roundByStep(input, step);

        assertEquals(new BigDecimal("0.03"), result);
    }

    @Test
    public void testRoundByDecimals35() {
        BigDecimal input = new BigDecimal("0.035379584992664");
        BigDecimal step = new BigDecimal("0.01");

        BigDecimal result = TradeVolume.roundByStep(input, step);

        assertEquals(new BigDecimal("0.04"), result);
    }

    @Test
    public void testRoundByDecimals36() {
        BigDecimal input = new BigDecimal("0.036379584992664");
        BigDecimal step = new BigDecimal("0.01");

        BigDecimal result = TradeVolume.roundByStep(input, step);

        assertEquals(new BigDecimal("0.04"), result);
    }

}
