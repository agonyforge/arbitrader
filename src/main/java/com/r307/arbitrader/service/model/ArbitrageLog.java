package com.r307.arbitrader.service.model;

import org.apache.commons.text.StringEscapeUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * This class represents each row in the csv file.
 * There are some constrains. All variables must be declared in the same order as they are parsed into CSV
 * in the toCsv() method and vice versa. The same rule applies to the headers (csvHeaders).
 *
 * So, every time you add a new field variable (that you want to be included in the csv) you must also add it
 * to the toCsv method in the same order as it is declared.
 *
 * For retrocompatibility, in case you need to add a new field, it should always be added at the end.
 */
public class ArbitrageLog {
    private static final Field[] CSV_HEADERS = ArbitrageLog.class.getDeclaredFields();

    private String shortExchange;
    private BigDecimal shortSpread;
    private BigDecimal shortSlip;
    private BigDecimal shortAmount;
    private String shortCurrency;

    private String longExchange;
    private BigDecimal longSpread;
    private BigDecimal longSlip;
    private BigDecimal longAmount;
    private String longCurrency;

    private BigDecimal profit;
    private OffsetDateTime timestamp;

    public String getShortExchange() {
        return shortExchange;
    }

    public BigDecimal getShortSpread() {
        return shortSpread;
    }

    public BigDecimal getShortSlip() {
        return shortSlip;
    }

    public BigDecimal getShortAmount() {
        return shortAmount;
    }

    public String getShortCurrency() {
        return shortCurrency;
    }

    public String getLongExchange() {
        return longExchange;
    }

    public BigDecimal getLongSpread() {
        return longSpread;
    }

    public BigDecimal getLongSlip() {
        return longSlip;
    }

    public BigDecimal getLongAmount() {
        return longAmount;
    }

    public String getLongCurrency() {
        return longCurrency;
    }

    public BigDecimal getProfit() {
        return profit;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public String csvHeaders() {
        final StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < CSV_HEADERS.length; i++) {
            final Field field = CSV_HEADERS[i];
            final String genericTypeName = field.getGenericType().getTypeName();

            if (!genericTypeName.equalsIgnoreCase("java.lang.reflect.Field[]")) {
                final String csvHeader = field.getName();
                stringBuilder.append("\"").append(StringEscapeUtils.escapeCsv(csvHeader)).append("\"");

                if (i != CSV_HEADERS.length - 1) {
                    stringBuilder.append(",");
                }
            }
        }
        return stringBuilder.append("\n").toString();
    }

    public String toCsv() {
        return new StringBuilder("\"")
            .append(StringEscapeUtils.escapeCsv(shortExchange))
            .append("\",\"")
            .append(StringEscapeUtils.escapeCsv(shortSpread.toPlainString()))
            .append("\",\"")
            .append(StringEscapeUtils.escapeCsv(shortSlip.toPlainString()))
            .append("\",\"")
            .append(StringEscapeUtils.escapeCsv(shortAmount.toPlainString()))
            .append("\",\"")
            .append(StringEscapeUtils.escapeCsv(shortCurrency))
            .append("\",\"")
            .append(StringEscapeUtils.escapeCsv(longExchange))
            .append("\",\"")
            .append(StringEscapeUtils.escapeCsv(longSpread.toPlainString()))
            .append("\",\"")
            .append(StringEscapeUtils.escapeCsv(longSlip.toPlainString()))
            .append("\",\"")
            .append(StringEscapeUtils.escapeCsv(longAmount.toPlainString()))
            .append("\",\"")
            .append(StringEscapeUtils.escapeCsv(longCurrency))
            .append("\",\"")
            .append(StringEscapeUtils.escapeCsv(profit.toPlainString()))
            .append("\",\"")
            .append(StringEscapeUtils.escapeCsv(timestamp.toString()))
            .append("\"")
            .append("\n")
            .toString();
    }

    public static final class ArbitrageLogBuilder {
        private String shortExchange;
        private BigDecimal shortSpread;
        private BigDecimal shortSlip;
        private BigDecimal shortAmount;
        private String shortCurrency;
        private String longExchange;
        private BigDecimal longSpread;
        private BigDecimal longSlip;
        private BigDecimal longAmount;
        private String longCurrency;
        private BigDecimal profit;
        private OffsetDateTime timestamp;

        public static ArbitrageLogBuilder builder() {
            return new ArbitrageLogBuilder();
        }

        public ArbitrageLogBuilder withShortExchange(String shortExchange) {
            this.shortExchange = shortExchange;
            return this;
        }

        public ArbitrageLogBuilder withShortSpread(BigDecimal shortSpread) {
            this.shortSpread = shortSpread;
            return this;
        }

        public ArbitrageLogBuilder withShortSlip(BigDecimal shortSlip) {
            this.shortSlip = shortSlip;
            return this;
        }

        public ArbitrageLogBuilder withShortAmount(BigDecimal shortAmount) {
            this.shortAmount = shortAmount;
            return this;
        }

        public ArbitrageLogBuilder withShortCurrency(String shortCurrency) {
            this.shortCurrency = shortCurrency;
            return this;
        }

        public ArbitrageLogBuilder withLongExchange(String longExchange) {
            this.longExchange = longExchange;
            return this;
        }

        public ArbitrageLogBuilder withLongSpread(BigDecimal longSpread) {
            this.longSpread = longSpread;
            return this;
        }

        public ArbitrageLogBuilder withLongSlip(BigDecimal longSlip) {
            this.longSlip = longSlip;
            return this;
        }

        public ArbitrageLogBuilder withLongAmount(BigDecimal longAmount) {
            this.longAmount = longAmount;
            return this;
        }

        public ArbitrageLogBuilder withLongCurrency(String longCurrency) {
            this.longCurrency = longCurrency;
            return this;
        }

        public ArbitrageLogBuilder withProfit(BigDecimal profit) {
            this.profit = profit;
            return this;
        }

        public ArbitrageLogBuilder withTimestamp(OffsetDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public com.r307.arbitrader.service.model.ArbitrageLog build() {
            com.r307.arbitrader.service.model.ArbitrageLog arbitrageLog = new com.r307.arbitrader.service.model.ArbitrageLog();
            arbitrageLog.longSpread = this.longSpread;
            arbitrageLog.shortSpread = this.shortSpread;
            arbitrageLog.shortSlip = this.shortSlip;
            arbitrageLog.longAmount = this.longAmount;
            arbitrageLog.profit = this.profit;
            arbitrageLog.longExchange = this.longExchange;
            arbitrageLog.shortCurrency = this.shortCurrency;
            arbitrageLog.shortExchange = this.shortExchange;
            arbitrageLog.longCurrency = this.longCurrency;
            arbitrageLog.longSlip = this.longSlip;
            arbitrageLog.timestamp = this.timestamp;
            arbitrageLog.shortAmount = this.shortAmount;
            return arbitrageLog;
        }
    }
}
