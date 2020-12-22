package com.r307.arbitrader.service.event;

import com.r307.arbitrader.service.TickerService;
import com.r307.arbitrader.service.model.event.TickerEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class TickerEventListener {
    private final TickerService tickerService;

    public TickerEventListener(TickerService tickerService) {
        this.tickerService = tickerService;
    }

    @EventListener
    @Async("tickerEventTaskExecutor")
    public void onTickerEvent(TickerEvent tickerEvent) {
        tickerService.updateTicker(tickerEvent.getExchange(), tickerEvent.getTicker());
    }
}
