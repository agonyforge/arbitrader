package com.r307.arbitrader;

public final class DecimalConstants {
    public static final int USD_SCALE = 2;
    public static final int BTC_SCALE = 8;

    private DecimalConstants() {
        // this method intentionally left blank
    }

    //An intermediate scale is necessary to limit rounding errors when queueing BigDecimal.divide calls
    public static int getIntermediateScale(int scale) {
        return scale+4;
    }
}
