package com.r307.arbitrader.service.event;

import com.r307.arbitrader.service.TradingService;
import com.r307.arbitrader.service.model.TickerEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class StreamingTickerEventListener {
    private final TradingService tradingService;

    public StreamingTickerEventListener(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @EventListener
    @Async
    public void onTradeEvent(TickerEvent tickerEvent) {
        tradingService.startTradingProcess(tickerEvent.isStreamingExchange(), tickerEvent.getExchangeName());
    }
}
