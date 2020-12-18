package com.r307.arbitrader.service;

import org.apache.commons.io.FileUtils;
import org.knowm.xchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ConditionService {
    static final String FORCE_OPEN = "force-open";
    static final String FORCE_CLOSE = "force-close";
    static final String EXIT_WHEN_IDLE = "exit-when-idle";
    static final String BLACKOUT = "blackout";

    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionService.class);

    private final File forceOpenFile = new File(FORCE_OPEN);
    private final File forceCloseFile = new File(FORCE_CLOSE);
    private final File exitWhenIdleFile = new File(EXIT_WHEN_IDLE);
    private final File blackoutFile = new File(BLACKOUT);

    public boolean isForceOpenCondition() {
        return forceOpenFile.exists();
    }

    public String readForceOpenContent() {
        if (isForceOpenCondition()) {
            try {
                return FileUtils.readFileToString(forceOpenFile, Charset.defaultCharset());
            } catch (IOException e) {
                LOGGER.warn("IOException reading file '{}': {}", FORCE_OPEN, e.getMessage());
            }
        }

        return "";
    }

    public void clearForceOpenCondition() {
        FileUtils.deleteQuietly(forceOpenFile);
    }

    public boolean isForceCloseCondition() {
        return forceCloseFile.exists();
    }

    public void clearForceCloseCondition() {
        FileUtils.deleteQuietly(forceCloseFile);
    }

    public boolean isExitWhenIdleCondition() {
        return exitWhenIdleFile.exists();
    }

    public void clearExitWhenIdleCondition() {
        FileUtils.deleteQuietly(exitWhenIdleFile);
    }

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
