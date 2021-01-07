package com.r307.arbitrader.service.event;

import com.r307.arbitrader.service.SpreadService;
import com.r307.arbitrader.service.TickerService;
import com.r307.arbitrader.service.TradingService;
import com.r307.arbitrader.service.model.Spread;
import com.r307.arbitrader.service.model.TickerEvent;
import com.r307.arbitrader.service.model.TradeCombination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Listens for TickerEvents and starts analysis for trading when an event is received.
 */
@Component
public class TickerEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(TickerEventListener.class);

    private final TradingService tradingService;
    private final TickerService tickerService;
    private final SpreadService spreadService;

    public TickerEventListener(
        TradingService tradingService,
        TickerService tickerService,
        SpreadService spreadService) {

        this.tradingService = tradingService;
        this.tickerService = tickerService;
        this.spreadService = spreadService;
    }

    /**
     * Initiate trade analysis when a TickerEvent is received, but only for trade combinations that involve
     * the exchange and currency pair that was updated. This code runs every time a ticker is received so it's
     * important to make it as fast and as lightweight as possible.
     *
     * @param tickerEvent The TickerEvent we received.
     */
    @EventListener
    @Async
    public void onTradeEvent(TickerEvent tickerEvent) {
        LOGGER.trace("Received ticker event: {} {} {}/{}",
            tickerEvent.getExchange().getExchangeSpecification().getExchangeName(),
            tickerEvent.getTicker().getInstrument(),
            tickerEvent.getTicker().getBid(),
            tickerEvent.getTicker().getAsk());

        List<TradeCombination> tradeCombinations = tickerService.getExchangeTradeCombinations();

        tradeCombinations
            .stream()
            // only consider combinations where one of the exchanges is from the event
            .filter(tradeCombination -> (
                tradeCombination.getLongExchange().equals(tickerEvent.getExchange())
                    || tradeCombination.getShortExchange().equals(tickerEvent.getExchange())
            ))
            // only consider combinations where the currency pair matches the event
            .filter(tradeCombination -> tradeCombination.getCurrencyPair().equals(tickerEvent.getTicker().getInstrument()))
            .forEach(tradeCombination -> {
                Spread spread = spreadService.computeSpread(tradeCombination);

                if (spread != null) { // spread will be null if any tickers were missing for this combination
                    final long start = System.currentTimeMillis();
                    tradingService.trade(spread);

                    LOGGER.debug("Analyzed {} ({} ms)", spread, System.currentTimeMillis() - start);
                }
            });
    }
}
