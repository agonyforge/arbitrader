package com.r307.arbitrader.service;

import com.r307.arbitrader.Utils;
import com.r307.arbitrader.config.ExchangeConfiguration;
import com.r307.arbitrader.service.ticker.TickerStrategy;
import com.r307.arbitrader.service.ticker.TickerStrategyProvider;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.account.Wallet;
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
import java.util.Map;

import static com.r307.arbitrader.DecimalConstants.USD_SCALE;

@Component
public class ExchangeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExchangeService.class);

    public static final String METADATA_KEY = "arbitrader-metadata";
    public static final String TICKER_STRATEGY_KEY = "tickerStrategy";

    private final ExchangeFeeCache feeCache;
    private final TickerStrategyProvider tickerStrategyProvider;

    @Inject
    public ExchangeService(ExchangeFeeCache feeCache, TickerStrategyProvider tickerStrategyProvider) {
        this.feeCache = feeCache;
        this.tickerStrategyProvider = tickerStrategyProvider;
    }

    public ExchangeConfiguration getExchangeMetadata(Exchange exchange) {
        return (ExchangeConfiguration) exchange.getExchangeSpecification().getExchangeSpecificParametersItem(METADATA_KEY);
    }

    public Currency getExchangeHomeCurrency(Exchange exchange) {
        return getExchangeMetadata(exchange).getHomeCurrency();
    }

    public CurrencyPair convertExchangePair(Exchange exchange, CurrencyPair currencyPair) {
        if (Currency.USD == currencyPair.base) {
            return new CurrencyPair(getExchangeHomeCurrency(exchange), currencyPair.counter);
        } else if (Currency.USD == currencyPair.counter) {
            return new CurrencyPair(currencyPair.base, getExchangeHomeCurrency(exchange));
        }

        return currencyPair;
    }

    public void setUpExchange(Exchange exchange) {
        try {
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
            LOGGER.info("{} balance: {}{}",
                exchange.getExchangeSpecification().getExchangeName(),
                getExchangeHomeCurrency(exchange).getSymbol(),
                getAccountBalance(exchange));
        } catch (IOException e) {
            LOGGER.error("Unable to fetch account balance: ", e);
        }


        if (Utils.isStreamingExchange(exchange)) {
            final TickerStrategy streamingTickerStrategy = tickerStrategyProvider.getStreamingTickerStrategy(this);

            exchange.getExchangeSpecification().setExchangeSpecificParametersItem(TICKER_STRATEGY_KEY, streamingTickerStrategy);
        } else {
            try {
                CurrencyPairsParam param = () -> getExchangeMetadata(exchange).getTradingPairs().subList(0, 1);
                exchange.getMarketDataService().getTickers(param);

                final TickerStrategy singleCallTickerStrategy = tickerStrategyProvider.getSingleCallTickerStrategy(this);

                exchange.getExchangeSpecification().setExchangeSpecificParametersItem(TICKER_STRATEGY_KEY, singleCallTickerStrategy);
            } catch (NotYetImplementedForExchangeException e) {
                LOGGER.warn("{} does not support fetching multiple tickers at a time and will fetch tickers " +
                        "individually instead. This may result in API rate limiting.",
                    exchange.getExchangeSpecification().getExchangeName());

                final TickerStrategy parallelTickerStrategy = tickerStrategyProvider.getParallelTickerStrategy(this);

                exchange.getExchangeSpecification().setExchangeSpecificParametersItem(TICKER_STRATEGY_KEY, parallelTickerStrategy);
            } catch (IOException e) {
                LOGGER.debug("IOException fetching tickers for {}: ", exchange.getExchangeSpecification().getExchangeName(), e);
            }
        }

        BigDecimal tradingFee = getExchangeFee(exchange, convertExchangePair(exchange, CurrencyPair.BTC_USD), false);

        LOGGER.info("{} ticker strategy: {}",
            exchange.getExchangeSpecification().getExchangeName(),
            exchange.getExchangeSpecification().getExchangeSpecificParametersItem(TICKER_STRATEGY_KEY));

        LOGGER.info("{} {} trading fee: {}",
            exchange.getExchangeSpecification().getExchangeName(),
            convertExchangePair(exchange, CurrencyPair.BTC_USD),
            tradingFee);
    }


    public BigDecimal getAccountBalance(Exchange exchange, Currency currency) throws IOException {
        return getAccountBalance(exchange, currency, USD_SCALE);
    }

    public BigDecimal getAccountBalance(Exchange exchange) throws IOException {
        Currency currency = getExchangeHomeCurrency(exchange);

        return getAccountBalance(exchange, currency);
    }

    public BigDecimal getExchangeFee(Exchange exchange, CurrencyPair currencyPair, boolean isQuiet) {
        BigDecimal cachedFee = feeCache.getCachedFee(exchange, currencyPair);

        if (cachedFee != null) {
            return cachedFee;
        }

        // if an explicit override is configured, default to that
        if (getExchangeMetadata(exchange).getFeeOverride() != null) {
            BigDecimal fee = getExchangeMetadata(exchange).getFeeOverride();
            feeCache.setCachedFee(exchange, currencyPair, fee);

            LOGGER.trace("Using explicitly configured fee override of {} for {}",
                fee,
                exchange.getExchangeSpecification().getExchangeName());

            return fee;
        }

        try {
            Map<CurrencyPair, Fee> fees = exchange.getAccountService().getDynamicTradingFees();

            if (fees.containsKey(currencyPair)) {
                BigDecimal fee = fees.get(currencyPair).getMakerFee();

                // We're going to cache this value. Fees don't change all that often and we don't want to use up
                // our allowance of API calls just checking the fees.
                feeCache.setCachedFee(exchange, currencyPair, fee);

                LOGGER.trace("Using dynamic maker fee for {}",
                    exchange.getExchangeSpecification().getExchangeName());

                return fee;
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

        CurrencyPairMetaData currencyPairMetaData = exchange.getExchangeMetaData().getCurrencyPairs().get(convertExchangePair(exchange, currencyPair));

        if (currencyPairMetaData == null || currencyPairMetaData.getTradingFee() == null) {
            BigDecimal configuredFee = getExchangeMetadata(exchange).getFee();

            if (configuredFee == null) {
                if (!isQuiet) {
                    LOGGER.error("{} has no fees configured. Setting default of 0.0030. Please configure the correct value!",
                        exchange.getExchangeSpecification().getExchangeName());
                }

                return new BigDecimal("0.0030");
            }

            if (!isQuiet) {
                LOGGER.warn("{} fees unavailable via API. Will use configured value.",
                    exchange.getExchangeSpecification().getExchangeName());
            }

            return configuredFee;
        }

        return currencyPairMetaData.getTradingFee();
    }

    public BigDecimal getAccountBalance(Exchange exchange, Currency currency, int scale) throws IOException {
        AccountService accountService = exchange.getAccountService();

        for (Wallet wallet : accountService.getAccountInfo().getWallets().values()) {
            if (wallet.getBalances().containsKey(currency)) {
                return wallet.getBalance(currency).getAvailable()
                    .setScale(scale, RoundingMode.HALF_EVEN);
            }
        }

        LOGGER.error("{}: Unable to fetch {} balance",
            exchange.getExchangeSpecification().getExchangeName(),
            currency.getCurrencyCode());

        return BigDecimal.ZERO;
    }
}
