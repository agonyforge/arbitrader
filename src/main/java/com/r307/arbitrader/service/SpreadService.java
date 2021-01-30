package com.r307.arbitrader.service;

import com.r307.arbitrader.config.TradingConfiguration;
import com.r307.arbitrader.service.model.Spread;
import com.r307.arbitrader.service.model.TradeCombination;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;

/**
 * Services related to computing spreads. A spread is a representation of the amount of difference between two prices.
 */
@Component
public class SpreadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpreadService.class);

    private final Map<String, BigDecimal> minSpreadIn = new HashMap<>();
    private final Map<String, BigDecimal> maxSpreadIn = new HashMap<>();
    private final Map<String, BigDecimal> minSpreadOut = new HashMap<>();
    private final Map<String, BigDecimal> maxSpreadOut = new HashMap<>();
    private final TradingConfiguration tradingConfiguration;
    private final TickerService tickerService;

    public SpreadService(TradingConfiguration tradingConfiguration, TickerService tickerService) {
        this.tradingConfiguration = tradingConfiguration;
        this.tickerService = tickerService;
    }

    /**
     * Update the high and low water marks given a new Spread. Keeping track of the highest and lowest values over time
     * can be useful for figuring out how to configure your entrySpread and exitTarget.
     *
     * @param spread A new Spread.
     */
    void publish(Spread spread) {
        String spreadKey = spreadKey(spread.getLongExchange(), spread.getShortExchange(), spread.getCurrencyPair());

        if (LOGGER.isInfoEnabled() && tradingConfiguration.isSpreadNotifications()) {
            if (spread.getIn().compareTo(maxSpreadIn.getOrDefault(spreadKey, BigDecimal.valueOf(-1))) > 0) {
                LOGGER.info("Record high spreadIn: {}/{} {} {}",
                    spread.getLongExchange().getExchangeSpecification().getExchangeName(),
                    spread.getShortExchange().getExchangeSpecification().getExchangeName(),
                    spread.getCurrencyPair(),
                    spread.getIn());
            }

            if (spread.getOut().compareTo(minSpreadOut.getOrDefault(spreadKey, BigDecimal.valueOf(1))) < 0) {
                LOGGER.info("Record low spreadOut: {}/{} {} {}",
                    spread.getLongExchange().getExchangeSpecification().getExchangeName(),
                    spread.getShortExchange().getExchangeSpecification().getExchangeName(),
                    spread.getCurrencyPair(),
                    spread.getIn());
            }
        }

        minSpreadIn.put(spreadKey, spread.getIn().min(minSpreadIn.getOrDefault(spreadKey, BigDecimal.valueOf(1))));
        maxSpreadIn.put(spreadKey, spread.getIn().max(maxSpreadIn.getOrDefault(spreadKey, BigDecimal.valueOf(-1))));
        minSpreadOut.put(spreadKey, spread.getOut().min(minSpreadOut.getOrDefault(spreadKey, BigDecimal.valueOf(1))));
        maxSpreadOut.put(spreadKey, spread.getOut().max(maxSpreadOut.getOrDefault(spreadKey, BigDecimal.valueOf(-1))));
    }

    /**
     * Periodically display a summary of the high and low water marks that we have tracked.
     */
    @Scheduled(cron = "0 0 0 * * *") // midnight every day
    public void summary() {
        LOGGER.info("Minimum spreadIns:\n{}", buildSummary(minSpreadIn));
        LOGGER.info("Maximum spreadIns:\n{}", buildSummary(maxSpreadIn));
        LOGGER.info("Minimum spreadOuts:\n{}", buildSummary(minSpreadOut));
        LOGGER.info("Maximum spreadOuts:\n{}", buildSummary(maxSpreadOut));
    }

    /**
     * Compute a Spread based on a TradeCombination. We get the exchanges and currency pair from the TradeCombination
     * and then look up the current prices to create a Spread.
     *
     * @param tradeCombination The TradeCombination representing the exchanges and currency pair we're interested in.
     * @return A Spread representing the difference in price between the elements of the TradeCombination.
     */
    public Spread computeSpread(TradeCombination tradeCombination) {
        Exchange longExchange = tradeCombination.getLongExchange();
        Exchange shortExchange = tradeCombination.getShortExchange();
        CurrencyPair currencyPair = tradeCombination.getCurrencyPair();

        Ticker longTicker = tickerService.getTicker(longExchange, currencyPair);
        Ticker shortTicker = tickerService.getTicker(shortExchange, currencyPair);

        if (tickerService.isInvalidTicker(longTicker) || tickerService.isInvalidTicker(shortTicker)) {
            return null;
        }

        // A Spread is a combination of a spread "in" and spread "out".
        // "in" matches against entrySpread to see if the prices are ready to enter a position.
        // "out" matches against exitTarget to see if the prices are ready to exit a position.
        BigDecimal spreadIn = computeSpread(longTicker.getAsk(), shortTicker.getBid());
        BigDecimal spreadOut = computeSpread(longTicker.getBid(), shortTicker.getAsk());

        Spread spread = new Spread(
            currencyPair,
            longExchange,
            shortExchange,
            longTicker,
            shortTicker,
            spreadIn,
            spreadOut);

        // track high and low water marks
        publish(spread);

        return spread;
    }

    /**
     * The formula is: spread = (short - long) / long
     * That gives us a percentage. For example:
     *   0.008 means the short price is 0.8% higher than the long price.
     *   -0.003 means the long price is 0.3% higher than the short price.
     *   0.000 means the prices are equal to one another.
     *
     * @param longPrice The price on the long exchange.
     * @param shortPrice The price on the short exchange.
     * @return The spread, or percentage difference between the two prices.
     */
    public BigDecimal computeSpread(BigDecimal longPrice, BigDecimal shortPrice) {
        BigDecimal scaledLongPrice = longPrice.setScale(BTC_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal scaledShortPrice = shortPrice.setScale(BTC_SCALE, RoundingMode.HALF_EVEN);

        return (scaledShortPrice.subtract(scaledLongPrice)).divide(scaledLongPrice, RoundingMode.HALF_EVEN);
    }

    // build a summary of the contents of a spread map (high/low water marks)
    private String buildSummary(Map<String, BigDecimal> spreadMap) {
        return spreadMap.entrySet()
            .stream()
            .map(entry -> {
                String[] keyElements = entry.getKey().split(":");
                Map<String, String> result = new HashMap<>();

                result.put("long", keyElements[0]);
                result.put("short", keyElements[1]);
                result.put("currency", keyElements[2]);
                result.put("value", entry.getValue().toString());

                return result;
            })
            .map(map -> String.format("%s/%s %s: %s",
                map.get("long"),
                map.get("short"),
                map.get("currency"),
                map.get("value")))
            .collect(Collectors.joining("\n"));
    }

    // build a string from a pair of exchanges and currency pair suitable for using as the key in a map
    private static String spreadKey(Exchange longExchange, Exchange shortExchange, CurrencyPair currencyPair) {
        return String.format("%s:%s:%s",
            longExchange.getExchangeSpecification().getExchangeName(),
            shortExchange.getExchangeSpecification().getExchangeName(),
            currencyPair);
    }
}
