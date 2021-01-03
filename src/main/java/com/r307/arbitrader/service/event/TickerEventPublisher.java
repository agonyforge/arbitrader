package com.r307.arbitrader.service.event;

import com.r307.arbitrader.service.model.TickerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes ticker events.
 */
@Component
public class TickerEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(TickerEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public TickerEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Publish a TickerEvent.
     *
     * @param tickerEvent the TickerEvent to publish.
     */
    public void publishTicker(TickerEvent tickerEvent) {
        LOGGER.trace("Publishing ticker event: {} {} {}/{}",
            tickerEvent.getExchange().getExchangeSpecification().getExchangeName(),
            tickerEvent.getTicker().getInstrument(),
            tickerEvent.getTicker().getBid(),
            tickerEvent.getTicker().getAsk());

        applicationEventPublisher.publishEvent(tickerEvent);
    }
}
