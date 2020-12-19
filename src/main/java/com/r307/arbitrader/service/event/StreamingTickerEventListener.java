package com.r307.arbitrader.service.event;

import com.r307.arbitrader.service.TradingService;
import com.r307.arbitrader.service.model.TickerEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

/**
 * Listens for TickerEvents and starts analysis for trading when an event is received.
 */
public class StreamingTickerEventListener {
    private final TradingService tradingService;

    public StreamingTickerEventListener(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    /**
     * Initiate trade analysis when a TickerEvent is received.
     *
     * @param tickerEvent The TickerEvent we received.
     */
    @EventListener
    @Async
    public void onTradeEvent(TickerEvent tickerEvent) {
        tradingService.startTradingProcess(tickerEvent.isStreamingExchange());
    }
}
