package com.r307.arbitrader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r307.arbitrader.Utils;
import com.r307.arbitrader.config.TradingConfiguration;
import com.r307.arbitrader.service.model.ActivePosition;
import com.r307.arbitrader.service.model.Spread;
import com.r307.arbitrader.service.model.TradeCombination;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class TradingScheduler {
    public static final String METADATA_KEY = "arbitrader-metadata";
    public static final String TICKER_STRATEGY_KEY = "tickerStrategy";

    private static final Logger LOGGER = LoggerFactory.getLogger(TradingScheduler.class);
    private static final String STATE_FILE = ".arbitrader/arbitrader-state.json";
    protected static final String TRADE_HISTORY_FILE = ".arbitrader/arbitrader-arbitrage-history.csv";

    private final ObjectMapper objectMapper;
    private final TradingConfiguration tradingConfiguration;
    private final ConditionService conditionService;
    private final ExchangeService exchangeService;
    private final ErrorCollectorService errorCollectorService;
    private final SpreadService spreadService;
    private final TickerService tickerService;
    private final List<Exchange> exchanges = new ArrayList<>();
    private final TradingService tradingService;

    public TradingScheduler(
        ObjectMapper objectMapper,
        TradingConfiguration tradingConfiguration,
        ConditionService conditionService,
        ExchangeService exchangeService,
        TradingService tradingService,
        ErrorCollectorService errorCollectorService,
        SpreadService spreadService,
        TickerService tickerService) {

        this.objectMapper = objectMapper;
        this.tradingConfiguration = tradingConfiguration;
        this.conditionService = conditionService;
        this.exchangeService = exchangeService;
        this.errorCollectorService = errorCollectorService;
        this.spreadService = spreadService;
        this.tickerService = tickerService;
        this.tradingService = tradingService;
    }

    @PostConstruct
    public void connectExchanges() {
        tradingConfiguration.getExchanges().forEach(exchangeMetadata -> {
            Class<Exchange> exchangeClass;

            try {
                exchangeClass = Utils.loadExchangeClass(exchangeMetadata.getExchangeClass());
            } catch (ClassNotFoundException e) {
                LOGGER.error("Failed to load exchange {}: {}",
                    exchangeMetadata.getExchangeClass(),
                    e.getMessage());
                return;
            }

            ExchangeSpecification specification = new ExchangeSpecification(exchangeClass);

            specification.setUserName(exchangeMetadata.getUserName());
            specification.setApiKey(exchangeMetadata.getApiKey());
            specification.setSecretKey(exchangeMetadata.getSecretKey());

            if (exchangeMetadata.getSslUri() != null) {
                specification.setSslUri(exchangeMetadata.getSslUri());
            }

            if (exchangeMetadata.getHost() != null) {
                specification.setHost(exchangeMetadata.getHost());
            }

            if (exchangeMetadata.getPort() != null) {
                specification.setPort(exchangeMetadata.getPort());
            }

            if (!exchangeMetadata.getCustom().isEmpty()) {
                exchangeMetadata.getCustom().forEach((key, value) -> {
                    if ("true".equals(value) || "false".equals(value)) {
                        specification.setExchangeSpecificParametersItem(key, Boolean.valueOf(value));
                    } else {
                        specification.setExchangeSpecificParametersItem(key, value);
                    }
                });
            }

            specification.setExchangeSpecificParametersItem(METADATA_KEY, exchangeMetadata);

            if (specification.getExchangeClass().getSimpleName().contains("Streaming")) {
                exchanges.add(StreamingExchangeFactory.INSTANCE.createExchange(specification));
            } else {
                exchanges.add(ExchangeFactory.INSTANCE.createExchange(specification));
            }
        });

        exchanges.forEach(exchangeService::setUpExchange);

        tickerService.initializeTickers(exchanges);

        if (tradingConfiguration.getFixedExposure() != null) {
            LOGGER.info("Using fixed exposure of ${} as configured", tradingConfiguration.getFixedExposure());
        }

        if (tradingConfiguration.getTradeTimeout() != null) {
            LOGGER.info("Using trade timeout of {} hours", tradingConfiguration.getTradeTimeout());
        }

        // load active trades from file, if there is one
        File stateFile = new File(STATE_FILE);

        if (stateFile.exists()) {
            if (!stateFile.canRead()) {
                LOGGER.error("Cannot read state file: {}", stateFile.getAbsolutePath());
            } else {
                try {
                    ActivePosition activePosition = objectMapper.readValue(stateFile, ActivePosition.class);

                    tradingService.setActivePosition(activePosition);

                    LOGGER.info("Loaded active trades from file: {}", stateFile.getAbsolutePath());
                    LOGGER.info("Active trades: {}", activePosition);
                } catch (IOException e) {
                    LOGGER.error("Unable to parse state file {}: ", stateFile.getAbsolutePath(), e);
                }
            }
        }
    }



    /**
     * As often as once per minute, display a summary of any non-critical error messages. Summarizing them greatly
     * reduces how noisy the logs are while still providing the same information.
     */
    @Scheduled(cron = "0 * * * * *")
    public void errorSummary() {
        if (!errorCollectorService.isEmpty()) {
            errorCollectorService.report().forEach(LOGGER::info);
            errorCollectorService.clear();
        }
    }

    /**
     * Display a summary once every 6 hours with the current spreads.
     */
    @Scheduled(cron = "0 0 0/6 * * *") // every 6 hours
    public void summary() {
        LOGGER.info("Summary: [Long/Short Exchanges] [Pair] [Current Spread] -> [{} Spread Target]", (tradingService.getActivePosition() != null ? "Exit" : "Entry"));

        List<TradeCombination> tradeCombinations = tickerService.getPollingExchangeTradeCombinations();

        tradeCombinations.forEach(tradeCombination -> {
            Spread spread = spreadService.computeSpread(tradeCombination);

            if (spread == null) {
                return;
            }

            if (tradingService.getActivePosition() == null && BigDecimal.ZERO.compareTo(spread.getIn()) < 0) {
                LOGGER.info("{}/{} {} {} -> {}",
                    spread.getLongExchange().getExchangeSpecification().getExchangeName(),
                    spread.getShortExchange().getExchangeSpecification().getExchangeName(),
                    spread.getCurrencyPair(),
                    spread.getIn(),
                    tradingConfiguration.getEntrySpread());
            } else if (tradingService.getActivePosition() != null
                && tradingService.getActivePosition().getCurrencyPair().equals(spread.getCurrencyPair())
                && tradingService.getActivePosition().getLongTrade().getExchange().equals(spread.getLongExchange().getExchangeSpecification().getExchangeName())
                && tradingService.getActivePosition().getShortTrade().getExchange().equals(spread.getShortExchange().getExchangeSpecification().getExchangeName())) {

                LOGGER.info("{}/{} {} {} -> {}",
                    spread.getLongExchange().getExchangeSpecification().getExchangeName(),
                    spread.getShortExchange().getExchangeSpecification().getExchangeName(),
                    spread.getCurrencyPair(),
                    spread.getOut(),
                    tradingService.getActivePosition().getExitTarget());
            }
        });
    }

    @Scheduled(initialDelay = 5000, fixedRate = 3000)
    public void pollForPriceData() {
        LOGGER.debug("Tick");

        if (tradingService.getActivePosition() == null && conditionService.isExitWhenIdleCondition()) {
            LOGGER.info("Exiting at user request");
            conditionService.clearExitWhenIdleCondition();
            System.exit(0);
        }

        tickerService.refreshTickers();

        long exchangePollStartTime = System.currentTimeMillis();

        startTradingProcess();

        long exchangePollDuration = System.currentTimeMillis() - exchangePollStartTime;

        if (exchangePollDuration > 3000) {
            LOGGER.warn("Polling exchanges took {} ms", exchangePollDuration);
        }
    }

    public void startTradingProcess() {
        tickerService.getPollingExchangeTradeCombinations()
            .forEach(tradeCombination -> {
                Spread spread = spreadService.computeSpread(tradeCombination);

                if (spread != null) {
                    tradingService.trade(spread);
                }
        });
    }
}
