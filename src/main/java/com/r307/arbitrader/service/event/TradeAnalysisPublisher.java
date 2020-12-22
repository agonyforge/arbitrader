package com.r307.arbitrader.service.event;

import com.r307.arbitrader.service.model.event.TradeAnalysisEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class TradeAnalysisPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    public TradeAnalysisPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishTradeAnalysis(TradeAnalysisEvent tradeAnalysisEvent) {
        applicationEventPublisher.publishEvent(tradeAnalysisEvent);
    }
}
