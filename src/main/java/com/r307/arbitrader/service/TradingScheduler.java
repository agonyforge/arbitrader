package com.r307.arbitrader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r307.arbitrader.Utils;
import com.r307.arbitrader.config.TradingConfiguration;
import com.r307.arbitrader.service.model.ActivePosition;
import com.r307.arbitrader.service.paper.PaperExchange;
import com.r307.arbitrader.service.model.Spread;
import com.r307.arbitrader.service.model.TradeCombination;
import com.r307.arbitrader.service.paper.PaperStreamExchange;
import info.bitrich.xchangestream.core.StreamingExchange;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Initiates trading action on a timer.
 */
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

    /**
     * Configure exchanges and connect to them after the rest of the Spring framework is done starting up.
     */
    @PostConstruct
    public void connectExchanges() {
        tradingConfiguration.getExchanges().forEach(exchangeMetadata -> {
            // skip exchanges that are explicitly disabled
            if (exchangeMetadata.getActive() != null && !exchangeMetadata.getActive()) {
                LOGGER.info("Skipping exchange '{}' because it is not set as active", exchangeMetadata.getExchangeClass());
                return;
            }

            Class<? extends Exchange> exchangeClass;

            try {
                // try to load the exchange class
                exchangeClass = Utils.loadExchangeClass(exchangeMetadata.getExchangeClass());
            } catch (ClassNotFoundException e) {
                LOGGER.error("Failed to load exchange {}: {}",
                    exchangeMetadata.getExchangeClass(),
                    e.getMessage());
                return;
            }

            // exchangeMetadata is an ExchangeConfiguration (our class) and has the user's configuration in it
            // we're going to use it to populate an ExchangeSpecification (an XChange class) and configure the Exchange
            // we have our own configuration class so we can have our own parameters and not be locked into only
            // the ones XChange offers.
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

            // Some exchanges (see Quoine in the example configuration) have custom parameters that they need to be
            // configured properly so we offer a "custom" block in our configuration to hold them. This is a little
            // blind so you need to know what you're doing when setting custom parameters, but it's flexible for a
            // lot of different use cases.
            if (!exchangeMetadata.getCustom().isEmpty()) {
                exchangeMetadata.getCustom().forEach((key, value) -> {
                    if ("true".equals(value) || "false".equals(value)) {
                        specification.setExchangeSpecificParametersItem(key, Boolean.valueOf(value));
                    } else {
                        specification.setExchangeSpecificParametersItem(key, value);
                    }
                });
            }

            // Here we store our configuration object into the XChange configuration object so we can reference it later.
            specification.setExchangeSpecificParametersItem(METADATA_KEY, exchangeMetadata);

            // Decide whether to create a streaming exchange or a normal one based on the class name.
            Exchange exchange;
            if(specification.getExchangeClass().getSimpleName().contains("Streaming")) {
                exchange = StreamingExchangeFactory.INSTANCE.createExchange(specification);
            } else {
                exchange = ExchangeFactory.INSTANCE.createExchange(specification);
            }

            // If paper trading is enabled then wrap the current exchange config into a PaperExchange or PaperStreamingExchange
            if(tradingConfiguration.getPaper() != null && tradingConfiguration.getPaper().isActive()) {
                if(specification.getExchangeClass().getSimpleName().contains("Streaming")) {
                    exchange = new PaperStreamExchange((StreamingExchange) exchange, exchangeMetadata.getHomeCurrency(), tickerService, exchangeService,
                        tradingConfiguration.getPaper()
                    );
                } else {
                    exchange = new PaperExchange(exchange, exchangeMetadata.getHomeCurrency(), tickerService, exchangeService, tradingConfiguration.getPaper());
                }
            }
            exchanges.add(exchange);
        });

        // call setUpExchange on every exchange
        exchanges.forEach(exchangeService::setUpExchange);

        // set up all the valid TradeCombinations between all our exchanges so we know what currency pairs we can trade
        tickerService.initializeTickers(exchanges);

        // tell the user whether fixed exposure is configured
        if (tradingConfiguration.getFixedExposure() != null) {
            LOGGER.info("Using fixed exposure of ${} as configured", tradingConfiguration.getFixedExposure());
        }

        // tell the user whether trade timeout is configured
        if (tradingConfiguration.getTradeTimeout() != null) {
            LOGGER.info("Using trade timeout of {} hours", tradingConfiguration.getTradeTimeout());
        }

        if (tradingConfiguration.getPaper() != null && tradingConfiguration.getPaper().isActive()) {
            LOGGER.info("Paper trading enabled, will NOT trade real money");
        }

        // load active trades from file, if there is one
        File stateFile = new File(STATE_FILE);

        // if there is a state file, we need to try to load the in-progress trade from the file
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

        List<TradeCombination> tradeCombinations = tickerService.getExchangeTradeCombinations();

        tradeCombinations.forEach(tradeCombination -> {
            Spread spread = spreadService.computeSpread(tradeCombination);

            if (spread == null) {
                return;
            }

            if (tradingService.getActivePosition() == null) {
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

    /**
     * Periodically update tickers and check for other special tasks such as exiting early or displaying
     * a status report due to a request from the user.
     */
    @Scheduled(initialDelay = 5000, fixedRate = 3000)
    public void pollForPriceData() {
        // log just to let the user know we're still working
        LOGGER.debug("Tick");

        // if the user wants the bot to exit, go ahead and exit
        if (tradingService.getActivePosition() == null && conditionService.isExitWhenIdleCondition()) {
            LOGGER.info("Exiting at user request");
            conditionService.clearExitWhenIdleCondition();
            System.exit(0);
        }

        // if the user requested a status, print the status into the logs and clear the condition
        if (conditionService.isStatusCondition()) {
            logStatus();
            conditionService.clearStatusCondition();
        }

        long exchangePollStartTime = System.currentTimeMillis();

        // fetch tickers for all exchanges and currencies
        tickerService.refreshTickers();

        long exchangePollDuration = System.currentTimeMillis() - exchangePollStartTime;

        // measure the time we took to analyze prices
        if (exchangePollDuration > 3000) {
            LOGGER.warn("Refreshing tickers took {} ms", exchangePollDuration);
        }
    }

    // print a summary of all trade combinations, prices, and spreads
    private void logStatus() {
        LOGGER.info("=== Current Status ===");

        tickerService.getExchangeTradeCombinations()
            .stream()
            .sorted(Comparator.comparing(o ->
                o.getLongExchange().getExchangeSpecification().getExchangeName()
                    + o.getShortExchange().getExchangeSpecification().getExchangeName()
                    + o.getCurrencyPair()))
            .forEach(tradeCombination -> {
                Spread spread = spreadService.computeSpread(tradeCombination);

                if (spread != null) {
                    LOGGER.info("{}/{} {}",
                        spread.getLongExchange().getExchangeSpecification().getExchangeName(),
                        spread.getShortExchange().getExchangeSpecification().getExchangeName(),
                        spread.getCurrencyPair());
                    LOGGER.info("\tLong/Short Bid/Asks:{}/{} {}/{}",
                        spread.getLongTicker().getBid(),
                        spread.getLongTicker().getAsk(),
                        spread.getShortTicker().getBid(),
                        spread.getShortTicker().getAsk());
                    LOGGER.info("\tSpread In/Out:{}/{}",
                        spread.getIn(),
                        spread.getOut());
                }
            });
    }
}
