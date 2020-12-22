package com.r307.arbitrader.service.event;

import com.r307.arbitrader.service.model.event.TradeAnalysisEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class TradeAnalysisListener {

    @EventListener
    @Async("tradeAnalysisEventTaskExecutor")
    public void onTradeAnalysisEvent(TradeAnalysisEvent tradeAnalysisEvent) {
        // TODO: Implement
    }
}
