package com.r307.arbitrader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r307.arbitrader.DecimalConstants;
import com.r307.arbitrader.Utils;
import com.r307.arbitrader.config.FeeComputation;
import com.r307.arbitrader.config.TradingConfiguration;
import com.r307.arbitrader.exception.OrderNotFoundException;
import com.r307.arbitrader.service.cache.ExchangeBalanceCache;
import com.r307.arbitrader.service.cache.OrderVolumeCache;
import com.r307.arbitrader.service.model.*;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import org.apache.commons.io.FileUtils;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.FeeTier;
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
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;
import static com.r307.arbitrader.DecimalConstants.USD_SCALE;

/**
 * Trade analysis and execution.
 */
@Component
public class TradingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TradingService.class);
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
    private final NotificationService notificationService;
    private final ExchangeBalanceCache exchangeBalanceCache = new ExchangeBalanceCache();
    private final OrderVolumeCache orderVolumeCache = new OrderVolumeCache();
    private boolean timeoutExitWarning = false;
    private ActivePosition activePosition = null;
    private boolean bailOut = false;
    private AtomicBoolean openOrdersFlag = new AtomicBoolean(false);

    public TradingService(
        ObjectMapper objectMapper,
        TradingConfiguration tradingConfiguration,
        ConditionService conditionService,
        ExchangeService exchangeService,
        SpreadService spreadService,
        NotificationService notificationService) {

        this.objectMapper = objectMapper;
        this.tradingConfiguration = tradingConfiguration;
        this.conditionService = conditionService;
        this.exchangeService = exchangeService;
        this.spreadService = spreadService;
        this.notificationService = notificationService;
    }

    /**
     * Evaluate whether or not to trade (both entry and exit). Execute a trade if appropriate.
     *
     * @param spread The Spread contains the exchanges and prices for the trade.
     */
    public synchronized void trade(Spread spread) {
        if (bailOut) {
            LOGGER.error("Exiting immediately to avoid erroneous trades.");
            System.exit(1);
        }

        if (openOrdersFlag.get()) {
            LOGGER.debug("We have open orders waiting to be filled. Skipping this event");
            return;
        }

        final String shortExchangeName = spread.getShortExchange().getExchangeSpecification().getExchangeName();
        final String longExchangeName = spread.getLongExchange().getExchangeSpecification().getExchangeName();

        LOGGER.debug("Attempting trade: {}/{} {} {}/{}",
            longExchangeName,
            shortExchangeName,
            spread.getCurrencyPair(),
            spread.getIn(),
            spread.getOut());

        if (conditionService.isBlackoutCondition(spread.getLongExchange()) || conditionService.isBlackoutCondition(spread.getShortExchange())) {
            LOGGER.warn("Cannot alter position on one or more exchanges due to user configured blackout");
            return;
        }
        final ExchangeFee longFeePercent = exchangeService.getExchangeFee(spread.getLongExchange(), spread.getCurrencyPair(), true);
        final ExchangeFee shortFeePercent = exchangeService.getExchangeFee(spread.getShortExchange(), spread.getCurrencyPair(), true);
        final BigDecimal entrySpreadTarget = spreadService.getEntrySpreadTarget(tradingConfiguration, longFeePercent, shortFeePercent);
        // This is more verbose than it has to be. I'm trying to keep it easy to read as we continue
        // adding more different conditions that can affect whether we trade or not.
        if (activePosition == null) {
            if (conditionService.isForceOpenCondition(spread.getCurrencyPair(), longExchangeName, shortExchangeName)) {
                LOGGER.debug("enterPosition() {}/{} {} - forced", longExchangeName, shortExchangeName, spread.getCurrencyPair());
                enterPosition(spread);
            } else if (spread.getIn().compareTo(entrySpreadTarget) > 0) {
                LOGGER.debug("enterPosition() {}/{} {} - spread in {} > entry spread target {}", longExchangeName, shortExchangeName, spread.getCurrencyPair(), spread.getIn(), entrySpreadTarget);
                LOGGER.debug("entry spread target {} was calculated from the effective entry spread target {}, with {} long fees and {} short fees",
                    entrySpreadTarget,
                    tradingConfiguration.getEntrySpreadTarget(),
                    longFeePercent,
                    shortFeePercent);
                enterPosition(spread);
            }
        } else if (spread.getCurrencyPair().equals(activePosition.getCurrencyPair())
                && longExchangeName.equals(activePosition.getLongTrade().getExchange())
                && shortExchangeName.equals(activePosition.getShortTrade().getExchange())) {

            if (conditionService.isForceCloseCondition()) {
                LOGGER.debug("exitPosition() {}/{} {} - forced", longExchangeName, shortExchangeName, spread.getCurrencyPair());
                exitPosition(spread);
            } else if (isActivePositionExpired()) {
                LOGGER.debug("exitPosition() {}/{} {} - active position timed out", longExchangeName, shortExchangeName, spread.getCurrencyPair());
                exitPosition(spread);
            } else if (spread.getOut().compareTo(activePosition.getExitTarget()) < 0) {
                LOGGER.debug("exitPosition() {}/{} {} - spread out {} < exit target {}", longExchangeName, shortExchangeName, spread.getCurrencyPair(), spread.getOut(), activePosition.getExitTarget());
                exitPosition(spread);
            }
        }
    }

    public ActivePosition getActivePosition() {
        return activePosition;
    }

    public void setActivePosition(ActivePosition activePosition) {
        this.activePosition = activePosition;
    }

    // enter a position
    private void enterPosition(Spread spread) {
        final String longExchangeName = spread.getLongExchange().getExchangeSpecification().getExchangeName();
        final String shortExchangeName = spread.getShortExchange().getExchangeSpecification().getExchangeName();
        final CurrencyPair currencyPairLongExchange = exchangeService.convertExchangePair(spread.getLongExchange(), spread.getCurrencyPair());
        final CurrencyPair currencyPairShortExchange = exchangeService.convertExchangePair(spread.getShortExchange(), spread.getCurrencyPair());
        final ExchangeFee longFee = exchangeService.getExchangeFee(spread.getLongExchange(), currencyPairLongExchange, true);
        final ExchangeFee shortFee = exchangeService.getExchangeFee(spread.getShortExchange(), currencyPairShortExchange, true);
        final BigDecimal exitSpreadTarget = spreadService.getExitSpreadTarget(tradingConfiguration, spread.getIn(), longFee, shortFee);
        final BigDecimal maxExposure = getMaximumExposure(spread.getLongExchange(), spread.getShortExchange());
        final FeeComputation longFeeComputation = exchangeService.getExchangeMetadata(spread.getLongExchange()).getFeeComputation();
        final FeeComputation shortFeeComputation = exchangeService.getExchangeMetadata(spread.getShortExchange()).getFeeComputation();

        // check whether we have enough money to trade (forcing it can't work if we can't afford it)
        if (!validateMaxExposure(maxExposure, spread, currencyPairLongExchange, currencyPairShortExchange)) {
            return;
        }

        // Figure out the scale (number of decimal places) for each exchange based on its CurrencyMetaData.
        // If there is no metadata, fall back to BTC's default of 8 places that should work in most cases.
        final int longVolumeScale = computeVolumeScale(spread.getLongExchange(), currencyPairLongExchange);
        final int shortVolumeScale = computeVolumeScale(spread.getShortExchange(), currencyPairShortExchange);

        LOGGER.debug("Max exposure: {}", maxExposure);
        LOGGER.debug("Long volume scale: {}", longVolumeScale);
        LOGGER.debug("Short volume scale: {}", shortVolumeScale);
        LOGGER.debug("Long ticker ASK: {}", spread.getLongTicker().getAsk());
        LOGGER.debug("Short ticker BID: {}", spread.getShortTicker().getBid());
        LOGGER.debug("Long trade fee percent: {}", longFee.getTotalFee());
        LOGGER.debug("Short trade and margin fee percent: {} + {} = {}", shortFee.getTradeFee(), shortFee.getMarginFee(), shortFee.getTotalFee());

        // figure out how much we want to trade
        EntryTradeVolume tradeVolume;
        try {
            tradeVolume = TradeVolume.getEntryTradeVolume(
                longFeeComputation,
                shortFeeComputation,
                maxExposure,maxExposure,
                spread.getLongTicker().getAsk(),
                spread.getShortTicker().getBid(),
                longFee,
                shortFee,
                exitSpreadTarget,
                longVolumeScale,
                shortVolumeScale);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Cannot instantiate order volumes, exiting trade.");
            return;
        }

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
            longLimitPrice = getLimitPrice(spread.getLongExchange(), spread.getCurrencyPair(), tradeVolume.getLongVolume(), Order.OrderType.ASK);
            shortLimitPrice = getLimitPrice(spread.getShortExchange(), spread.getCurrencyPair(), tradeVolume.getShortVolume(), Order.OrderType.BID);
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
        final boolean isForcedOpenCondition = conditionService.isForceOpenCondition(spread.getCurrencyPair(), longExchangeName, shortExchangeName);

        final BigDecimal entrySpreadTarget = spreadService.getEntrySpreadTarget(tradingConfiguration, longFee, shortFee);
        if (!isForcedOpenCondition && spreadVerification.compareTo(entrySpreadTarget) < 0) {
            LOGGER.debug("Spread verification {} is less than entry spread target {}, will not trade", spreadVerification, entrySpreadTarget); // this is debug because it can get spammy
            return;
        }

        if(longLimitPrice.compareTo(spread.getLongTicker().getAsk()) != 0 || shortLimitPrice.compareTo(spread.getShortTicker().getBid()) != 0) {
            //Adjust the volume after slip so the trade stays market neutral
            try {
                tradeVolume = TradeVolume.getEntryTradeVolume(longFeeComputation, shortFeeComputation, maxExposure, maxExposure, longLimitPrice, shortLimitPrice, longFee, shortFee, exitSpreadTarget, longVolumeScale, shortVolumeScale);
            } catch (IllegalArgumentException e) {
                LOGGER.error("Cannot instantiate order volumes, exiting trade.");
                return;
            }
        }

        final BigDecimal longAmountStepSize = spread.getLongExchange().getExchangeMetaData().getCurrencyPairs()
            .getOrDefault(spread.getCurrencyPair(), NULL_CURRENCY_PAIR_METADATA).getAmountStepSize();
        final BigDecimal shortAmountStepSize =  spread.getShortExchange().getExchangeMetaData().getCurrencyPairs()
            .getOrDefault(spread.getCurrencyPair(), NULL_CURRENCY_PAIR_METADATA).getAmountStepSize();

        //Adjust order volumes so they match the fee computation, step size and scales of the exchanges
        try{
            tradeVolume.adjustOrderVolume(longExchangeName, shortExchangeName, longAmountStepSize, shortAmountStepSize);
        } catch(IllegalArgumentException e) {
            LOGGER.error("Cannot adjust order volumes, exiting trade.");
            return;
        }

        //Scales or steps might have broken market neutrality, check that the entry is still as market neutral as possible
        //Otherwise it could mess with profit estimations!
        if (!conditionService.isForceOpenCondition(spread.getCurrencyPair(), longExchangeName, shortExchangeName)
            && !tradeVolume.isMarketNeutral()) {
            LOGGER.info("Trade is not market neutral (market neutrality rating is {}), profit estimates might be off, will not trade.",
                tradeVolume.getMarketNeutralityRating());
            return;
        }

        logEntryTrade(spread, shortExchangeName, longExchangeName, exitSpreadTarget, tradeVolume, longFeeComputation, shortFeeComputation, longLimitPrice, shortLimitPrice, isForcedOpenCondition);

        BigDecimal totalBalance = logCurrentExchangeBalances(spread.getLongExchange(), spread.getShortExchange());

        try {
            activePosition = new ActivePosition();
            activePosition.setEntryTime(OffsetDateTime.now());
            activePosition.setCurrencyPair(spread.getCurrencyPair());
            activePosition.setExitTarget(exitSpreadTarget);
            activePosition.setEntryBalance(totalBalance);
            activePosition.getLongTrade().setExchange(spread.getLongExchange());
            activePosition.getLongTrade().setVolume(tradeVolume.getLongOrderVolume());
            activePosition.getLongTrade().setEntry(longLimitPrice);
            activePosition.getShortTrade().setExchange(spread.getShortExchange());
            activePosition.getShortTrade().setVolume(tradeVolume.getShortOrderVolume());
            activePosition.getShortTrade().setEntry(shortLimitPrice);

            executeOrderPair(
                spread.getLongExchange(), spread.getShortExchange(),
                spread.getCurrencyPair(),
                longLimitPrice, shortLimitPrice,
                tradeVolume.getLongOrderVolume(), tradeVolume.getShortOrderVolume(),
                true);

            notificationService.sendEntryTradeNotification(spread, exitSpreadTarget, tradeVolume,
                longLimitPrice, shortLimitPrice, isForcedOpenCondition);
        } catch (IOException e) {
            LOGGER.error("IOE executing limit orders: ", e);
            activePosition = null;
        }

        try {
            Utils.createStateFile(objectMapper.writeValueAsString(activePosition));
        } catch (IOException e) {
            LOGGER.error("Unable to write state file!", e);
        }

        conditionService.clearForceOpenCondition();
    }

    /**
     * Fetch the correct volume scale from an exchange's metadata, or return a default
     * value if it cannot be found.
     *
     * @param exchange The exchange to fetch metadata from.
     * @param currencyPair The currency pair to look for.
     * @return The number of decimals allowed for a volume in this currency pair on this exchange.
     */
    Integer computeVolumeScale(Exchange exchange, CurrencyPair currencyPair) {
        final CurrencyPairMetaData defaultPairMetaData = new CurrencyPairMetaData(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.valueOf(Long.MAX_VALUE),
            BTC_SCALE,
            BTC_SCALE,
            new FeeTier[0],
            Currency.BTC
        );

        final ExchangeMetaData exchangeMetaData = exchange.getExchangeMetaData();
        final CurrencyPairMetaData currencyPairMetaData = exchangeMetaData.getCurrencyPairs().getOrDefault(
            currencyPair,
            defaultPairMetaData
        );

        if (currencyPairMetaData.getVolumeScale() == null) {
            LOGGER.debug("Defaulting to scale of {} for volume because metadata is unavailable", BTC_SCALE);
            return BTC_SCALE;
        }

        return currencyPairMetaData.getVolumeScale();
    }

    /**
     * Fetch the correct price scale from an exchange's metadata, or return a default
     * value if it cannot be found.
     *
     * @param exchange The exchange to fetch metadata from.
     * @param currencyPair The currency pair to look for.
     * @return The number of decimals allowed for a price in this currency pair on this exchange.
     */
    Integer computePriceScale(Exchange exchange, CurrencyPair currencyPair) {
        final CurrencyPairMetaData defaultPairMetaData = new CurrencyPairMetaData(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.valueOf(Long.MAX_VALUE),
            BTC_SCALE,
            BTC_SCALE,
            new FeeTier[0],
            Currency.BTC
        );
        final ExchangeMetaData exchangeMetaData = exchange.getExchangeMetaData();
        final CurrencyPairMetaData currencyPairMetaData = exchangeMetaData.getCurrencyPairs().getOrDefault(
            currencyPair,
            defaultPairMetaData
        );

        if (currencyPairMetaData.getPriceScale() == null) {
            LOGGER.debug("Defaulting to scale of {} for price because metadata is unavailable", USD_SCALE);
            return USD_SCALE;
        }

        return currencyPairMetaData.getPriceScale();
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
    private void exitPosition(Spread spread) {
        final String longExchangeName = spread.getLongExchange().getExchangeSpecification().getExchangeName();
        final String shortExchangeName = spread.getShortExchange().getExchangeSpecification().getExchangeName();
        final CurrencyPair currencyPairLongExchange = exchangeService.convertExchangePair(spread.getLongExchange(), spread.getCurrencyPair());
        final CurrencyPair currencyPairShortExchange = exchangeService.convertExchangePair(spread.getShortExchange(), spread.getCurrencyPair());
        final ExchangeFee longFee = exchangeService.getExchangeFee(spread.getLongExchange(), currencyPairLongExchange, true);
        final ExchangeFee shortFee = exchangeService.getExchangeFee(spread.getShortExchange(), currencyPairShortExchange, true);

        final FeeComputation longFeeComputation = exchangeService.getExchangeMetadata(spread.getLongExchange()).getFeeComputation();
        final FeeComputation shortFeeComputation = exchangeService.getExchangeMetadata(spread.getShortExchange()).getFeeComputation();

        final BigDecimal longAmountStepSize = spread.getLongExchange().getExchangeMetaData().getCurrencyPairs()
            .getOrDefault(spread.getCurrencyPair(), NULL_CURRENCY_PAIR_METADATA).getAmountStepSize();
        final BigDecimal shortAmountStepSize =  spread.getShortExchange().getExchangeMetaData().getCurrencyPairs()
            .getOrDefault(spread.getCurrencyPair(), NULL_CURRENCY_PAIR_METADATA).getAmountStepSize();

        final int longVolumeScale = computeVolumeScale(spread.getLongExchange(), currencyPairLongExchange);
        final int shortVolumeScale = computeVolumeScale(spread.getShortExchange(), currencyPairShortExchange);

        // figure out how much to trade
        ExitTradeVolume tradeVolume;
        try {
            BigDecimal longEntryOrderVolume = getVolumeForOrder(
                spread.getLongExchange(),
                spread.getCurrencyPair(),
                activePosition.getLongTrade().getOrderId(),
                activePosition.getLongTrade().getVolume());
            BigDecimal shortEntryOrderVolume = getVolumeForOrder(
                spread.getShortExchange(),
                spread.getCurrencyPair(),
                activePosition.getShortTrade().getOrderId(),
                activePosition.getShortTrade().getVolume());

            tradeVolume = TradeVolume.getExitTradeVolume(longFeeComputation, shortFeeComputation, longEntryOrderVolume, shortEntryOrderVolume, longFee, shortFee, longVolumeScale, shortVolumeScale);
        } catch (OrderNotFoundException e) {
            LOGGER.error(e.getMessage());
            return;
        }


        LOGGER.debug("Volumes: {}/{}", tradeVolume.getLongVolume(), tradeVolume.getShortVolume());

        BigDecimal longLimitPrice;
        BigDecimal shortLimitPrice;

        // Calculate a more accurate price based on the order book rather than just multiplying the bid or ask price.
        // Sometimes there isn't enough currency available at the listed price and part of your order has to be filled
        // at a slightly worse price, which we call "slip". This is a little bit computationally expensive which is why
        // we wait until we're pretty sure we want to trade before we do it.
        try {
            longLimitPrice = getLimitPrice(spread.getLongExchange(), spread.getCurrencyPair(), tradeVolume.getLongVolume(), Order.OrderType.BID);
            shortLimitPrice = getLimitPrice(spread.getShortExchange(), spread.getCurrencyPair(), tradeVolume.getShortVolume(), Order.OrderType.ASK);
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

        LOGGER.debug("Exit spread: {}", spreadVerification);
        LOGGER.debug("Exit spread target: {}", activePosition.getExitTarget());

        if (tradeVolume.getLongVolume().compareTo(BigDecimal.ZERO) <= 0 || tradeVolume.getShortVolume().compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.error("Computed trade volume for exiting position was zero or less than zero!");
            return;
        }

        final boolean isForceCloseCondition = conditionService.isForceCloseCondition();
        if (!isActivePositionExpired() && !isForceCloseCondition && spreadVerification.compareTo(activePosition.getExitTarget()) > 0) {
            LOGGER.debug("Not enough liquidity to execute both trades profitably!");
            return;
        }

        // If we are being forced to exit or the timeout has elapsed, but the spread is still high enough that
        // we could re-enter this position, then don't exit.
        //
        // Also, don't spam the logs with this warning. It's possible that this condition could last for awhile
        // and this code could be executed frequently.
        final BigDecimal entrySpreadTarget = spreadService.getEntrySpreadTarget(tradingConfiguration, longFee, shortFee);
        if (isActivePositionExpired() && spreadVerification.compareTo(entrySpreadTarget) < 0) {
            if (!timeoutExitWarning) {
                LOGGER.warn("Timeout exit triggered");
                LOGGER.warn("Cannot exit now because spread would cause immediate reentry");
                timeoutExitWarning = true;
            }
            return;
        }

        //Adjust order volumes so they match the fee computation, step size and scales of the exchanges
        try {
            tradeVolume.adjustOrderVolume(longExchangeName, shortExchangeName, longAmountStepSize, shortAmountStepSize);
        } catch(IllegalArgumentException e) {
            LOGGER.error("Cannot adjust order volumes, exiting trade.");
            return;
        }

        logExitTrade(spread, longExchangeName, shortExchangeName, tradeVolume, longFeeComputation, shortFeeComputation, longLimitPrice, shortLimitPrice, isForceCloseCondition);

        try {
            executeOrderPair(
                spread.getLongExchange(), spread.getShortExchange(),
                spread.getCurrencyPair(),
                longLimitPrice, shortLimitPrice,
                tradeVolume.getLongOrderVolume(), tradeVolume.getShortOrderVolume(),
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
            .withShortAmount(tradeVolume.getShortVolume().multiply(spread.getShortTicker().getAsk()))
            .withLongExchange(longExchangeName)
            .withLongCurrency(spread.getCurrencyPair().toString())
            .withLongSpread(longLimitPrice)
            .withLongSlip(longLimitPrice.subtract(spread.getLongTicker().getBid()))
            .withLongAmount(tradeVolume.getLongVolume().multiply(spread.getLongTicker().getBid()))
            .withProfit(profit)
            .withTimestamp(OffsetDateTime.now())
            .build();

        persistArbitrageToCsvFile(arbitrageLog);

        // Email notification must be sent before we set activePosition = null
        notificationService.sendExitTradeNotification(spread, tradeVolume, longLimitPrice,
            shortLimitPrice, activePosition.getEntryBalance(), updatedBalance, activePosition.getExitTarget(), isForceCloseCondition, isActivePositionExpired());

        activePosition = null;

        Utils.deleteStateFile();

        if (isForceCloseCondition) {
            conditionService.clearForceCloseCondition();
        }
    }

    // convenience method to encapsulate logging an exit
    private void logExitTrade(Spread spread, String longExchangeName, String shortExchangeName, TradeVolume tradeVolume, FeeComputation longFeeComputation, FeeComputation shortFeeComputation, BigDecimal longLimitPrice, BigDecimal shortLimitPrice, boolean isForcedCloseCondition) {

        if (isActivePositionExpired()) {
            LOGGER.warn("***** TIMEOUT EXIT *****");
            timeoutExitWarning = false;
        } else if (isForcedCloseCondition) {
            LOGGER.warn("***** FORCED EXIT *****");
        } else {
            LOGGER.info("***** EXIT *****");
        }

        LOGGER.info("Exit spread: {}", spread.getOut());
        LOGGER.info("Exit spread target: {}", activePosition.getExitTarget());
        if(longFeeComputation == FeeComputation.SERVER) {
            LOGGER.info("Long close: {} {} {} @ {} (slipped from {}) = {}{} with {}{} estimated extra fees",
                longExchangeName,
                spread.getCurrencyPair(),
                tradeVolume.getLongOrderVolume(),
                longLimitPrice,
                spread.getLongTicker().getAsk().toPlainString(),
                spread.getCurrencyPair().counter.getSymbol(),
                tradeVolume.getLongOrderVolume().multiply(longLimitPrice).toPlainString(),
                spread.getCurrencyPair().counter.getSymbol(),
                tradeVolume.getLongFee().multiply(tradeVolume.getLongOrderVolume().multiply(longLimitPrice)).setScale(USD_SCALE, RoundingMode.HALF_EVEN));
        } else {
            LOGGER.info("Long close: {} {} {} @ {} (slipped from {}) = {}{}, including {}{} estimated fees",
                longExchangeName,
                spread.getCurrencyPair(),
                tradeVolume.getLongOrderVolume(),
                longLimitPrice,
                spread.getLongTicker().getAsk().toPlainString(),
                spread.getCurrencyPair().counter.getSymbol(),
                tradeVolume.getLongOrderVolume().multiply(longLimitPrice).toPlainString(),
                spread.getCurrencyPair().base.getSymbol(),
                tradeVolume.getLongOrderVolume().subtract(tradeVolume.getLongVolume()).abs());
        }
        if(shortFeeComputation == FeeComputation.SERVER) {
            LOGGER.info("Short close: {} {} {} @ {} (slipped from {}) = {}{} with {}{} estimated extra fees",
                shortExchangeName,
                spread.getCurrencyPair(),
                tradeVolume.getShortOrderVolume(),
                shortLimitPrice,
                spread.getShortTicker().getBid().toPlainString(),
                spread.getCurrencyPair().counter.getSymbol(),
                tradeVolume.getShortOrderVolume().multiply(shortLimitPrice).toPlainString(),
                spread.getCurrencyPair().counter.getSymbol(),
                tradeVolume.getShortFee().multiply(tradeVolume.getShortOrderVolume().multiply(shortLimitPrice)).setScale(USD_SCALE, RoundingMode.HALF_EVEN));
        } else {
            LOGGER.info("Short close: {} {} {} @ {} (slipped from {}) = {}{}, including {}{} estimated fees.",
                shortExchangeName,
                spread.getCurrencyPair(),
                tradeVolume.getShortOrderVolume(),
                shortLimitPrice,
                spread.getShortTicker().getBid().toPlainString(),
                spread.getCurrencyPair().counter.getSymbol(),
                tradeVolume.getShortOrderVolume().multiply(shortLimitPrice).toPlainString(),
                spread.getCurrencyPair().base.getSymbol(),
                tradeVolume.getShortOrderVolume().subtract(tradeVolume.getShortVolume()).abs());
        }
    }

    // convenience method to encapsulate logging an entry
    private void logEntryTrade(Spread spread, String shortExchangeName, String longExchangeName, BigDecimal exitTarget,
                               EntryTradeVolume tradeVolume, FeeComputation longFeeComputation, FeeComputation shortFeeComputation, BigDecimal longLimitPrice, BigDecimal shortLimitPrice, boolean isForcedEntry) {

        if (isForcedEntry) {
            LOGGER.warn("***** FORCED ENTRY *****");
        } else {
            LOGGER.info("***** ENTRY *****");
        }

        LOGGER.info("Entry spread: {}", spread.getIn());
        LOGGER.info("Exit spread target: {}", exitTarget);
        LOGGER.info("Market neutrality rating: {}", tradeVolume.getMarketNeutralityRating().setScale(3, RoundingMode.HALF_EVEN));
        LOGGER.info("Minimum profit estimation: {}{}", spread.getCurrencyPair().counter.getSymbol(), tradeVolume.getMinimumProfit(longLimitPrice, shortLimitPrice));
        if(longFeeComputation == FeeComputation.SERVER) {
            LOGGER.info("Long entry: {} {} {} @ {} (slipped from {}) = {}{} with {}{} estimated extra fees",
                longExchangeName,
                spread.getCurrencyPair(),
                tradeVolume.getLongOrderVolume(),
                longLimitPrice,
                spread.getLongTicker().getAsk().toPlainString(),
                spread.getCurrencyPair().counter.getSymbol(),
                tradeVolume.getLongOrderVolume().multiply(longLimitPrice).toPlainString(),
                spread.getCurrencyPair().counter.getSymbol(),
                tradeVolume.getLongFee().multiply(tradeVolume.getLongOrderVolume().multiply(longLimitPrice)).setScale(USD_SCALE, RoundingMode.HALF_EVEN));
        } else {
            LOGGER.info("Long entry: {} {} {} @ {} (slipped from {}) = {}{}, including {}{} estimated fees",
                longExchangeName,
                spread.getCurrencyPair(),
                tradeVolume.getLongOrderVolume(),
                longLimitPrice,
                spread.getLongTicker().getAsk().toPlainString(),
                spread.getCurrencyPair().counter.getSymbol(),
                tradeVolume.getLongOrderVolume().multiply(longLimitPrice).toPlainString(),
                spread.getCurrencyPair().base.getSymbol(),
                tradeVolume.getLongOrderVolume().subtract(tradeVolume.getLongVolume()).abs());
        }
        if(shortFeeComputation == FeeComputation.SERVER) {
            LOGGER.info("Short entry: {} {} {} @ {} (slipped from {}) = {}{} with {}{} estimated extra fees",
                shortExchangeName,
                spread.getCurrencyPair(),
                tradeVolume.getShortOrderVolume(),
                shortLimitPrice,
                spread.getShortTicker().getBid().toPlainString(),
                spread.getCurrencyPair().counter.getSymbol(),
                tradeVolume.getShortOrderVolume().multiply(shortLimitPrice).toPlainString(),
                spread.getCurrencyPair().counter.getSymbol(),
                tradeVolume.getShortFee().multiply(tradeVolume.getShortOrderVolume().multiply(shortLimitPrice)).setScale(USD_SCALE, RoundingMode.HALF_EVEN));
        } else {
            LOGGER.info("Short entry: {} {} {} @ {} (slipped from {}) = {}{}, including {}{} estimated fees.",
                shortExchangeName,
                spread.getCurrencyPair(),
                tradeVolume.getShortOrderVolume(),
                shortLimitPrice,
                spread.getShortTicker().getBid().toPlainString(),
                spread.getCurrencyPair().counter.getSymbol(),
                tradeVolume.getShortOrderVolume().multiply(shortLimitPrice).toPlainString(),
                spread.getCurrencyPair().base.getSymbol(),
                tradeVolume.getShortOrderVolume().subtract(tradeVolume.getShortVolume()).abs());
        }
    }

    // get the smallest possible order for an entry position on an exchange
    private BigDecimal getMinimumAmountForEntryPosition(Spread spread, Exchange longExchange) {
        final BigDecimal defaultValue = new BigDecimal("0.001"); // TODO too big?
        final CurrencyPairMetaData currencyPairMetaData = longExchange
            .getExchangeMetaData()
            .getCurrencyPairs()
            .getOrDefault(exchangeService.convertExchangePair(longExchange, spread.getCurrencyPair()), NULL_CURRENCY_PAIR_METADATA);

        if (currencyPairMetaData == null || currencyPairMetaData.getMinimumAmount() == null) {
            return defaultValue;
        }

        return currencyPairMetaData.getMinimumAmount();
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

        final Observable<OpenOrders> shorOpenOrdersObservable = checkForOpenOrders(shortExchange);
        final Observable<OpenOrders> longOpenOrdersObservable = checkForOpenOrders(longExchange);


        Observable
            .zip(shorOpenOrdersObservable, longOpenOrdersObservable, (shortResult, longResult) -> shortResult.getOpenOrders().isEmpty() && longResult.getOpenOrders().isEmpty())
            .subscribe(aBoolean -> {
                // aBoolean here will be true because both openOrder list should be empty
                // So we negate the value of aBoolean 
                openOrdersFlag.set(!aBoolean);

                // invalidate the balance cache because we *know* it's incorrect now
                exchangeBalanceCache.invalidate(longExchange, shortExchange);

                // yay!
                LOGGER.info("Trades executed successfully!");
            });
    }

    private Observable<OpenOrders> checkForOpenOrders(final Exchange exchange) {
        return Observable.fromCallable(() -> fetchOpenOrders(exchange).orElseThrow(Exception::new))
            .retryWhen(throwableFlowable -> throwableFlowable.flatMap(throwable -> Observable.timer(3, TimeUnit.SECONDS)))
            .repeatWhen(objectObservable -> objectObservable.delay(3, TimeUnit.SECONDS))
            .takeUntil(openOrders -> {
                LOGGER.warn(collectOpenOrders(exchange, openOrders));
                return openOrders.getOpenOrders().isEmpty();
            })
            .subscribeOn(Schedulers.io());
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
    private Optional<OpenOrders> fetchOpenOrders(Exchange exchange) throws IOException {
        return Optional.of(exchange.getTradeService().getOpenOrders());
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
        // first, try to fetch the order from the cache
        // next, fetch from the exchange by its ID and just return its volume
        // not supported by all exchanges, so then we have to fall back to alternative methods
        LOGGER.debug("{}: Attempting to fetch volume from cache: {}", exchange.getExchangeSpecification().getExchangeName(), orderId);
        BigDecimal volume = orderVolumeCache.getCachedVolume(exchange, orderId)
            .orElseGet(() -> {
                LOGGER.debug("{}: Attempting to fetch volume from order by ID: {}", exchange.getExchangeSpecification().getExchangeName(), orderId);
                try {
                    return Optional.ofNullable(exchange.getTradeService().getOrder(orderId))
                        .orElseThrow(() -> new NotAvailableFromExchangeException(orderId))
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new OrderNotFoundException(exchange, orderId))
                        .getOriginalAmount();
                } catch (NotAvailableFromExchangeException e) {
                    LOGGER.debug("{}: Does not support fetching orders by ID", exchange.getExchangeSpecification().getExchangeName());
                } catch (IllegalStateException | IOException e) {
                    LOGGER.warn("{}: Unable to fetch order {}", exchange.getExchangeSpecification().getExchangeName(), orderId, e);
                }

                return BigDecimal.ZERO;
            });

        // if we got a greater-than-zero volume, we're done
        // cache it and return it
        if (volume != null && BigDecimal.ZERO.compareTo(volume) < 0) {
            orderVolumeCache.setCachedVolume(exchange, orderId, volume);
            return volume;
        }

        // we couldn't get an order volume by ID, so next we try to get the account balance
        // for the BASE pair (eg. the BTC in BTC/USD)
        try {
            final Integer scale = exchange.getExchangeMetaData()
                .getCurrencies()
                .getOrDefault(currencyPair.base, new CurrencyMetaData(BTC_SCALE, BigDecimal.ZERO))
                .getScale();

            BigDecimal balance = exchangeService.getAccountBalance(exchange, currencyPair.base, scale);

            if (BigDecimal.ZERO.compareTo(balance) < 0) {
                LOGGER.debug("{}: Using {} balance: {}",
                    exchange.getExchangeSpecification().getExchangeName(),
                    currencyPair.base.toString(),
                    balance);

                orderVolumeCache.setCachedVolume(exchange, orderId, balance);
                return balance;
            }
        } catch (IOException e) {
            LOGGER.warn("{}: Unable to fetch {} account balance",
                exchange.getExchangeSpecification().getExchangeName(),
                currencyPair.base.toString(),
                e);
        }

        // finally, give up and return the "default" value
        LOGGER.debug("{}: Falling back to default volume: {}",
            exchange.getExchangeSpecification().getExchangeName(),
            defaultVolume);

        orderVolumeCache.setCachedVolume(exchange, orderId, defaultVolume);
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

            // Walk through orders, ordered by price, until we satisfy all the volume we need.
            // Return the price of the last order we see.
            //
            // If we set our limit order at this price (without waiting too long) it is very likely to fill
            // because we know the exchange has enough currency available to fill it at this or a better price.
            for (LimitOrder order : orders) {
                price = order.getLimitPrice();
                volume = volume.add(order.getRemainingAmount());

                if (volume.compareTo(allowedVolume) > 0) {
                    int scale = computePriceScale(exchange, currencyPair);

                    return price.setScale(scale, RoundingMode.HALF_EVEN);
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
                .map(exchange -> exchangeBalanceCache.getCachedBalance(exchange) // try the cache first
                    .orElseGet(() -> {
                        try {
                            final Currency homeCurrency = exchangeService.getExchangeHomeCurrency(exchange);
                            final int homeCurrencyScale = exchangeService.getExchangeCurrencyScale(exchange, homeCurrency);
                            final BigDecimal balance = exchangeService.getAccountBalance(exchange, homeCurrency, homeCurrencyScale); // then make the API call

                            exchangeBalanceCache.setCachedBalance(exchange, balance); // cache the returned value

                            return balance;
                        } catch (IOException e) {
                            LOGGER.info("IOException fetching {} account balance", exchange.getExchangeSpecification().getExchangeName());

                            // set the cache to zero so we don't keep spamming the API when there's an IOException
                            // we may have gotten the IOE because of rate limiting
                            // this cache entry will only last a short time
                            // but it will make us back off awhile before trying again
                            exchangeBalanceCache.setCachedBalance(exchange, BigDecimal.ZERO);
                        }

                        return BigDecimal.ZERO; // just return a zero balance if we couldn't get anything
                    }))
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
    private BigDecimal logCurrentExchangeBalances(final Exchange longExchange, final Exchange shortExchange) {
        try {
            final Currency longHomeCurrency = exchangeService.getExchangeHomeCurrency(longExchange);
            final Currency shortHomeCurrency = exchangeService.getExchangeHomeCurrency(shortExchange);
            final int longHomeCurrencyScale = exchangeService.getExchangeCurrencyScale(longExchange, longHomeCurrency);
            final int shortHomeCurrencyScale = exchangeService.getExchangeCurrencyScale(shortExchange, shortHomeCurrency);
            final BigDecimal longBalance = exchangeService.getAccountBalance(longExchange, longHomeCurrency, longHomeCurrencyScale);
            final BigDecimal shortBalance = exchangeService.getAccountBalance(shortExchange, shortHomeCurrency, shortHomeCurrencyScale);
            final BigDecimal totalBalance = longBalance.add(shortBalance);

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



    // determine whether a trade has exceeded the configured trade timeout
    private boolean isActivePositionExpired() {
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
