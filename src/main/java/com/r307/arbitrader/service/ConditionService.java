package com.r307.arbitrader.service;

import org.apache.commons.io.FileUtils;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A service to detect several different conditions that can control Arbitrader's behavior without exposing
 * the nature of the implementation of those conditions. Decoupling like this provides flexibility in the
 * future in case we want to change how these signals are generated or add other ways of sending the signals.
 */
@Component
public class ConditionService {
    static final String FORCE_OPEN = "force-open";
    static final String FORCE_CLOSE = "force-close";
    static final String EXIT_WHEN_IDLE = "exit-when-idle";
    static final String STATUS = "status";
    static final String BLACKOUT = "blackout";

    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionService.class);

    private final File forceOpenFile = new File(FORCE_OPEN);
    private final File forceCloseFile = new File(FORCE_CLOSE);
    private final File exitWhenIdleFile = new File(EXIT_WHEN_IDLE);
    private final File statusFile = new File(STATUS);
    private final File blackoutFile = new File(BLACKOUT);

    /**
     * Is the "force a trade to open" condition enabled?
     *
     * @param currencyPair A CurrencyPair
     * @param longExchangeName The name of the long Exchange.
     * @param shortExchangeName The name of the short Exchange.
     * @return true if we should force a trade to open.
     */
    public boolean isForceOpenCondition(CurrencyPair currencyPair, String longExchangeName, String shortExchangeName) {
        return forceOpenFile.exists() && evaluateForceOpenCondition(currencyPair, longExchangeName, shortExchangeName);
    }

    private boolean evaluateForceOpenCondition(CurrencyPair currencyPair, String longExchangeName, String shortExchangeName) {
        String exchanges;

        try {
            exchanges = FileUtils.readFileToString(forceOpenFile, Charset.defaultCharset()).trim();
        } catch (IOException e) {
            LOGGER.warn("IOException reading file '{}': {}", FORCE_OPEN, e.getMessage());
            return false;
        }

        // The force-open file should contain the names of the exchanges you want to force a trade on.
        // It's meant to be a tool to aid testing entry and exit on specific pairs of exchanges.
        //
        // In this format: currencyPair longExchange/shortExchange
        // For example: BTC/USD BitFlyer/Kraken
        String current = String.format("%s %s/%s",
            currencyPair,
            longExchangeName,
            shortExchangeName);

        return current.equals(exchanges);
    }

    /**
     * Removes the "force a trade to open" condition.
     */
    public void clearForceOpenCondition() {
        FileUtils.deleteQuietly(forceOpenFile);
    }

    /**
     * Is the "force trades to close" condition enabled?
     *
     * @return true if we should force our open trades to close.
     */
    public boolean isForceCloseCondition() {
        return forceCloseFile.exists();
    }

    /**
     * Removes the "force trades to close" condition.
     */
    public void clearForceCloseCondition() {
        FileUtils.deleteQuietly(forceCloseFile);
    }

    /**
     * Is the "exit when idle" condition enabled?
     *
     * @return true if we should exit the next time the bot is idle.
     */
    public boolean isExitWhenIdleCondition() {
        return exitWhenIdleFile.exists();
    }

    /**
     * Removes the "exit when idle" condition.
     */
    public void clearExitWhenIdleCondition() {
        FileUtils.deleteQuietly(exitWhenIdleFile);
    }

    /**
     * Is the "status report" condition enabled?
     *
     * @return true if we should generate a status report.
     */
    public boolean isStatusCondition() {
        return statusFile.exists();
    }

    /**
     * Removes the "status report" condition.
     */
    public void clearStatusCondition() {
        FileUtils.deleteQuietly(statusFile);
    }

    /**
     * Are we inside a user-configured blackout window?
     *
     * @param exchange The Exchange to check for blackout windows.
     * @return true if we are within a blackout window for the given Exchange.
     */
    public boolean isBlackoutCondition(Exchange exchange) {
        if (!blackoutFile.exists() || !blackoutFile.canRead()) {
            return false;
        }

        try {
            List<String> lines = FileUtils.readLines(blackoutFile, Charset.defaultCharset());

            return lines
                .stream()
                .filter(line -> line.startsWith(exchange.getExchangeSpecification().getExchangeName()))
                .anyMatch(this::checkBlackoutWindow);

        } catch (IOException e) {
            LOGGER.error("Blackout file exists but cannot be read!", e);
        }

        return false;
    }

    // checks a blackout window line to see if the current time is within it
    private boolean checkBlackoutWindow(String line) {
        String[] dateStrings = line.split("[,]");

        if (dateStrings.length != 3) {
            return false;
        }

        ZonedDateTime start = ZonedDateTime.parse(dateStrings[1], DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        ZonedDateTime end = ZonedDateTime.parse(dateStrings[2], DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        ZonedDateTime now = ZonedDateTime.now();

        return now.isAfter(start) && now.isBefore(end);
    }
}
