package com.r307.arbitrader.service;

import com.r307.arbitrader.config.ExchangeConfiguration;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.stereotype.Component;

import static com.r307.arbitrader.service.TradingService.METADATA_KEY;

@Component
public class ExchangeService {
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
}
