package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.service.ErrorCollectorService;
import com.r307.arbitrader.service.ExchangeService;
import com.r307.arbitrader.service.event.TickerEventPublisher;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component
public class TickerStrategyProvider {

    private final ErrorCollectorService errorCollectorService;
    private final TickerEventPublisher tickerEventPublisher;
    private final NotificationConfiguration notificationConfiguration;

    @Inject
    public TickerStrategyProvider(ErrorCollectorService errorCollectorService,
                                  TickerEventPublisher tickerEventPublisher,
                                  NotificationConfiguration notificationConfiguration) {

        this.errorCollectorService = errorCollectorService;
        this.tickerEventPublisher = tickerEventPublisher;
        this.notificationConfiguration = notificationConfiguration;
    }

    public TickerStrategy getStreamingTickerStrategy(ExchangeService exchangeService) {
        return new StreamingTickerStrategy(errorCollectorService, exchangeService, tickerEventPublisher);
    }

    public TickerStrategy getParallelTickerStrategy(ExchangeService exchangeService) {
        return new ParallelTickerStrategy(notificationConfiguration, errorCollectorService, exchangeService, tickerEventPublisher);
    }

    public TickerStrategy getSingleCallTickerStrategy(ExchangeService exchangeService) {
        return new SingleCallTickerStrategy(notificationConfiguration, errorCollectorService, exchangeService, tickerEventPublisher);
    }
}
