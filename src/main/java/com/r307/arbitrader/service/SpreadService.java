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
     * We show a green check or a red bar icon in the log to indicate whether the pair has reached a "profitable range".
     * The check means that the maximum spreadIn is higher than the minimum spreadOut. In other words if you had entered
     * the market at the maximum spreadIn and exited at the minimum spreadOut, you would have made money. If this
     * condition is not true it is not worthwhile to enter a trade in that market yet based on the prices we have seen
     * so far.
     *
     * @param spread A new Spread.
     */
    void publish(Spread spread) {
        String spreadKey = spreadKey(spread.getLongExchange(), spread.getShortExchange(), spread.getCurrencyPair());

        if (LOGGER.isInfoEnabled() && tradingConfiguration.isSpreadNotifications()) {
            BigDecimal maxIn = maxSpreadIn.getOrDefault(spreadKey, BigDecimal.valueOf(-1));
            BigDecimal maxOut = minSpreadOut.getOrDefault(spreadKey, BigDecimal.valueOf(1));
            boolean crossed = maxIn.compareTo(maxOut) > 0;

            if (spread.getIn().compareTo(maxSpreadIn.getOrDefault(spreadKey, BigDecimal.valueOf(-1))) > 0) {
                LOGGER.info("{} Record high spreadIn: {}/{} {} {}",
                    crossed ? "✅️" : "⛔",
                    spread.getLongExchange().getExchangeSpecification().getExchangeName(),
                    spread.getShortExchange().getExchangeSpecification().getExchangeName(),
                    spread.getCurrencyPair(),
                    spread.getIn());
            }

            if (spread.getOut().compareTo(minSpreadOut.getOrDefault(spreadKey, BigDecimal.valueOf(1))) < 0) {
                LOGGER.info("{} Record low spreadOut: {}/{} {} {}",
                    crossed ? "✅️" : "⛔",
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

    /**
     * Get the real entry spread target from the effective entry spread target (the real entry spread target is larger
     * as it needs to compensate for the entry fees)
     * @param tradingConfiguration the trading configuration
     * @param longFee the long exchange fees in percentage
     * @param shortFee the short exchange fees in percentage
     * @return the real entry spread target
     */
    public BigDecimal getEntrySpreadTarget(TradingConfiguration tradingConfiguration, BigDecimal longFee, BigDecimal shortFee) {
        return (BigDecimal.ONE.add(tradingConfiguration.getEntrySpreadTarget())).multiply(BigDecimal.ONE.add(longFee)).divide(BigDecimal.ONE.subtract(shortFee), BTC_SCALE, RoundingMode.HALF_EVEN).subtract(BigDecimal.ONE);
    }

    /**
     * Get the real exit spread target from the trading configuration. The real exit spread target can be computed from
     * the configured exitSpreadTarget or from the configured minimum profit. Both configuration should not be present
     * at the same time.
     * @param tradingConfiguration the trading configuration
     * @param entrySpread the real entry spread
     * @param longFee the long exchange fees in percentage
     * @param shortFee the short exchange fees in percentage
     * @return the real exit spread target
     */
    public BigDecimal getExitSpreadTarget(TradingConfiguration tradingConfiguration, BigDecimal entrySpread, BigDecimal longFee, BigDecimal shortFee) {
        return computeExitSpreadTarget(computeEffectiveExitSpreadTarget(tradingConfiguration, entrySpread, longFee, shortFee), longFee, shortFee);
    }

    /*
    * Calculate the real exit spread target from an effective exit spread target (the real exit spread target is lower
    * as is needs to compensate for the exit fees)
    */
    private BigDecimal computeExitSpreadTarget(BigDecimal effectiveExitSpreadTarget, BigDecimal longFee, BigDecimal shortFee) {
        return (BigDecimal.ONE.add(effectiveExitSpreadTarget)).multiply(BigDecimal.ONE.subtract(longFee)).divide(BigDecimal.ONE.add(shortFee), BTC_SCALE, RoundingMode.HALF_EVEN).subtract(BigDecimal.ONE);
    }

    /*
    * Calculate the effective exit spread target either from the configured value or from the minimum profit percentage
     */
    private BigDecimal computeEffectiveExitSpreadTarget(TradingConfiguration tradingConfiguration, BigDecimal entrySpread, BigDecimal longFee, BigDecimal shortFee) {
        if(tradingConfiguration.getExitSpreadTarget() != null) {
            return tradingConfiguration.getEntrySpreadTarget();
        } else {
            BigDecimal profit = tradingConfiguration.getMinimumProfit() != null ? tradingConfiguration.getMinimumProfit() : BigDecimal.ZERO;
            BigDecimal effectiveEntrySpread = (BigDecimal.ONE.add(entrySpread)).multiply(BigDecimal.ONE.subtract(shortFee)).divide(BigDecimal.ONE.add(longFee), BTC_SCALE, RoundingMode.HALF_EVEN).subtract(BigDecimal.ONE);
            return effectiveEntrySpread.subtract(profit);
        }
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
