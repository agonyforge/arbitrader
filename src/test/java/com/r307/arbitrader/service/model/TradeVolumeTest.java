package com.r307.arbitrader.service.model;

import com.r307.arbitrader.config.FeeComputation;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;
import static com.r307.arbitrader.DecimalConstants.USD_SCALE;
import static org.junit.Assert.*;

public class TradeVolumeTest {

    @Test
    public void enterAndExitSameVolumeSERVERSERVER() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        ExchangeFee longFee = new ExchangeFee(new BigDecimal("0.001"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.001"), BigDecimal.ZERO);
        int longScale = 5;
        int shortScale = 6;

        EntryTradeVolume entryTradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, BigDecimal.ZERO, longScale, shortScale);
        entryTradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null);
        ExitTradeVolume exitTradeVolume =ExitTradeVolume.getExitTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, entryTradeVolume.getLongOrderVolume(), entryTradeVolume.getShortOrderVolume(), longFee, shortFee, longScale, shortScale);
        exitTradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null);

        assertEquals(entryTradeVolume.getLongVolume(), exitTradeVolume.getLongVolume());
        assertEquals(entryTradeVolume.getShortVolume(), exitTradeVolume.getShortVolume());
        //Test that we are not selling more than we bought
        assertTrue(entryTradeVolume.getLongVolume().compareTo(exitTradeVolume.getLongVolume())>=0);
        assertTrue(entryTradeVolume.getShortVolume().compareTo(exitTradeVolume.getShortVolume())<=0);
    }

    @Test
    public void enterAndExitSameVolumeSERVERCLIENT() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        ExchangeFee longFee = new ExchangeFee(new BigDecimal("0.001"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.001"), BigDecimal.ZERO);

        int longScale = 6;
        int shortScale = 6;

        EntryTradeVolume entryTradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.CLIENT, longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, BigDecimal.ZERO, longScale, shortScale);
        entryTradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null);
        ExitTradeVolume exitTradeVolume =ExitTradeVolume.getExitTradeVolume(FeeComputation.SERVER, FeeComputation.CLIENT, entryTradeVolume.getLongOrderVolume(), entryTradeVolume.getShortOrderVolume(), longFee, shortFee, longScale, shortScale);
        exitTradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null);

        assertEquals(entryTradeVolume.getLongVolume().setScale(longScale-1, RoundingMode.DOWN), exitTradeVolume.getLongVolume().setScale(longScale-1, RoundingMode.DOWN));
        assertEquals(entryTradeVolume.getShortVolume().setScale(shortScale-1, RoundingMode.DOWN), exitTradeVolume.getShortVolume().setScale(shortScale-1, RoundingMode.DOWN));
        //Test that we are not selling more than we bought
        assertTrue(entryTradeVolume.getLongVolume().compareTo(exitTradeVolume.getLongVolume())>=0);
        assertTrue(entryTradeVolume.getShortVolume().compareTo(exitTradeVolume.getShortVolume())<=0);
    }

    @Test
    public void enterAndExitSameVolumeCLIENTSERVER() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        ExchangeFee longFee = new ExchangeFee(new BigDecimal("0.001"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.001"), BigDecimal.ZERO);


        int longScale = 6;
        int shortScale = 6;

        EntryTradeVolume entryTradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.CLIENT, FeeComputation.SERVER, longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, BigDecimal.ZERO, longScale, shortScale);
        entryTradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null);
        ExitTradeVolume exitTradeVolume =ExitTradeVolume.getExitTradeVolume(FeeComputation.CLIENT, FeeComputation.SERVER, entryTradeVolume.getLongOrderVolume(), entryTradeVolume.getShortOrderVolume(), longFee, shortFee, longScale, shortScale);
        exitTradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null);

        assertEquals(entryTradeVolume.getLongVolume().setScale(longScale-1, RoundingMode.DOWN), exitTradeVolume.getLongVolume().setScale(longScale-1, RoundingMode.DOWN));
        assertEquals(entryTradeVolume.getShortVolume().setScale(shortScale-1, RoundingMode.DOWN), exitTradeVolume.getShortVolume().setScale(shortScale-1, RoundingMode.DOWN));
        //Test that we are not selling more than we bought
        assertTrue(entryTradeVolume.getLongVolume().compareTo(exitTradeVolume.getLongVolume())>=0);
        assertTrue(entryTradeVolume.getShortVolume().compareTo(exitTradeVolume.getShortVolume())<=0);
    }

    @Test
    public void enterAndExitSameVolumeCLIENTCLIENT() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        ExchangeFee longBaseFee = new ExchangeFee(new BigDecimal("0.001"), null);
        ExchangeFee shortBaseFee = new ExchangeFee(new BigDecimal("0.001"), BigDecimal.ZERO);

        int longScale = 6;
        int shortScale = 6;

        EntryTradeVolume entryTradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.CLIENT, FeeComputation.CLIENT, longMaxExposure, shortMaxExposure, longPrice, shortPrice, longBaseFee, shortBaseFee, BigDecimal.ZERO, longScale, shortScale);
        entryTradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null);
        ExitTradeVolume exitTradeVolume =ExitTradeVolume.getExitTradeVolume(FeeComputation.CLIENT, FeeComputation.CLIENT, entryTradeVolume.getLongOrderVolume(), entryTradeVolume.getShortOrderVolume(), longBaseFee, shortBaseFee, longScale, shortScale);
        exitTradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null);

        assertEquals(entryTradeVolume.getLongVolume().setScale(longScale-1, RoundingMode.DOWN), exitTradeVolume.getLongVolume().setScale(longScale-1, RoundingMode.DOWN));
        assertEquals(entryTradeVolume.getShortVolume().setScale(shortScale-1, RoundingMode.DOWN), exitTradeVolume.getShortVolume().setScale(shortScale-1, RoundingMode.DOWN));
        //Test that we are not selling more than we bought
        assertTrue(entryTradeVolume.getLongVolume().compareTo(exitTradeVolume.getLongVolume())>=0);
        assertTrue(entryTradeVolume.getShortVolume().compareTo(exitTradeVolume.getShortVolume())<=0);
    }




    @Test
    public void exitAdjustVolumeScaleSERVER56() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        ExitTradeVolume tradeVolume = TradeVolume.getExitTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, 5, 6);
        tradeVolume.longVolume=new BigDecimal("100").setScale(5, RoundingMode.HALF_EVEN);
        tradeVolume.shortVolume=new BigDecimal("100").setScale(6, RoundingMode.HALF_EVEN);
        tradeVolume.longFee=new BigDecimal("0.05");
        tradeVolume.shortFee=new BigDecimal("0.01");

        tradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null);

        assertEquals(5, tradeVolume.getLongOrderVolume().scale());
        assertEquals(6, tradeVolume.getShortOrderVolume().scale());
    }

    @Test
    public void exitAdjustVolumeScaleThrows() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        ExitTradeVolume tradeVolume = TradeVolume.getExitTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee,5,6);
        tradeVolume.longVolume=new BigDecimal("100").setScale(3, RoundingMode.HALF_EVEN);
        tradeVolume.shortVolume=new BigDecimal("100").setScale(7, RoundingMode.HALF_EVEN);
        tradeVolume.longFee=new BigDecimal("0.05");
        tradeVolume.shortFee=new BigDecimal("0.01");

        assertThrows(IllegalArgumentException.class, ()->tradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null));

    }


    @Test
    public void entryAdjustVolumeScaleSERVER8() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER,  BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 8, 8);
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longVolume=new BigDecimal("100");
        tradeVolume.longFee=new BigDecimal("0.05");
        tradeVolume.shortFee=new BigDecimal("0.01");

        tradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null);
        BigDecimal result = tradeVolume.getMarketNeutralityRating();

        //Cannot test for perfect market neutrality because of the need to scale both volume
        //Instead test that the market neutrality rating is very close to true market neutrality
        BigDecimal threshold = new BigDecimal("0.0001");
        assertTrue("Trade volume is not close enough to market neutrality", result.subtract(BigDecimal.ONE).abs().compareTo(threshold) <0);

        assertEquals(BTC_SCALE, tradeVolume.getLongOrderVolume().scale());
        assertEquals(BTC_SCALE, tradeVolume.getShortOrderVolume().scale());
    }

    @Test
    public void entryAdjustVolumeScaleCLIENT63() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.CLIENT, FeeComputation.CLIENT,  BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 6,3);
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longVolume=new BigDecimal("100");
        tradeVolume.longFee=new BigDecimal("0.05");
        tradeVolume.shortFee=new BigDecimal("0.01");

        tradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null);
        BigDecimal result = tradeVolume.getMarketNeutralityRating();

        //Cannot test for perfect market neutrality because of the need to scale both volume
        //Instead test that the market neutrality rating is very close to true market neutrality
        BigDecimal threshold = new BigDecimal("0.0001");
        assertTrue("Trade volume is not close enough to market neutrality", result.subtract(BigDecimal.ONE).abs().compareTo(threshold) <0);


        assertEquals(6, tradeVolume.getLongOrderVolume().scale());
        assertEquals(3, tradeVolume.getShortOrderVolume().scale());
    }

    @Test
    public void entryAdjustVolumeScaleCLIENT36() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.CLIENT, FeeComputation.CLIENT,  BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 3,6);
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longVolume=new BigDecimal("100");
        tradeVolume.longFee=new BigDecimal("0.05");
        tradeVolume.shortFee=new BigDecimal("0.01");

        tradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null);
        BigDecimal result = tradeVolume.getMarketNeutralityRating();

        //Cannot test for perfect market neutrality because of the need to scale both volume
        //Instead test that the market neutrality rating is very close to true market neutrality
        BigDecimal threshold = new BigDecimal("0.0001");
        assertTrue("Trade volume is not close enough to market neutrality", result.subtract(BigDecimal.ONE).abs().compareTo(threshold) <0);


        assertEquals(3, tradeVolume.getLongOrderVolume().scale());
        assertEquals(6, tradeVolume.getShortOrderVolume().scale());
    }

    @Test
    public void entryAdjustVolumeStepSizeSERVER23() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER,  BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 3,6);
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longVolume=new BigDecimal("100");
        tradeVolume.longFee=new BigDecimal("0.05");
        tradeVolume.shortFee=new BigDecimal("0.01");

        tradeVolume.adjustOrderVolume("longExchange", "shortExchange", new BigDecimal("2"), new BigDecimal("3"));

        //Cannot test market neutrality when both exchanges uses amount step size


        assertEquals(0, tradeVolume.getLongOrderVolume().remainder(new BigDecimal("2")).compareTo(BigDecimal.ZERO));
        assertEquals(0, tradeVolume.getShortOrderVolume().remainder(new BigDecimal("3")).compareTo(BigDecimal.ZERO));
        assertEquals(3, tradeVolume.getLongOrderVolume().scale());
        assertEquals(6, tradeVolume.getShortOrderVolume().scale());
    }


    @Test
    public void entryAdjustVolumeStepSizeCLIENTSERVERx3() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.CLIENT, FeeComputation.SERVER,  BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 5,2);
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longVolume=new BigDecimal("100");
        tradeVolume.longFee=new BigDecimal("0.05");
        tradeVolume.shortFee=new BigDecimal("0.01");

        tradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, new BigDecimal("3"));
        BigDecimal result = tradeVolume.getMarketNeutralityRating();

        //Cannot test for perfect market neutrality because of the need to scale both volume
        //Instead test that the market neutrality rating is very close to true market neutrality
        BigDecimal threshold = new BigDecimal("0.0001");
        assertTrue("Trade volume is not close enough to market neutrality", result.subtract(BigDecimal.ONE).abs().compareTo(threshold) <0);

        assertEquals(0, tradeVolume.getShortOrderVolume().remainder(new BigDecimal("3")).compareTo(BigDecimal.ZERO));
        assertEquals(5, tradeVolume.getLongOrderVolume().scale());
        assertEquals(2, tradeVolume.getShortOrderVolume().scale());
    }

    @Test
    public void isMarketNeutralAfterConstructor() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        ExchangeFee longFee = new ExchangeFee(new BigDecimal("0.001"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.001"), BigDecimal.ZERO);

        boolean result = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, BigDecimal.ZERO, 8, 8)
            .isMarketNeutral();

        assertTrue("After construction a trade volume is expected to be perfectly market neutral",  result);
    }

    @Test
    public void isMarketNeutralAfterConstructorCLIENT() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        ExchangeFee longFee = new ExchangeFee(new BigDecimal("0.001"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.001"), BigDecimal.ZERO);

        boolean result = EntryTradeVolume.getEntryTradeVolume(FeeComputation.CLIENT, FeeComputation.CLIENT, longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, BigDecimal.ZERO, 8, 8)
            .isMarketNeutral();

        assertTrue("After construction a trade volume is expected to be perfectly market neutral",  result);
    }

    @Test
    public void isMarketNeutralAfterConstructorCLIENTSERVER() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        ExchangeFee longFee = new ExchangeFee(new BigDecimal("0.001"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.001"), BigDecimal.ZERO);

        boolean result = EntryTradeVolume.getEntryTradeVolume(FeeComputation.CLIENT, FeeComputation.SERVER, longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, BigDecimal.ZERO, 8, 8)
            .isMarketNeutral();

        assertTrue("After construction a trade volume is expected to be perfectly market neutral",  result);
    }

    @Test
    public void isMarketNeutralZERO() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER,  BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 8, 8);
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longVolume=new BigDecimal("100");
        tradeVolume.longFee=new BigDecimal("0.05");
        tradeVolume.shortFee=new BigDecimal("0.01");

        boolean result = tradeVolume.isMarketNeutral();

        assertTrue(result);
    }

    @Test
    public void isMarketNeutralBelow() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 8, 8);
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longVolume=new BigDecimal("0.99");
        tradeVolume.longFee=new BigDecimal("0.05");
        tradeVolume.shortFee=new BigDecimal("0.00");

        boolean result = tradeVolume.isMarketNeutral();

        assertFalse(result);
    }

    @Test
    public void isMarketNeutralTWO() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 8, 8);
        tradeVolume.longVolume=new BigDecimal("110");
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longFee=new BigDecimal("0.05");
        tradeVolume.shortFee=new BigDecimal("0.0");

        boolean result = tradeVolume.isMarketNeutral();

        assertTrue(result);
    }

    @Test
    public void isMarketNeutralAbove() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 8, 8);
        tradeVolume.longVolume=new BigDecimal("111");
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longFee=new BigDecimal("0.05");
        tradeVolume.shortFee=new BigDecimal("0.0");

        boolean result = tradeVolume.isMarketNeutral();

        assertFalse(result);
    }

    @Test
    public void getMarketNeutralityRatingAfterConstructorSERVER() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        ExchangeFee longFee = new ExchangeFee(new BigDecimal("0.001"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.001"), BigDecimal.ZERO);

        BigDecimal result = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER,  longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee,BigDecimal.ZERO, 8, 8)
            .getMarketNeutralityRating();

        assertEquals("After construction a trade volume is expected to be perfectly market neutral", BigDecimal.ONE.setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getMarketNeutralityRatingAfterConstructorCLIENT() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        ExchangeFee longFee = new ExchangeFee( new BigDecimal("0.001"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.001"), BigDecimal.ZERO);

        BigDecimal result = EntryTradeVolume.getEntryTradeVolume(FeeComputation.CLIENT, FeeComputation.CLIENT,  longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, BigDecimal.ZERO, 8, 8)
            .getMarketNeutralityRating();

        assertEquals("After construction a trade volume is expected to be perfectly market neutral", new BigDecimal("1").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getMarketNeutralityRatingAfterConstructorCLIENTSERVER() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        ExchangeFee longFee = new ExchangeFee(new BigDecimal("0.001"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.001"), BigDecimal.ZERO);

        BigDecimal result = EntryTradeVolume.getEntryTradeVolume(FeeComputation.CLIENT, FeeComputation.SERVER,  longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee,BigDecimal.ZERO, 8, 8)
            .getMarketNeutralityRating();

        assertEquals("After construction a trade volume is expected to be perfectly market neutral", new BigDecimal("1").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getMarketNeutralityRatingAfterConstructorSERVERCLIENT() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        ExchangeFee longFee = new ExchangeFee(new BigDecimal("0.001"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.001"), BigDecimal.ZERO);

        BigDecimal result = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.CLIENT,  longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee,BigDecimal.ZERO, 8, 8)
            .getMarketNeutralityRating();

        assertEquals("After construction a trade volume is expected to be perfectly market neutral", new BigDecimal("1").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }
    @Test
    public void getMarketNeutralityRatingZERO() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 8, 8);
        tradeVolume.longVolume=new BigDecimal("100");
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longFee=new BigDecimal("0.05");
        tradeVolume.shortFee=new BigDecimal("0.01");

        BigDecimal result = tradeVolume.getMarketNeutralityRating();

        assertEquals(new BigDecimal("0").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getMarketNeutralityRatingONE() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 8, 8);
        tradeVolume.longVolume=new BigDecimal("105");
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longFee=new BigDecimal("0.0");
        tradeVolume.shortFee=new BigDecimal("0.05");

        BigDecimal result = tradeVolume.getMarketNeutralityRating();

        assertEquals(new BigDecimal("1").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getMarketNeutralityRatingTWO() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 8, 8);
        tradeVolume.longVolume=new BigDecimal("110");
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longFee=new BigDecimal("0.0");
        tradeVolume.shortFee=new BigDecimal("0.05");

        BigDecimal result = tradeVolume.getMarketNeutralityRating();

        assertEquals(new BigDecimal("2").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getMarketNeutralityRatingTHREE() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 8, 8);
        tradeVolume.longVolume=new BigDecimal("115");
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longFee=new BigDecimal("0.0");
        tradeVolume.shortFee=new BigDecimal("0.05");

        BigDecimal result = tradeVolume.getMarketNeutralityRating();

        assertEquals(new BigDecimal("3").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getMarketNeutralityRating() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 8, 8);
        tradeVolume.longVolume=new BigDecimal("103");
        tradeVolume.shortVolume=new BigDecimal("100");
        tradeVolume.longFee=new BigDecimal("0.05");
        tradeVolume.shortFee=new BigDecimal("0.01");

        BigDecimal result = tradeVolume.getMarketNeutralityRating();

        assertEquals(new BigDecimal("0.475000000000001").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }


    @Test
    public void getShortToLongVolumeTargetRatioNoFee() {
        BigDecimal longFee = new BigDecimal("0");
        BigDecimal shortFee = new BigDecimal("0");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("1").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortToLongVolumeTargetRatio005and001() {
        BigDecimal longFee = new BigDecimal("0.05");
        BigDecimal shortFee = new BigDecimal("0.01");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("1.06315789473684").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortToLongVolumeTargetRatio01and001() {
        BigDecimal longFee = new BigDecimal("0.1");
        BigDecimal shortFee = new BigDecimal("0.01");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("1.12222222222222").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortToLongVolumeTargetRatio001and005() {
        BigDecimal longFee = new BigDecimal("0.01");
        BigDecimal shortFee = new BigDecimal("0.05");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("1.06060606060606").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortToLongVolumeTargetRatio0001and00026() {
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.0026");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("1.0036036036036").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortToLongVolumeTargetRatio0005and00026() {
        BigDecimal longFee = new BigDecimal("0.005");
        BigDecimal shortFee = new BigDecimal("0.0026");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("1.00763819095477").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortToLongVolumeTargetRatio001and002() {
        BigDecimal longFee = new BigDecimal("0.01");
        BigDecimal shortFee = new BigDecimal("0.002");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("1.01212121212121").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortToLongVolumeTargetRatio0001and0003() {
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.003");

        BigDecimal result = EntryTradeVolume.getShortToLongVolumeTargetRatio(longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("1.004004004004").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }



    @Test
    public void getShortVolumeFromLongNoFee() {
        BigDecimal longVolume = new BigDecimal ("100");
        BigDecimal longFee = new BigDecimal("0");
        BigDecimal shortFee = new BigDecimal("0");

        BigDecimal result = EntryTradeVolume.getShortVolumeFromLong(longVolume, longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("100").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortVolumeFromLongHighVolume() {
        BigDecimal longVolume = new BigDecimal ("200");
        BigDecimal longFee = new BigDecimal("0.01");
        BigDecimal shortFee = new BigDecimal("0.01");

        BigDecimal result = EntryTradeVolume.getShortVolumeFromLong(longVolume, longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("196.039603960396").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortVolumeFromLongHighLongFee() {
        BigDecimal longVolume = new BigDecimal ("100");
        BigDecimal longFee = new BigDecimal("0.05");
        BigDecimal shortFee = new BigDecimal("0.001");

        BigDecimal result = EntryTradeVolume.getShortVolumeFromLong(longVolume, longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("94.9050949050949").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getShortVolumeFromLongHighShortFee() {
        BigDecimal longVolume = new BigDecimal ("100");
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.05");

        BigDecimal result = EntryTradeVolume.getShortVolumeFromLong(longVolume, longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("95.1428571428571").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getLongVolumeFromShortNoFee() {
        BigDecimal shortVolume = new BigDecimal ("100");
        BigDecimal longFee = new BigDecimal("0");
        BigDecimal shortFee = new BigDecimal("0");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromShort(shortVolume, longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("100").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getLongVolumeFromShortHighVolume() {
        BigDecimal shortVolume = new BigDecimal ("200");
        BigDecimal longFee = new BigDecimal("0.01");
        BigDecimal shortFee = new BigDecimal("0.01");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromShort(shortVolume, longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("204.040404040404").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getLongVolumeFromShortHighLongFee() {
        BigDecimal shortVolume = new BigDecimal ("100");
        BigDecimal longFee = new BigDecimal("0.05");
        BigDecimal shortFee = new BigDecimal("0.001");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromShort(shortVolume, longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("105.368421052632").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getLongVolumeFromShortHighShortFee() {
        BigDecimal shortVolume = new BigDecimal ("100");
        BigDecimal longFee = new BigDecimal("0.001");
        BigDecimal shortFee = new BigDecimal("0.05");

        BigDecimal result = EntryTradeVolume.getLongVolumeFromShort(shortVolume, longFee, shortFee, BigDecimal.ZERO,12);

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

        BigDecimal result = EntryTradeVolume.getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, BigDecimal.ZERO, 12);

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

        BigDecimal result = EntryTradeVolume.getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, BigDecimal.ZERO, 12);

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

        BigDecimal result = EntryTradeVolume.getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, BigDecimal.ZERO,12);

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

        BigDecimal result = EntryTradeVolume.getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, BigDecimal.ZERO,12);

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

        BigDecimal result = EntryTradeVolume.getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee,BigDecimal.ZERO,12);

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

        BigDecimal result = EntryTradeVolume.getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, BigDecimal.ZERO,12);

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

        BigDecimal result = EntryTradeVolume.getLongVolumeFromExposures(longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, BigDecimal.ZERO,12);

        assertEquals(new BigDecimal("0.0668001334668").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), result.setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void getBuyBaseFees() {
        FeeComputation feeComputation = FeeComputation.SERVER;
        BigDecimal volume = new BigDecimal("100").setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.001");

        BigDecimal baseFees = TradeVolume.getBuyBaseFees(feeComputation,volume,baseFee, true);

        assertEquals(0, BigDecimal.ZERO.compareTo(baseFees));
    }

    @Test
    public void getBuyBaseFeesFromOrderVolumeCLIENT() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal orderVolume = new BigDecimal("100").setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.001");

        BigDecimal baseFees = TradeVolume.getBuyBaseFees(feeComputation,orderVolume,baseFee, true);

        assertEquals(new BigDecimal("0.1").setScale(4, RoundingMode.HALF_EVEN),baseFees );
    }

    @Test
    public void getBuyBaseFeesFromVolumeCLIENT1() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("789").setScale(1, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.0026");

        BigDecimal baseFees = TradeVolume.getBuyBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.add(baseFees);
        BigDecimal baseFees2 = TradeVolume.getBuyBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getBuyBaseFeesFromVolumeCLIENT2() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("78.37").setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.0026");

        BigDecimal baseFees = TradeVolume.getBuyBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.add(baseFees);
        BigDecimal baseFees2 = TradeVolume.getBuyBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getBuyBaseFeesFromVolumeCLIENT3() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("216.89").setScale(3, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.001");

        BigDecimal baseFees = TradeVolume.getBuyBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.add(baseFees);
        BigDecimal baseFees2 = TradeVolume.getBuyBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getBuyBaseFeesFromVolumeCLIENT4() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("23.0003").setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.009");

        BigDecimal baseFees = TradeVolume.getBuyBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.add(baseFees);
        BigDecimal baseFees2 = TradeVolume.getBuyBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getBuyBaseFeesFromVolumeCLIENT5() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("938.9874").setScale(5, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.007");

        BigDecimal baseFees = TradeVolume.getBuyBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.add(baseFees);
        BigDecimal baseFees2 = TradeVolume.getBuyBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getBuyBaseFeesFromVolumeCLIENT6() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("0.398300").setScale(6, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.002");

        BigDecimal baseFees = TradeVolume.getBuyBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.add(baseFees);
        BigDecimal baseFees2 = TradeVolume.getBuyBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getBuyBaseFeesFromVolumeCLIENT7() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("1.59").setScale(7, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.003");

        BigDecimal baseFees = TradeVolume.getBuyBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.add(baseFees);
        BigDecimal baseFees2 = TradeVolume.getBuyBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getBuyBaseFeesFromVolumeCLIENT8() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("199.03").setScale(8, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.005");

        BigDecimal baseFees = TradeVolume.getBuyBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.add(baseFees);
        BigDecimal baseFees2 = TradeVolume.getBuyBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getSellBaseFees() {
        FeeComputation feeComputation = FeeComputation.SERVER;
        BigDecimal volume = new BigDecimal("100").setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.001");

        BigDecimal baseFees = TradeVolume.getSellBaseFees(feeComputation,volume,baseFee, true);

        assertEquals(0, BigDecimal.ZERO.compareTo(baseFees));
    }

    @Test
    public void getSellBaseFeesFromOrderVolumeCLIENT() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal orderVolume = new BigDecimal("100").setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.001");

        BigDecimal baseFees = TradeVolume.getSellBaseFees(feeComputation,orderVolume,baseFee, true);

        assertEquals(new BigDecimal("0.1").setScale(4, RoundingMode.HALF_EVEN),baseFees );
    }

    @Test
    public void getSellBaseFeesFromVolumeCLIENT1() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("0.9").setScale(1, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.009");

        BigDecimal baseFees = TradeVolume.getSellBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.subtract(baseFees);
        BigDecimal baseFees2 = TradeVolume.getSellBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getSellBaseFeesFromVolumeCLIENT2() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("78.37").setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.0026");

        BigDecimal baseFees = TradeVolume.getSellBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.subtract(baseFees);
        BigDecimal baseFees2 = TradeVolume.getSellBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getSellBaseFeesFromVolumeCLIENT3() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("0.026").setScale(3, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.001");

        BigDecimal baseFees = TradeVolume.getSellBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.subtract(baseFees);
        BigDecimal baseFees2 = TradeVolume.getSellBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getSellBaseFeesFromVolumeCLIENT4() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("23.0003").setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.001");

        BigDecimal baseFees = TradeVolume.getSellBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.subtract(baseFees);
        BigDecimal baseFees2 = TradeVolume.getSellBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getSellBaseFeesFromVolumeCLIENT5() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("938.9874").setScale(5, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.007");

        BigDecimal baseFees = TradeVolume.getSellBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.subtract(baseFees);
        BigDecimal baseFees2 = TradeVolume.getSellBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getSellBaseFeesFromVolumeCLIENT6() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("0.398300").setScale(6, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.002");

        BigDecimal baseFees = TradeVolume.getSellBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.subtract(baseFees);
        BigDecimal baseFees2 = TradeVolume.getSellBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getSellBaseFeesFromVolumeCLIENT7() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("1.59").setScale(7, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.003");

        BigDecimal baseFees = TradeVolume.getSellBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.subtract(baseFees);
        BigDecimal baseFees2 = TradeVolume.getSellBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void getSellBaseFeesFromVolumeCLIENT8() {
        FeeComputation feeComputation = FeeComputation.CLIENT;
        BigDecimal volume = new BigDecimal("199.03").setScale(8, RoundingMode.HALF_EVEN);
        BigDecimal baseFee = new BigDecimal("0.005");

        BigDecimal baseFees = TradeVolume.getSellBaseFees(feeComputation,volume,baseFee, false);

        BigDecimal orderVolume = volume.subtract(baseFees);
        BigDecimal baseFees2 = TradeVolume.getSellBaseFees(feeComputation,orderVolume,baseFee, true);
        assertEquals(baseFees, baseFees2);
    }

    @Test
    public void testRoundByNullStep() {
        BigDecimal input = new BigDecimal("64.00");
        BigDecimal result = TradeVolume.roundByStep(input, null);

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

    @Test
    public void adjustOrderVolumeScale108() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 10, 8);
        tradeVolume.longVolume=new BigDecimal("0.336449304100");
        tradeVolume.shortVolume=new BigDecimal("0.333905010855");
        tradeVolume.longFee=new BigDecimal("0.0026");
        tradeVolume.shortFee=new BigDecimal("0.050");

        tradeVolume.adjustOrderVolume("Kraken","CoinbasePro", null, null);

        assertEquals(10, tradeVolume.getLongOrderVolume().scale());
        assertEquals(8, tradeVolume.getShortOrderVolume().scale());
    }

    @Test
    public void getMinimumProfitREADME() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        BigDecimal exitTarget = BigDecimal.ZERO;
        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, exitTarget, 8, 8);
        tradeVolume.longVolume=new BigDecimal("0.336449304100");
        tradeVolume.shortVolume=new BigDecimal("0.333905010855");
        tradeVolume.longFee=new BigDecimal("0.0026");
        tradeVolume.shortFee=new BigDecimal("0.050");
    }

    @Test
    public void getMinimumProfitPriceUp() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        ExchangeFee longFee = new ExchangeFee(new BigDecimal("0.001"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.001"), BigDecimal.ZERO);
        BigDecimal exitTarget = new BigDecimal("-0.1");

        int longScale = 12;
        int shortScale = 12;

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.CLIENT, longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, exitTarget, longScale, shortScale);
        tradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null);

        BigDecimal shortEntry = tradeVolume.getShortVolume().multiply(shortPrice).multiply(BigDecimal.ONE.subtract(shortFee.getTotalFee()));
        BigDecimal longEntry = tradeVolume.getLongVolume().multiply(longPrice).multiply(BigDecimal.ONE.add(longFee.getTotalFee()));

        BigDecimal longExitPrice = new BigDecimal("2000");
        BigDecimal shortExitPrice = longExitPrice.multiply(BigDecimal.ONE.add(exitTarget));

        BigDecimal shortExit = tradeVolume.getShortVolume().multiply(shortExitPrice).multiply(BigDecimal.ONE.add(shortFee.getTotalFee()));
        BigDecimal longExit = tradeVolume.getLongVolume().multiply(longExitPrice).multiply(BigDecimal.ONE.subtract(longFee.getTotalFee()));

        BigDecimal profit = shortEntry.subtract(shortExit).subtract(longEntry).add(longExit);

        assertEquals(profit.setScale(USD_SCALE, RoundingMode.FLOOR), tradeVolume.getMinimumProfit(longPrice, shortPrice));
    }

    @Test
    public void getMinimumProfitPriceDown() {
        BigDecimal longMaxExposure = new BigDecimal("100");
        BigDecimal shortMaxExposure = new BigDecimal("100");
        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");
        ExchangeFee longFee = new ExchangeFee(new BigDecimal("0.001"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.001"), BigDecimal.ZERO);
        BigDecimal exitTarget = new BigDecimal("-0.1");

        int longScale = 12;
        int shortScale = 12;

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.CLIENT, longMaxExposure, shortMaxExposure, longPrice, shortPrice, longFee, shortFee, exitTarget, longScale, shortScale);
        tradeVolume.adjustOrderVolume("longExchange", "shortExchange", null, null);

        BigDecimal shortEntry = tradeVolume.getShortVolume().multiply(shortPrice).multiply(BigDecimal.ONE.subtract(shortFee.getTotalFee()));
        BigDecimal longEntry = tradeVolume.getLongVolume().multiply(longPrice).multiply(BigDecimal.ONE.add(longFee.getTotalFee()));

        BigDecimal longExitPrice = new BigDecimal("500");
        BigDecimal shortExitPrice = longExitPrice.multiply(BigDecimal.ONE.add(exitTarget));

        BigDecimal shortExit = tradeVolume.getShortVolume().multiply(shortExitPrice).multiply(BigDecimal.ONE.add(shortFee.getTotalFee()));
        BigDecimal longExit = tradeVolume.getLongVolume().multiply(longExitPrice).multiply(BigDecimal.ONE.subtract(longFee.getTotalFee()));

        BigDecimal profit = shortEntry.subtract(shortExit).subtract(longEntry).add(longExit);

        assertEquals(profit.setScale(USD_SCALE, RoundingMode.FLOOR), tradeVolume.getMinimumProfit(longPrice, shortPrice));
    }


    @Test
    public void getMinimumProfit() {
        ExchangeFee longFee = new ExchangeFee(BigDecimal.ZERO, null);
        ExchangeFee shortFee = new ExchangeFee(BigDecimal.ZERO, BigDecimal.ZERO);

        EntryTradeVolume tradeVolume = EntryTradeVolume.getEntryTradeVolume(FeeComputation.SERVER, FeeComputation.SERVER, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, longFee, shortFee, BigDecimal.ZERO, 8, 8);
        tradeVolume.exitSpread=new BigDecimal("-0.02");
        tradeVolume.longVolume=new BigDecimal("100");
        tradeVolume.shortVolume= new BigDecimal("101.83693857");
        tradeVolume.longFee=new BigDecimal("0.001");
        tradeVolume.shortFee=new BigDecimal("0.001");

        BigDecimal longPrice = new BigDecimal("950");
        BigDecimal shortPrice = new BigDecimal("1050");

        assertEquals(new BigDecimal("11726.85").setScale(USD_SCALE, RoundingMode.FLOOR), tradeVolume.getMinimumProfit(longPrice, shortPrice));
    }
}
