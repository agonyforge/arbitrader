package com.r307.arbitrader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r307.arbitrader.DecimalConstants;
import com.r307.arbitrader.config.FeeComputation;
import com.r307.arbitrader.config.TradingConfiguration;
import com.r307.arbitrader.exception.OrderNotFoundException;
import com.r307.arbitrader.service.model.ActivePosition;
import com.r307.arbitrader.service.model.ArbitrageLog;
import com.r307.arbitrader.service.model.Spread;
import com.r307.arbitrader.service.model.TradeCombination;
import org.apache.commons.io.FileUtils;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;

/**
 * Trade analysis and execution.
 */
@Component
public class TradingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TradingService.class);
    private static final String STATE_FILE = ".arbitrader/arbitrader-state.json";
    private static final String TRADE_HISTORY_FILE = ".arbitrader/arbitrader-arbitrage-history.csv";
    private static final BigDecimal TRADE_PORTION = new BigDecimal("0.9");
    private static final BigDecimal TRADE_REMAINDER = BigDecimal.ONE.subtract(TRADE_PORTION);
    private static final CurrencyPairMetaData NULL_CURRENCY_PAIR_METADATA = new CurrencyPairMetaData(
        null, null, null, null, null);

    private final ObjectMapper objectMapper;
    private final TradingConfiguration tradingConfiguration;
    private final ConditionService conditionService;
    private final ExchangeService exchangeService;
    private final SpreadService spreadService;
    private final TickerService tickerService;
    private final NotificationService notificationService;
    private boolean timeoutExitWarning = false;
    private ActivePosition activePosition = null;
    private boolean bailOut = false;

    public TradingService(
        ObjectMapper objectMapper,
        TradingConfiguration tradingConfiguration,
        ConditionService conditionService,
        ExchangeService exchangeService,
        SpreadService spreadService,
        TickerService tickerService,
        @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") NotificationService notificationService) {

        this.objectMapper = objectMapper;
        this.tradingConfiguration = tradingConfiguration;
        this.conditionService = conditionService;
        this.exchangeService = exchangeService;
        this.spreadService = spreadService;
        this.tickerService = tickerService;
        this.notificationService = notificationService;
    }

    /**
     * Start to analyze prices and look for trades.
     *
     * @param isStreaming true if we should look at streaming exchanges
     */
    public void startTradingProcess(Exchange exchange) {
        final Set<TradeCombination> tradeCombinations = tickerService.getExchangeTradeCombinations();

        // tradeCombinations may be null if there is only one active streaming exchange
        if (tradeCombinations == null) {
            LOGGER.debug("exchange:{}|tradeCombinations is null because there is only one active streaming exchange",
                exchange.getExchangeSpecification().getExchangeName());
            return;
        }

        tradeCombinations.forEach(tradeCombination -> {
            Spread spread = spreadService.computeSpread(tradeCombination);

            if (spread != null) {
                trade(spread);
            }
        });
    }

    /**
     * Attempt to trade (both entry and exit).
     *
     * @param spread The Spread contains the exchanges and prices for the trade.
     */
    public void trade(Spread spread) {
        if (bailOut) {
            LOGGER.error("Exiting immediately to avoid erroneous trades.");
            System.exit(1);
        }

        final String shortExchangeName = spread.getShortExchange().getExchangeSpecification().getExchangeName();
        final String longExchangeName = spread.getLongExchange().getExchangeSpecification().getExchangeName();

        LOGGER.debug("Long/Short: {}/{} {} {}",
            longExchangeName,
            shortExchangeName,
            spread.getCurrencyPair(),
            spread.getIn());

        // consider whether to enter a new position:
        //   are we being forced to open a position?
        //   is the "in" spread higher than our entry spread?
        if (!conditionService.isForceCloseCondition() && (conditionService.isForceOpenCondition() || spread.getIn().compareTo(tradingConfiguration.getEntrySpread()) > 0)) {
            if (activePosition != null) {
                return;
            }

            // if we're being forced, do the current exchanges and currency pair match the requested ones?
            if (conditionService.isForceOpenCondition()) {
                String exchanges = conditionService.readForceOpenContent().trim();

                /*
                The force-open file should contain the names of the exchanges you want to force a trade on.
                It's meant to be a tool to aid testing entry and exit on specific pairs of exchanges.

                In this format: currencyPair longExchange/shortExchange
                For example: BTC/USD BitFlyer/Kraken
                */
                String current = String.format("%s %s/%s",
                    spread.getCurrencyPair().toString(),
                    longExchangeName,
                    shortExchangeName);

                if (!(current).equals(exchanges)) {
                    return;
                }
            }

            entryPosition(spread, shortExchangeName, longExchangeName);

        // consider whether to exit an open position
        //   does the ActivePosition match the current exchanges and currency pair?
        //   is the "out" spread less than the exit target?
        //   are we being forced to exit?
        //   has the configured trade time expired?
        } else if (activePosition != null
            && spread.getCurrencyPair().equals(activePosition.getCurrencyPair())
            && longExchangeName.equals(activePosition.getLongTrade().getExchange())
            && shortExchangeName.equals(activePosition.getShortTrade().getExchange())
            && (spread.getOut().compareTo(activePosition.getExitTarget()) < 0 || conditionService.isForceCloseCondition() || isTradeExpired())) {

            exitPosition(spread, shortExchangeName, longExchangeName);
        }
    }

    public ActivePosition getActivePosition() {
        return activePosition;
    }

    public void setActivePosition(ActivePosition activePosition) {
        this.activePosition = activePosition;
    }

    // enter a position
    private void entryPosition(Spread spread, String shortExchangeName, String longExchangeName) {
        final BigDecimal longFeePercent = exchangeService.getExchangeFee(spread.getLongExchange(), spread.getCurrencyPair(), true);
        final BigDecimal shortFeePercent = exchangeService.getExchangeFee(spread.getShortExchange(), spread.getCurrencyPair(), true);
        final CurrencyPair currencyPairLongExchange = exchangeService.convertExchangePair(spread.getLongExchange(), spread.getCurrencyPair());
        final CurrencyPair currencyPairShortExchange = exchangeService.convertExchangePair(spread.getShortExchange(), spread.getCurrencyPair());
        final BigDecimal exitTarget = spread.getIn().subtract(tradingConfiguration.getExitTarget());
        final BigDecimal maxExposure = getMaximumExposure(spread.getLongExchange(), spread.getShortExchange());

        // bail out if we're in a blackout period and we're not being forced
        if (!conditionService.isForceOpenCondition() &&
            (conditionService.isBlackoutCondition(spread.getLongExchange()) || conditionService.isBlackoutCondition(spread.getShortExchange()))) {

            LOGGER.warn("Cannot open position on one or more exchanges due to user configured blackout");
            return;
        }

        // check whether we have enough money to trade (forcing it can't work if we can't afford it)
        if (!validateMaxExposure(maxExposure, spread, currencyPairLongExchange, currencyPairShortExchange)) {
            return;
        }

        final int longScale = BTC_SCALE;
        final int shortScale = BTC_SCALE;

        LOGGER.debug("Max exposure: {}", maxExposure);
        LOGGER.debug("Long scale: {}", longScale);
        LOGGER.debug("Short scale: {}", shortScale);
        LOGGER.debug("Long ticker ASK: {}", spread.getLongTicker().getAsk());
        LOGGER.debug("Short ticker BID: {}", spread.getShortTicker().getBid());
        LOGGER.debug("Long fee percent: {}", longFeePercent);
        LOGGER.debug("Short fee percent: {}", shortFeePercent);

        // figure out how much we want to trade
        BigDecimal longVolume = getVolumeForEntryPosition(
            spread.getLongExchange(),
            maxExposure,
            spread.getLongTicker().getAsk(),
            spread.getCurrencyPair(),
            longScale);
        BigDecimal shortVolume = getVolumeForEntryPosition(
            spread.getShortExchange(),
            maxExposure,
            spread.getShortTicker().getBid(),
            spread.getCurrencyPair(),
            shortScale);

        BigDecimal longLimitPrice;
        BigDecimal shortLimitPrice;

        // We originally calculated a spread based on the *entire order* being executed at the bid or ask price.
        //
        // In a real trade there may not be enough currency available at that price to fulfill the entire order
        // so we calculate it again using the order book in getLimitPrice() here. If part of the order will be
        // executed at a worse price, that is called "slip". It is possible that we can still meet the entry spread
        // even with slip, but if we can't we should bail out now and wait for the spread to improve.
        //
        // This recalculation of the spread is a little computationally expensive, which is why we don't do it
        // until we know we're close to wanting to trade.
        try {
            longLimitPrice = getLimitPrice(spread.getLongExchange(), spread.getCurrencyPair(), longVolume, Order.OrderType.ASK);
            shortLimitPrice = getLimitPrice(spread.getShortExchange(), spread.getCurrencyPair(), shortVolume, Order.OrderType.BID);
        } catch (ExchangeException e) {
            LOGGER.warn("Failed to fetch order books for {}/{} and currency {}/{} to compute entry prices: {}",
                longExchangeName,
                spread.getShortExchange().getDefaultExchangeSpecification().getExchangeName(),
                spread.getCurrencyPair().base,
                spread.getCurrencyPair().counter,
                e.getMessage());
            return;
        }

        BigDecimal spreadVerification = spreadService.computeSpread(longLimitPrice, shortLimitPrice);

        if (!conditionService.isForceOpenCondition() && spreadVerification.compareTo(tradingConfiguration.getEntrySpread()) < 0) {
            LOGGER.debug("Spread verification is less than entry spread, will not trade"); // this is debug because it can get spammy
            return;
        }

        // we need to add fees for exchanges where feeComputation is set to CLIENT
        BigDecimal longVolumeWithFees = addFees(spread.getLongExchange(), spread.getCurrencyPair(), longVolume);
        BigDecimal shortVolumeWithFees = addFees(spread.getShortExchange(), spread.getCurrencyPair(), shortVolume);

        logEntryTrade(spread, shortExchangeName, longExchangeName, exitTarget, longVolume, shortVolume, longLimitPrice, shortLimitPrice);

        BigDecimal totalBalance = logCurrentExchangeBalances(spread.getLongExchange(), spread.getShortExchange());

        try {
            activePosition = new ActivePosition();
            activePosition.setEntryTime(OffsetDateTime.now());
            activePosition.setCurrencyPair(spread.getCurrencyPair());
            activePosition.setExitTarget(exitTarget);
            activePosition.setEntryBalance(totalBalance);
            activePosition.getLongTrade().setExchange(spread.getLongExchange());
            activePosition.getLongTrade().setVolume(longVolume);
            activePosition.getLongTrade().setEntry(longLimitPrice);
            activePosition.getShortTrade().setExchange(spread.getShortExchange());
            activePosition.getShortTrade().setVolume(shortVolume);
            activePosition.getShortTrade().setEntry(shortLimitPrice);

            executeOrderPair(
                spread.getLongExchange(), spread.getShortExchange(),
                spread.getCurrencyPair(),
                longLimitPrice, shortLimitPrice,
                longVolumeWithFees, shortVolumeWithFees,
                true);

            notificationService.sendEmailNotificationBodyForEntryTrade(spread, exitTarget, longVolume,
                longLimitPrice, shortVolume, shortLimitPrice);
        } catch (IOException e) {
            LOGGER.error("IOE executing limit orders: ", e);
            activePosition = null;
        }

        try {
            FileUtils.write(new File(STATE_FILE), objectMapper.writeValueAsString(activePosition), Charset.defaultCharset());
        } catch (IOException e) {
            LOGGER.error("Unable to write state file!", e);
        }

        conditionService.clearForceOpenCondition();
    }

    // ensure that we have enough money to trade
    private boolean validateMaxExposure(BigDecimal maxExposure, Spread spread, CurrencyPair longCurrencyPair, CurrencyPair shortCurrencyPair) {
        final BigDecimal longMinAmount = getMinimumAmountForEntryPosition(spread, spread.getLongExchange());
        final BigDecimal shortMinAmount = getMinimumAmountForEntryPosition(spread, spread.getShortExchange());

        final String longExchangeName = spread.getLongExchange().getExchangeSpecification().getExchangeName();
        final String shortExchangeName = spread.getShortExchange().getExchangeSpecification().getExchangeName();

        if (maxExposure.compareTo(longMinAmount) <= 0) {
            LOGGER.error("{} must have at least ${} to trade {} but only has ${}",
                longExchangeName,
                longMinAmount.add(longMinAmount.multiply(TRADE_REMAINDER)),
                longCurrencyPair,
                maxExposure);
            return false;
        }

        if (maxExposure.compareTo(shortMinAmount) <= 0) {
            LOGGER.error("{} must have at least ${} to trade {} but only has ${}",
                shortExchangeName,
                shortMinAmount.add(shortMinAmount.multiply(TRADE_REMAINDER)),
                shortCurrencyPair,
                maxExposure);
            return false;
        }

        return true;
    }

    // exit a position
    private void exitPosition(Spread spread, String shortExchangeName, String longExchangeName) {
        // figure out how much to trade
        BigDecimal longVolume = getVolumeForOrder(
            spread.getLongExchange(),
            spread.getCurrencyPair(),
            activePosition.getLongTrade().getOrderId(),
            activePosition.getLongTrade().getVolume());
        BigDecimal shortVolume = getVolumeForOrder(
            spread.getShortExchange(),
            spread.getCurrencyPair(),
            activePosition.getShortTrade().getOrderId(),
            activePosition.getShortTrade().getVolume());

        LOGGER.debug("Volumes: {}/{}", longVolume, shortVolume);

        BigDecimal longLimitPrice;
        BigDecimal shortLimitPrice;

        // Calculate a more accurate price based on the order book rather than just multiplying the bid or ask price.
        // Sometimes there isn't enough currency available at the listed price and part of your order has to be filled
        // at a slightly worse price, which we call "slip". This is a little bit computationally expensive which is why
        // we wait until we're pretty sure we want to trade before we do it.
        try {
            longLimitPrice = getLimitPrice(spread.getLongExchange(), spread.getCurrencyPair(), longVolume, Order.OrderType.BID);
            shortLimitPrice = getLimitPrice(spread.getShortExchange(), spread.getCurrencyPair(), shortVolume, Order.OrderType.ASK);
        } catch (ExchangeException e) {
            LOGGER.warn("Failed to fetch order books (on active position) for {}/{} and currency {}/{} to compute entry prices: {}",
                longExchangeName,
                spread.getShortExchange().getDefaultExchangeSpecification().getExchangeName(),
                spread.getCurrencyPair().base,
                spread.getCurrencyPair().counter,
                e.getMessage());
            return;
        }

        LOGGER.debug("Limit prices: {}/{}", longLimitPrice, shortLimitPrice);

        // this spread is based on the prices we calculated using the order book, so it's more accurate than the original estimate
        BigDecimal spreadVerification = spreadService.computeSpread(longLimitPrice, shortLimitPrice);

        LOGGER.debug("Spread verification: {}", spreadVerification);

        if (longVolume.compareTo(BigDecimal.ZERO) <= 0 || shortVolume.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.error("Computed trade volume for exiting position was zero or less than zero!");
            return;
        }

        if (conditionService.isBlackoutCondition(spread.getLongExchange()) || conditionService.isBlackoutCondition(spread.getShortExchange())) {
            LOGGER.warn("Cannot exit position on one or more exchanges due to user configured blackout");
            return;
        }

        if (!isTradeExpired() && !conditionService.isForceCloseCondition() && spreadVerification.compareTo(activePosition.getExitTarget()) > 0) {
            LOGGER.debug("Not enough liquidity to execute both trades profitably!");
            return;
        }

        // if an exchange is configured as feeComputation = CLIENT then we subtract the fees here
        BigDecimal longVolumeWithFees = subtractFees(spread.getLongExchange(), spread.getCurrencyPair(), longVolume);
        BigDecimal shortVolumeWithFees = subtractFees(spread.getShortExchange(), spread.getCurrencyPair(), shortVolume);

        // If we are being forced to exit or the timeout has elapsed, but the spread is still high enough that
        // we could re-enter this position, then don't exit.
        //
        // Also, don't spam the logs with this warning. It's possible that this condition could last for awhile
        // and this code could be executed frequently.
        if (isTradeExpired() && spreadVerification.compareTo(tradingConfiguration.getEntrySpread()) < 0) {
            if (!timeoutExitWarning) {
                LOGGER.warn("Timeout exit triggered");
                LOGGER.warn("Cannot exit now because spread would cause immediate reentry");
                timeoutExitWarning = true;
            }
            return;
        }

        logExitTrade();

        try {
            LOGGER.info("Long close: {} {} {} @ {} ({} slip) = {}{}",
                longExchangeName,
                spread.getCurrencyPair(),
                longVolume,
                longLimitPrice,
                longLimitPrice.subtract(spread.getLongTicker().getBid()),
                Currency.USD.getSymbol(),
                longVolume.multiply(spread.getLongTicker().getBid()));
            LOGGER.info("Short close: {} {} {} @ {} ({} slip) = {}{}",
                shortExchangeName,
                spread.getCurrencyPair(),
                shortVolume,
                shortLimitPrice,
                spread.getShortTicker().getAsk().subtract(shortLimitPrice),
                Currency.USD.getSymbol(),
                shortVolume.multiply(spread.getShortTicker().getAsk()));

            executeOrderPair(
                spread.getLongExchange(), spread.getShortExchange(),
                spread.getCurrencyPair(),
                longLimitPrice, shortLimitPrice,
                longVolumeWithFees, shortVolumeWithFees,
                false);
        } catch (IOException e) {
            LOGGER.error("IOE executing limit orders: ", e);

            // We don't return here because there is no trade execution below this line. If we returned we'd lose out
            // on a bunch of logging and cleanup that is still useful to do even if the trades didn't work. But,
            // let's bail out so the bot doesn't do anything bad after failing to execute the orders.

            bailOut = true;
        }

        LOGGER.info("Combined account balances on entry: ${}", activePosition.getEntryBalance());

        final BigDecimal updatedBalance = logCurrentExchangeBalances(spread.getLongExchange(), spread.getShortExchange());
        final BigDecimal profit = updatedBalance.subtract(activePosition.getEntryBalance());

        LOGGER.info("Profit calculation: ${} - ${} = ${}",
            updatedBalance,
            activePosition.getEntryBalance(),
            profit);

        final ArbitrageLog arbitrageLog = ArbitrageLog.ArbitrageLogBuilder.builder()
            .withShortExchange(shortExchangeName)
            .withShortCurrency(spread.getCurrencyPair().toString())
            .withShortSpread(shortLimitPrice)
            .withShortSlip(spread.getShortTicker().getAsk().subtract(shortLimitPrice))
            .withShortAmount(shortVolume.multiply(spread.getShortTicker().getAsk()))
            .withLongExchange(longExchangeName)
            .withLongCurrency(spread.getCurrencyPair().toString())
            .withLongSpread(longLimitPrice)
            .withLongSlip(longLimitPrice.subtract(spread.getLongTicker().getBid()))
            .withLongAmount(longVolume.multiply(spread.getLongTicker().getBid()))
            .withProfit(profit)
            .withTimestamp(OffsetDateTime.now())
            .build();

        persistArbitrageToCsvFile(arbitrageLog);

        // Email notification must be sent before we set activePosition = null
        notificationService.sendEmailNotificationBodyForExitTrade(spread, longVolume, longLimitPrice, shortVolume,
            shortLimitPrice, activePosition.getEntryBalance(), updatedBalance);

        activePosition = null;

        FileUtils.deleteQuietly(new File(STATE_FILE));

        if (conditionService.isForceCloseCondition()) {
            conditionService.clearForceCloseCondition();
        }
    }

    // if feeComputation == CLIENT we want to compute the fees and add them to the volume
    private BigDecimal addFees(Exchange exchange, CurrencyPair currencyPair, BigDecimal volume) {
        if (exchangeService.getExchangeMetadata(exchange).getFeeComputation().equals(FeeComputation.CLIENT)) {
            BigDecimal fee = volume
                .multiply(exchangeService.getExchangeFee(exchange, currencyPair, true))
                .setScale(BTC_SCALE, RoundingMode.HALF_EVEN);

            BigDecimal adjustedVolume = volume.add(fee);

            LOGGER.info("{} fees are computed in the client: {} - {} = {}",
                exchange.getExchangeSpecification().getExchangeName(),
                volume,
                fee,
                adjustedVolume);

            return adjustedVolume;
        }

        return volume;
    }

    // if feeComputation == CLIENT we want to compute the fees and subtract them from the volume
    private BigDecimal subtractFees(Exchange exchange, CurrencyPair currencyPair, BigDecimal volume) {
        if (exchangeService.getExchangeMetadata(exchange).getFeeComputation().equals(FeeComputation.CLIENT)) {
            BigDecimal fee = volume
                .multiply(exchangeService.getExchangeFee(exchange, currencyPair, true))
                .setScale(BTC_SCALE, RoundingMode.HALF_EVEN);

            BigDecimal adjustedVolume = volume.subtract(fee);

            LOGGER.info("{} fees are computed in the client: {} - {} = {}",
                exchange.getExchangeSpecification().getExchangeName(),
                volume,
                fee,
                adjustedVolume);

            return adjustedVolume;
        }

        return volume;
    }

    // convenience method to encapsulate logging an exit
    private void logExitTrade() {
        if (isTradeExpired()) {
            LOGGER.warn("***** TIMEOUT EXIT *****");
            timeoutExitWarning = false;
        } else if (conditionService.isForceCloseCondition()) {
            LOGGER.warn("***** FORCED EXIT *****");
        } else {
            LOGGER.info("***** EXIT *****");
        }
    }

    // convenience method to encapsulate logging an entry
    private void logEntryTrade(Spread spread, String shortExchangeName, String longExchangeName, BigDecimal exitTarget,
                               BigDecimal longVolume, BigDecimal shortVolume, BigDecimal longLimitPrice, BigDecimal shortLimitPrice) {

        if (conditionService.isForceOpenCondition()) {
            LOGGER.warn("***** FORCED ENTRY *****");
        } else {
            LOGGER.info("***** ENTRY *****");
        }

        LOGGER.info("Entry spread: {}", spread.getIn());
        LOGGER.info("Exit spread target: {}", exitTarget);
        LOGGER.info("Long entry: {} {} {} @ {} ({} slip) = {}{}",
            longExchangeName,
            spread.getCurrencyPair(),
            longVolume,
            longLimitPrice,
            longLimitPrice.subtract(spread.getLongTicker().getAsk()),
            Currency.USD.getSymbol(),
            longVolume.multiply(longLimitPrice));
        LOGGER.info("Short entry: {} {} {} @ {} ({} slip) = {}{}",
            shortExchangeName,
            spread.getCurrencyPair(),
            shortVolume,
            shortLimitPrice,
            spread.getShortTicker().getBid().subtract(shortLimitPrice),
            Currency.USD.getSymbol(),
            shortVolume.multiply(shortLimitPrice));
    }

    // get volume for an entry position considering exposure and exchange step size if there is one
    private BigDecimal getVolumeForEntryPosition(Exchange exchange, BigDecimal maxExposure, BigDecimal price, CurrencyPair currencyPair, @SuppressWarnings("SameParameterValue") int scale) {
        final BigDecimal volume = maxExposure.divide(price, scale, RoundingMode.HALF_EVEN);
        final BigDecimal stepSize = exchange.getExchangeMetaData().getCurrencyPairs()
            .getOrDefault(exchangeService.convertExchangePair(exchange, currencyPair), NULL_CURRENCY_PAIR_METADATA).getAmountStepSize();

        if (stepSize != null) {
            return roundByStep(volume, stepSize);
        }

        return volume;
    }

    // get the smallest possible order for an entry position on an exchange
    private BigDecimal getMinimumAmountForEntryPosition(Spread spread, Exchange longExchange) {
        BigDecimal minimumAmount = longExchange.getExchangeMetaData().getCurrencyPairs()
            .getOrDefault(exchangeService.convertExchangePair(longExchange, spread.getCurrencyPair()), NULL_CURRENCY_PAIR_METADATA)
            .getMinimumAmount();

        if (minimumAmount == null) {
            minimumAmount = new BigDecimal("0.001"); // TODO too big?
        }
        return minimumAmount;
    }

    // execute a buy and a sell together
    private void executeOrderPair(Exchange longExchange, Exchange shortExchange,
                                  CurrencyPair currencyPair,
                                  BigDecimal longLimitPrice, BigDecimal shortLimitPrice,
                                  BigDecimal longVolume, BigDecimal shortVolume,
                                  boolean isPositionOpen) throws IOException, ExchangeException {

        // build two limit orders - orders that execute at a specific price
        // this helps us to get the "maker" price on exchanges where the fees are lower for makers
        LimitOrder longLimitOrder = new LimitOrder.Builder(isPositionOpen ? Order.OrderType.BID : Order.OrderType.ASK, exchangeService.convertExchangePair(longExchange, currencyPair))
            .limitPrice(longLimitPrice)
            .originalAmount(longVolume)
            .build();
        LimitOrder shortLimitOrder = new LimitOrder.Builder(isPositionOpen ? Order.OrderType.ASK : Order.OrderType.BID, exchangeService.convertExchangePair(shortExchange, currencyPair))
            .limitPrice(shortLimitPrice)
            .originalAmount(shortVolume)
            .build();

        // Kraken won't consider it a margin trade without this
        shortLimitOrder.setLeverage("2");

        LOGGER.debug("{}: {}",
            longExchange.getExchangeSpecification().getExchangeName(),
            longLimitOrder);
        LOGGER.debug("{}: {}",
            shortExchange.getExchangeSpecification().getExchangeName(),
            shortLimitOrder);

        try { // get the order IDs from each exchange
            String longOrderId = longExchange.getTradeService().placeLimitOrder(longLimitOrder);
            String shortOrderId = shortExchange.getTradeService().placeLimitOrder(shortLimitOrder);

            // TODO not happy with this coupling, need to refactor this
            // activePosition tracks the orders we just opened
            if (isPositionOpen) {
                activePosition.getLongTrade().setOrderId(longOrderId);
                activePosition.getShortTrade().setOrderId(shortOrderId);
            } else {
                activePosition.getLongTrade().setOrderId(null);
                activePosition.getShortTrade().setOrderId(null);
            }

            LOGGER.info("{} limit order ID: {}",
                longExchange.getExchangeSpecification().getExchangeName(),
                longOrderId);
            LOGGER.info("{} limit order ID: {}",
                shortExchange.getExchangeSpecification().getExchangeName(),
                shortOrderId);
        } catch (ExchangeException e) {
            // At this point we may or may not have executed one of the trades so we're in an unknown state.
            // For now, we'll just bail out and let the human figure out what to do to fix it.
            // TODO Look at both exchanges and attempt close any open positions.
            LOGGER.error("Exchange returned an error executing trade!", e);
            bailOut = true;
            return;
        }

        LOGGER.info("Waiting for limit orders to complete...");

        OpenOrders longOpenOrders;
        OpenOrders shortOpenOrders;
        int count = 0;

        // every few seconds check the exchanges to see if our orders are filled yet
        do {
            longOpenOrders = fetchOpenOrders(longExchange).orElse(null);
            shortOpenOrders = fetchOpenOrders(shortExchange).orElse(null);

            // only print the warning every 10th iteration
            // but do print warnings because otherwise I worry that the computer has died
            if (longOpenOrders != null && !longOpenOrders.getOpenOrders().isEmpty() && count % 10 == 0) {
                LOGGER.warn(collectOpenOrders(longExchange, longOpenOrders));
            }

            if (shortOpenOrders != null && !shortOpenOrders.getOpenOrders().isEmpty() && count % 10 == 0) {
                LOGGER.warn(collectOpenOrders(shortExchange, shortOpenOrders));
            }

            count++;

            try {
                // yes this is a busy wait, the bot has nothing better to do right now except wait for the orders to fill
                //noinspection BusyWait
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                LOGGER.trace("Sleep interrupted!", e);
            }
        } while (longOpenOrders == null || !longOpenOrders.getOpenOrders().isEmpty()
            || shortOpenOrders == null || !shortOpenOrders.getOpenOrders().isEmpty());

        // yay!
        LOGGER.info("Trades executed successfully!");
    }

    // summarize all the open orders on an exchange, used while we're waiting for orders to fill
    private String collectOpenOrders(Exchange exchange, OpenOrders openOrders) {
        String header = String.format("%s has the following open orders:\n", exchange.getExchangeSpecification().getExchangeName());

        return header + openOrders.getOpenOrders()
            .stream()
            .map(LimitOrder::toString)
            .collect(Collectors.joining("\n"));
    }

    // fetch open orders from the exchange
    private Optional<OpenOrders> fetchOpenOrders(Exchange exchange) {
        try {
            return Optional.of(exchange.getTradeService().getOpenOrders());
        } catch (IOException | ExchangeException e) {
            LOGGER.error("{} threw an Exception while fetching open orders: ",
                exchange.getExchangeSpecification().getExchangeName(), e);
        }

        return Optional.empty();
    }

    /**
     * Fetch an order and figure out its volume. If the exchange doesn't support that, use a default value instead.
     *
     * @param exchange The exchange to look for orders on.
     * @param currencyPair The currency pair for this order.
     * @param orderId The ID for the order.
     * @param defaultVolume The default volume in case we can't look it up.
     * @return An order volume.
     */
    BigDecimal getVolumeForOrder(Exchange exchange, CurrencyPair currencyPair, String orderId, BigDecimal defaultVolume) {
        // first, try to fetch the order from the exchange by its ID and just return its volume
        // not supported by all exchanges
        try {
            LOGGER.debug("{}: Attempting to fetch volume from order by ID: {}", exchange.getExchangeSpecification().getExchangeName(), orderId);
            BigDecimal volume = Optional.ofNullable(exchange.getTradeService().getOrder(orderId))
                .orElseThrow(() -> new NotAvailableFromExchangeException(orderId))
                .stream()
                .findFirst()
                .orElseThrow(() -> new OrderNotFoundException(orderId))
                .getOriginalAmount();

            LOGGER.debug("{}: Order {} volume is: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                orderId,
                volume);

            if (volume == null || volume.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Volume must be more than zero.");
            }

            LOGGER.debug("{}: Using volume: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                orderId);

            return volume;
        } catch (NotAvailableFromExchangeException e) {
            LOGGER.debug("{}: Does not support fetching orders by ID", exchange.getExchangeSpecification().getExchangeName());
        } catch (IOException e) {
            LOGGER.warn("{}: Unable to fetch order {}", exchange.getExchangeSpecification().getExchangeName(), orderId, e);
        } catch (IllegalStateException e) {
            LOGGER.debug(e.getMessage());
        }

        // next, try to get the account balance for the BASE pair (eg. the BTC in BTC/USD)
        try {
            BigDecimal balance = exchangeService.getAccountBalance(exchange, currencyPair.base);

            if (BigDecimal.ZERO.compareTo(balance) < 0) {
                LOGGER.debug("{}: Using {} balance: {}", exchange.getExchangeSpecification().getExchangeName(), currencyPair.base.toString(), balance);

                return balance;
            }
        } catch (IOException e) {
            LOGGER.warn("{}: Unable to fetch {} account balance", exchange.getExchangeSpecification().getExchangeName(), currencyPair.base.toString(), e);
        }

        // finally, just return the default value
        LOGGER.debug("{}: Falling back to default volume: {}",
            exchange.getExchangeSpecification().getExchangeName(),
            defaultVolume);

        return defaultVolume;
    }

    /**
     * Figure out the price for a limit order based on the order book. Computationally expensive, but accurate.
     *
     * @param exchange The exchange to use.
     * @param rawCurrencyPair The currency pair to use, not converted for home currency.
     * @param allowedVolume The volume we're looking for (governs how many orders to look through before stopping).
     * @param orderType Are we buying or selling? Use the bid or ask price?
     * @return The more accurate price for this order.
     */
    BigDecimal getLimitPrice(Exchange exchange, CurrencyPair rawCurrencyPair, BigDecimal allowedVolume, Order.OrderType orderType) {
        CurrencyPair currencyPair = exchangeService.convertExchangePair(exchange, rawCurrencyPair);

        try {
            OrderBook orderBook = exchange.getMarketDataService().getOrderBook(currencyPair);
            List<LimitOrder> orders = orderType.equals(Order.OrderType.ASK) ? orderBook.getAsks() : orderBook.getBids();
            BigDecimal price;
            BigDecimal volume = BigDecimal.ZERO;

            // walk through orders, ordered by price, until we satisfy all the volume we need
            // return the price of the last order we see
            // TODO is this correct? should we be averaging the order prices together to get the overall price?
            for (LimitOrder order : orders) {
                price = order.getLimitPrice();
                volume = volume.add(order.getRemainingAmount());

                if (volume.compareTo(allowedVolume) > 0) {
                    return price;
                }
            }
        } catch (IOException e) {
            LOGGER.error("IOE fetching {} {} order volume", exchange.getExchangeSpecification().getExchangeName(), currencyPair, e);
        }

        throw new RuntimeException("Not enough liquidity on exchange to fulfill required volume!");
    }

    /**
     * Figure out the largest trade we can make in our home currency. If fixedExposure is configured, just
     * use that value. Otherwise, go through each of the exchanges passed in and find the smallest balance,
     * then multiply by TRADE_PORTION to find the amount to trade.
     *
     * @param exchanges A list of exchanges to inspect balances for.
     * @return The maximum amount that can be traded across the given exchanges.
     */
    BigDecimal getMaximumExposure(Exchange ... exchanges) {
        if (tradingConfiguration.getFixedExposure() != null) {
            return tradingConfiguration.getFixedExposure();
        } else {
            BigDecimal smallestBalance = Arrays.stream(exchanges)
                .parallel()
                .map(exchange -> {
                    try {
                        return exchangeService.getAccountBalance(exchange);
                    } catch (IOException e) {
                        LOGGER.trace("IOException fetching {} account balance",
                            exchange.getExchangeSpecification().getExchangeName());
                    }

                    return BigDecimal.ZERO;
                })
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

            BigDecimal exposure = smallestBalance
                .multiply(TRADE_PORTION)
                .setScale(DecimalConstants.USD_SCALE, RoundingMode.HALF_EVEN);

            LOGGER.debug("Maximum exposure for {}: {}", exchanges, exposure);

            return exposure;
        }
    }

    // log the balances of two exchanges and the sum of both
    private BigDecimal logCurrentExchangeBalances(Exchange longExchange, Exchange shortExchange) {
        try {
            BigDecimal longBalance = exchangeService.getAccountBalance(longExchange);
            BigDecimal shortBalance = exchangeService.getAccountBalance(shortExchange);
            BigDecimal totalBalance = longBalance.add(shortBalance);

            LOGGER.info("Updated account balances: {} ${} + {} ${} = ${}",
                longExchange.getExchangeSpecification().getExchangeName(),
                longBalance,
                shortExchange.getExchangeSpecification().getExchangeName(),
                shortBalance,
                totalBalance);

            return totalBalance;
        } catch (IOException e) {
            LOGGER.error("IOE fetching account balances: ", e);
        }

        return BigDecimal.ZERO;
    }

    /**
     * Get the multiple of "step" that is nearest to the original number.
     *
     * The formula is: step * round(input / step)
     * All the BigDecimals make it really hard to read. We're using setScale() instead of round() because you can't
     * set the precision on round() to zero. You can do it with setScale() and it will implicitly do the rounding.
     *
     * @param input The original number.
     * @param step The step to round by.
     * @return A multiple of step that is the nearest to the original number.
     */
    static BigDecimal roundByStep(BigDecimal input, BigDecimal step) {
        LOGGER.info("input = {} step = {}", input, step);

        BigDecimal result = input
            .divide(step, RoundingMode.HALF_EVEN)
            .setScale(0, RoundingMode.HALF_EVEN)
            .multiply(step)
            .setScale(step.scale(), RoundingMode.HALF_EVEN);

        LOGGER.info("result = {}", result);

        return result;
    }

    // determine whether a trade has exceeded the configured trade timeout
    private boolean isTradeExpired() {
        if (tradingConfiguration.getTradeTimeout() == null || activePosition == null || activePosition.getEntryTime() == null) {
            return false;
        }

        return activePosition.getEntryTime().plusHours(tradingConfiguration.getTradeTimeout()).isBefore(OffsetDateTime.now());
    }

    /**
     * Write an entry in the trade history file.
     *
     * @param arbitrageLog A log message to write to the TRADE_HISTORY_FILE.
     */
    void persistArbitrageToCsvFile(ArbitrageLog arbitrageLog) {
        final File csvFile = new File(TRADE_HISTORY_FILE);

        try {
            if (!csvFile.exists()) {
                // Add headers first
                FileUtils.write(csvFile, arbitrageLog.csvHeaders(), StandardCharsets.UTF_8, csvFile.exists());
            }
            FileUtils.write(csvFile, arbitrageLog.toCsv(), StandardCharsets.UTF_8, csvFile.exists());
        }
        catch (IOException e) {
            LOGGER.error("Unable to log the trade into the csv file. Reason: {}", e.getMessage());
        }
    }
}
