package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.service.ErrorCollectorService;
import com.r307.arbitrader.service.ExchangeService;
import com.r307.arbitrader.service.event.StreamingTickerEventPublisher;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component
public class TickerStrategyProvider {

    private final ErrorCollectorService errorCollectorService;
    private final StreamingTickerEventPublisher streamingTickerEventPublisher;
    private final NotificationConfiguration notificationConfiguration;

    @Inject
    public TickerStrategyProvider(ErrorCollectorService errorCollectorService,
                                  StreamingTickerEventPublisher streamingTickerEventPublisher,
                                  NotificationConfiguration notificationConfiguration) {

        this.errorCollectorService = errorCollectorService;
        this.streamingTickerEventPublisher = streamingTickerEventPublisher;
        this.notificationConfiguration = notificationConfiguration;
    }

    public TickerStrategy getStreamingTickerStrategy(ExchangeService exchangeService) {
        return new StreamingTickerStrategy(errorCollectorService, exchangeService, streamingTickerEventPublisher);
    }

    public TickerStrategy getParallelTickerStrategy(ExchangeService exchangeService) {
        return new ParallelTickerStrategy(notificationConfiguration, errorCollectorService, exchangeService);
    }

    public TickerStrategy getSingleCallTickerStrategy(ExchangeService exchangeService) {
        return new SingleCallTickerStrategy(notificationConfiguration, errorCollectorService, exchangeService);
    }
}
