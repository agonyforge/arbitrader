package com.r307.arbitrader.service;

import com.r307.arbitrader.Utils;
import com.r307.arbitrader.config.ExchangeConfiguration;
import com.r307.arbitrader.service.cache.ExchangeFeeCache;
import com.r307.arbitrader.service.model.ExchangeFee;
import com.r307.arbitrader.service.ticker.TickerStrategy;
import com.r307.arbitrader.service.ticker.TickerStrategyProvider;
import org.jetbrains.annotations.Nullable;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.params.CurrencyPairsParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;

/**
 * Services related to exchanges.
 */
@Component
public class ExchangeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExchangeService.class);
    private static final CurrencyMetaData DEFAULT_CURRENCY_METADATA = new CurrencyMetaData(BTC_SCALE, BigDecimal.ZERO);

    public static final String METADATA_KEY = "arbitrader-metadata";
    public static final String TICKER_STRATEGY_KEY = "tickerStrategy";

    private final ExchangeFeeCache feeCache;
    private final TickerStrategyProvider tickerStrategyProvider;

    @Inject
    public ExchangeService(ExchangeFeeCache feeCache, TickerStrategyProvider tickerStrategyProvider) {
        this.feeCache = feeCache;
        this.tickerStrategyProvider = tickerStrategyProvider;
    }

    /**
     * Convenience method for accessing our ExchangeConfiguration object inside the Exchange.
     *
     * @param exchange The Exchange to pull information from.
     * @return The ExchangeConfiguration object for the Exchange.
     */
    public ExchangeConfiguration getExchangeMetadata(Exchange exchange) {
        return (ExchangeConfiguration) exchange.getExchangeSpecification().getExchangeSpecificParametersItem(METADATA_KEY);
    }

    /**
     * Convenience method for getting the configured home currency for an Exchange.
     *
     * @param exchange The Exchange to pull information from.
     * @return The home currency for the Exchange.
     */
    public Currency getExchangeHomeCurrency(Exchange exchange) {
        return getExchangeMetadata(exchange).getHomeCurrency();
    }

    /**
     * Convenience method for getting the scale for a Currency.
     *
     * @param exchange The Exchange to get information from.
     * @param currency The Currency to get the scale for.
     * @return The scale for the given Currency on the given Exchange.
     */
    public int getExchangeCurrencyScale(Exchange exchange, Currency currency) {
        return exchange.getExchangeMetaData().getCurrencies().getOrDefault(currency, DEFAULT_CURRENCY_METADATA).getScale();
    }

    /**
     * This is a kind of hacky workaround for the fact that Arbitrader was written assuming everyone would have USD
     * as their "main" currency. If you have configured a different "home currency" for an exchange, such as USDC or
     * EUR, this method translates USD into that currency instead and smooths over the USD assumptions we have made.
     *
     * This is OK for the time being but the places where USD is hard coded should eventually be replaced by a
     * configuration that lets you set a global default fiat currency.
     *
     * @param exchange An Exchange.
     * @param currencyPair A CurrencyPair.
     * @return A new CurrencyPair where USD has been replaced with the home currency for that Exchange.
     */
    public CurrencyPair convertExchangePair(Exchange exchange, CurrencyPair currencyPair) {
        if (Currency.USD == currencyPair.base) {
            return new CurrencyPair(getExchangeHomeCurrency(exchange), currencyPair.counter);
        } else if (Currency.USD == currencyPair.counter) {
            return new CurrencyPair(currencyPair.base, getExchangeHomeCurrency(exchange));
        }

        return currencyPair;
    }

    /**
     * Perform the initial setup for an Exchange.
     *
     * @param exchange The Exchange to setup.
     */
    public void setUpExchange(Exchange exchange) {
        try {
            if (!Utils.stateFileExists()) {
                final Set<String> cryptoCoinsFromTradingPairs = getCryptoCoinsFromTradingPairs(exchange);
                if (!cryptoCoinsFromTradingPairs.isEmpty()) {
                    cryptoCoinsFromTradingPairs.forEach(s -> LOGGER.error("Exchange {} is configured to trade with {} but the wallet for this coin is not empty! " +
                        "As a safety measure this is not allowed. You can either sell your coins or remove this coin from any trading pair for this exchange",
                        exchange.getExchangeSpecification().getExchangeName(), s)
                    );

                    throw new RuntimeException("All coins in the trading pair must have an empty wallet");
                }
            }
            LOGGER.debug("{} SSL URI: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                exchange.getExchangeSpecification().getSslUri());
            LOGGER.debug("{} SSL host: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                exchange.getExchangeSpecification().getHost());
            LOGGER.debug("{} SSL port: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                exchange.getExchangeSpecification().getPort());
            LOGGER.debug("{} home currency: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                getExchangeHomeCurrency(exchange));

            Currency homeCurrency = getExchangeHomeCurrency(exchange);

            LOGGER.info("{} balance: {}{}",
                exchange.getExchangeSpecification().getExchangeName(),
                homeCurrency.getSymbol(),
                getAccountBalance(exchange, homeCurrency, getExchangeCurrencyScale(exchange, homeCurrency)));
        } catch (IOException e) {
            LOGGER.error("Unable to fetch account balance: ", e);
        }

        // choose a TickerStrategy for the exchange
        if (Utils.isStreamingExchange(exchange)) {
            // streaming exchanges all use the StreamingTickerStrategy
            final TickerStrategy streamingTickerStrategy = tickerStrategyProvider.getStreamingTickerStrategy(this);

            exchange.getExchangeSpecification().setExchangeSpecificParametersItem(TICKER_STRATEGY_KEY, streamingTickerStrategy);
        } else {
            try {
                // attempt to fetch multiple tickers in one call from the exchange
                // if this works, we can use the single call strategy to fetch all tickers in one API call
                CurrencyPairsParam param = () -> getExchangeMetadata(exchange).getTradingPairs().subList(0, 1);
                exchange.getMarketDataService().getTickers(param);

                final TickerStrategy singleCallTickerStrategy = tickerStrategyProvider.getSingleCallTickerStrategy(this);

                exchange.getExchangeSpecification().setExchangeSpecificParametersItem(TICKER_STRATEGY_KEY, singleCallTickerStrategy);
            } catch (NotYetImplementedForExchangeException e) {
                // If we can't fetch all the tickers in one call, we need to fetch each ticker in its own API call.
                // So we fall back to the parallel ticker strategy which can unfortunately result in rate limiting
                // on some exchanges.
                LOGGER.warn("{} does not support fetching multiple tickers at a time and will fetch tickers " +
                        "individually instead. This may result in API rate limiting.",
                    exchange.getExchangeSpecification().getExchangeName());

                final TickerStrategy parallelTickerStrategy = tickerStrategyProvider.getParallelTickerStrategy(this);

                exchange.getExchangeSpecification().setExchangeSpecificParametersItem(TICKER_STRATEGY_KEY, parallelTickerStrategy);
            } catch (IOException e) {
                LOGGER.debug("IOException fetching tickers for {}: ", exchange.getExchangeSpecification().getExchangeName(), e);
            }
        }

        ExchangeFee tradingFee = getExchangeFee(exchange, convertExchangePair(exchange, CurrencyPair.BTC_USD), false);

        LOGGER.info("{} ticker strategy: {}",
            exchange.getExchangeSpecification().getExchangeName(),
            exchange.getExchangeSpecification().getExchangeSpecificParametersItem(TICKER_STRATEGY_KEY));

        if (tradingFee.getMarginFee().isPresent()) {
            LOGGER.info("{} {} trading fee: {} and margin fee: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                convertExchangePair(exchange, CurrencyPair.BTC_USD),
                tradingFee.getTradeFee(),
                tradingFee.getMarginFee().get().add(tradingFee.getTradeFee()));
        }
        else {
            LOGGER.info("{} {} trading fee: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                convertExchangePair(exchange, CurrencyPair.BTC_USD),
                tradingFee.getTradeFee());
        }

    }

    /**
     * Get the account balance in a specific currency from an exchange with a specific scale.
     *
     * @param exchange The Exchange to query.
     * @param currency The Currency to query.
     * @param scale The scale of the result.
     * @return The balance for the given currency on the given exchange with the given scale.
     * @throws IOException when we can't talk to the exchange.
     */
    public BigDecimal getAccountBalance(Exchange exchange, Currency currency, int scale) throws IOException {
        AccountService accountService = exchange.getAccountService();

        // walk through all wallets on the exchange
        for (Wallet wallet : accountService.getAccountInfo().getWallets().values()) {
            // find the one with the correct currency
            if (wallet.getBalances().containsKey(currency)) {
                // return the amount available in the wallet scaled to the requested scale
                return wallet.getBalance(currency).getAvailable()
                    .setScale(scale, RoundingMode.HALF_EVEN);
            }
        }

        LOGGER.error("{}: Unable to fetch {} balance",
            exchange.getExchangeSpecification().getExchangeName(),
            currency.getCurrencyCode());

        return BigDecimal.ZERO;
    }

    /**
     * By using the configured trading pairs for the given exchange, get the list of crypto currencies where the wallet
     * is not empty. The {@link Exchange} home currency is not taken in consideration, it is ignored.
     * @param exchange
     * @return A {@link Set} containing the currency code
     * @throws IOException
     */
    public Set<String> getCryptoCoinsFromTradingPairs(Exchange exchange) throws IOException {
        final List<CurrencyPair> tradingPairs = getExchangeMetadata(exchange).getTradingPairs();

        // Get all account currencies where the wallet balance is not empty
        final Set<String> accountCurrencies = exchange.getAccountService()
            .getAccountInfo()
            .getWallets()
            .values()
            .stream()
            .flatMap(wallet -> wallet.getBalances().entrySet().stream())
            .filter(currencyBalanceEntry -> currencyBalanceEntry.getKey() != getExchangeMetadata(exchange).getHomeCurrency())
            .filter(currencyBalanceEntry -> currencyBalanceEntry.getValue().getAvailable().compareTo(BigDecimal.ZERO) > 0)
            .map(currencyBalanceEntry -> currencyBalanceEntry.getKey().getCurrencyCode())
            .collect(Collectors.toSet());

        return tradingPairs.stream()
            .filter(currencyPair -> accountCurrencies.contains(currencyPair.base.getCurrencyCode()))
            .map(currencyPair -> currencyPair.base.getCurrencyCode())
            .collect(Collectors.toSet());
    }

    /**
     * Get the fee for using an exchange.
     *
     * @param exchange The Exchange to query.
     * @param currencyPair The CurrencyPair, in case fees vary by pair.
     * @param isQuiet true if we should suppress error messages that could get annoying if they are too frequent.
     * @return The fee expressed as a percentage, ie. 0.0016 for 0.16%
     */
    public ExchangeFee getExchangeFee(Exchange exchange, CurrencyPair currencyPair, boolean isQuiet) {
        Optional<ExchangeFee> cachedFee = feeCache.getCachedFee(exchange, currencyPair);

        // we cache fees because they don't change frequently, but we use them frequently and making API calls is expensive
        if (cachedFee.isPresent()) {
            return cachedFee.get();
        }

        final ExchangeConfiguration exchangeMetadata = getExchangeMetadata(exchange);

        // Get the margin fee configured for this exchange
        final BigDecimal marginFee = getMarginFee(exchangeMetadata);

        // if feeOverride is configured, just use that
        if (exchangeMetadata.getTradeFeeOverride() != null) {
            final BigDecimal tradeFee = exchangeMetadata.getTradeFeeOverride();
            final ExchangeFee exchangeFee = new ExchangeFee(tradeFee, marginFee);

            feeCache.setCachedFee(exchange, currencyPair, exchangeFee);

            LOGGER.trace("Using explicitly configured margin fee override of {} for {}",
                marginFee,
                exchange.getExchangeSpecification().getExchangeName());

            return exchangeFee;
        }

        try {
            // try to get dynamic trading fees from the exchange, if it's implemented
            Map<CurrencyPair, Fee> fees = exchange.getAccountService().getDynamicTradingFees();

            if (fees.containsKey(currencyPair)) {
                final BigDecimal tradeFee = fees.get(currencyPair).getMakerFee();

                final ExchangeFee exchangeFee = new ExchangeFee(tradeFee, marginFee);

                // We're going to cache this value. Fees don't change all that often and we don't want to use up
                // our allowance of API calls just checking the fees.
                feeCache.setCachedFee(exchange, currencyPair, exchangeFee);

                LOGGER.trace("Using dynamic maker fee for {}",
                    exchange.getExchangeSpecification().getExchangeName());

                return exchangeFee;
            }
        } catch (NotYetImplementedForExchangeException e) {
            LOGGER.trace("Dynamic fees not yet implemented for {}, will try other methods",
                exchange.getExchangeSpecification().getExchangeName());
        } catch (IOException e) {
            LOGGER.trace("IOE fetching dynamic trading fees for {}",
                exchange.getExchangeSpecification().getExchangeName());
        } catch (Exception e) {
            LOGGER.warn("Programming error in XChange! {} when calling getDynamicTradingFees() for exchange: {}",
                e.getClass().getName(),
                exchange.getExchangeSpecification().getExchangeName());
        }

        // try to get fees from the exchange metadata
        final CurrencyPairMetaData currencyPairMetaData = exchange.getExchangeMetaData().getCurrencyPairs().get(convertExchangePair(exchange, currencyPair));

        if (currencyPairMetaData == null || currencyPairMetaData.getTradingFee() == null) {
            // if no metadata, see if the user configured a fee
            BigDecimal configuredFee = exchangeMetadata.getTradeFee();

            if (configuredFee == null) {
                if (!isQuiet) {
                    LOGGER.error("{} has no fees configured. Setting default of 0.0030. Please configure the correct value!",
                        exchange.getExchangeSpecification().getExchangeName());
                }

                // give up and return a default fee - this is intentionally higher than most exchanges
                return new ExchangeFee(new BigDecimal("0.0030"), marginFee);
            }

            if (!isQuiet) {
                LOGGER.warn("{} fees unavailable via API. Will use configured value.",
                    exchange.getExchangeSpecification().getExchangeName());
            }

            return new ExchangeFee(configuredFee, marginFee);
        }

        // Last fall back - use CurrencyPairMetaData trading fee
        final ExchangeFee exchangeFee = new ExchangeFee(currencyPairMetaData.getTradingFee(), marginFee);
        feeCache.setCachedFee(exchange, currencyPair, exchangeFee);
        return exchangeFee;
    }

    @Nullable
    private BigDecimal getMarginFee(ExchangeConfiguration exchangeMetadata) {
        final BigDecimal marginFee;

        // If exchange is set for margin trade then get the configured margin fee
        if (exchangeMetadata.getMargin()) {
            if (exchangeMetadata.getMarginFeeOverride() != null) {
                marginFee = exchangeMetadata.getMarginFeeOverride();
            }
            else {
                marginFee = exchangeMetadata.getMarginFee();
            }
        }
        else {
            marginFee = null;
        }

        return marginFee;
    }

}
