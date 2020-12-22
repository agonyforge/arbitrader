package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.service.ErrorCollectorService;
import com.r307.arbitrader.service.ExchangeService;
import com.r307.arbitrader.service.event.TickerEventPublisher;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

/**
 * A service for getting TickerStrategy implementations. We need to do it this way because there is a
 * circular dependency between ExchangeService and one or more of the TickerStrategy implementations.
 */
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

    /**
     * Return a TickerStrategy for streaming exchanges.
     *
     * @param exchangeService An instance of ExchangeService.
     * @return A StreamingTickerStrategy.
     */
    public TickerStrategy getStreamingTickerStrategy(ExchangeService exchangeService) {
        return new StreamingTickerStrategy(errorCollectorService, exchangeService, tickerEventPublisher);
    }

    /**
     * Return a TickerStrategy that makes individual calls all in parallel.
     *
     * @param exchangeService An instance of ExchangeService.
     * @return A ParallelTickerStrategy.
     */
    public TickerStrategy getParallelTickerStrategy(ExchangeService exchangeService) {
        return new ParallelTickerStrategy(notificationConfiguration, errorCollectorService, exchangeService, tickerEventPublisher);
    }

    /**
     * Return a TickerStrategy that makes a single call to the exchange for all the Tickers.
     *
     * @param exchangeService An instance of ExchangeService.
     * @return A SingleCallTickerStrategy.
     */
    public TickerStrategy getSingleCallTickerStrategy(ExchangeService exchangeService) {
        return new SingleCallTickerStrategy(notificationConfiguration, errorCollectorService, exchangeService, tickerEventPublisher);
    }
}
