package com.r307.arbitrader.service;

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

@Component
public class SpreadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpreadService.class);

    private final Map<String, BigDecimal> minSpreadIn = new HashMap<>();
    private final Map<String, BigDecimal> maxSpreadIn = new HashMap<>();
    private final Map<String, BigDecimal> minSpreadOut = new HashMap<>();
    private final Map<String, BigDecimal> maxSpreadOut = new HashMap<>();
    private final TickerService tickerService;

    public SpreadService(TickerService tickerService) {
        this.tickerService = tickerService;
    }

    public void publish(Spread spread) {
        String spreadKey = spreadKey(spread.getLongExchange(), spread.getShortExchange(), spread.getCurrencyPair());

        minSpreadIn.put(spreadKey, spread.getIn().min(minSpreadIn.getOrDefault(spreadKey, BigDecimal.valueOf(1))));
        maxSpreadIn.put(spreadKey, spread.getIn().max(maxSpreadIn.getOrDefault(spreadKey, BigDecimal.valueOf(-1))));
        minSpreadOut.put(spreadKey, spread.getOut().min(minSpreadOut.getOrDefault(spreadKey, BigDecimal.valueOf(1))));
        maxSpreadOut.put(spreadKey, spread.getOut().max(maxSpreadOut.getOrDefault(spreadKey, BigDecimal.valueOf(-1))));
    }

    @Scheduled(cron = "0 0 0 * * *") // midnight every day
    public void summary() {
        LOGGER.info("Minimum spreadIns:\n{}", buildSummary(minSpreadIn));
        LOGGER.info("Maximum spreadIns:\n{}", buildSummary(maxSpreadIn));
        LOGGER.info("Minimum spreadOuts:\n{}", buildSummary(minSpreadOut));
        LOGGER.info("Maximum spreadOuts:\n{}", buildSummary(maxSpreadOut));
    }

    public Spread computeSpread(TradeCombination tradeCombination) {
        Exchange longExchange = tradeCombination.getLongExchange();
        Exchange shortExchange = tradeCombination.getShortExchange();
        CurrencyPair currencyPair = tradeCombination.getCurrencyPair();

        Ticker longTicker = tickerService.getTicker(longExchange, currencyPair);
        Ticker shortTicker = tickerService.getTicker(shortExchange, currencyPair);

        if (tickerService.isInvalidTicker(longTicker) || tickerService.isInvalidTicker(shortTicker)) {
            return null;
        }

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

        publish(spread);

        return spread;
    }

    public BigDecimal computeSpread(BigDecimal longPrice, BigDecimal shortPrice) {
        BigDecimal scaledLongPrice = longPrice.setScale(BTC_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal scaledShortPrice = shortPrice.setScale(BTC_SCALE, RoundingMode.HALF_EVEN);

        return (scaledShortPrice.subtract(scaledLongPrice)).divide(scaledLongPrice, RoundingMode.HALF_EVEN);
    }

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

    private static String spreadKey(Exchange longExchange, Exchange shortExchange, CurrencyPair currencyPair) {
        return String.format("%s:%s:%s",
            longExchange.getExchangeSpecification().getExchangeName(),
            shortExchange.getExchangeSpecification().getExchangeName(),
            currencyPair);
    }
}
