package com.r307.arbitrader.service;

import com.r307.arbitrader.ExchangeBuilder;
import com.r307.arbitrader.config.ExchangeConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ExchangeServiceTest {
    private Exchange exchange;

    private ExchangeService exchangeService;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        exchange = new ExchangeBuilder("CoinFraud", CurrencyPair.BTC_USD)
            .withHomeCurrency(Currency.USDT)
            .build();

        exchangeService = new ExchangeService();
    }

    @Test
    public void testExchangeMetadata() {
        ExchangeConfiguration configuration = exchangeService.getExchangeMetadata(exchange);

        assertNotNull(configuration);
    }

    @Test
    public void testExchangeHomeCurrency() {
        Currency homeCurrency = exchangeService.getExchangeHomeCurrency(exchange);

        assertEquals(Currency.USDT, homeCurrency);
    }

    @Test
    public void testConvertExchangePairBase() {
        CurrencyPair currencyPair = CurrencyPair.BTC_USD;
        CurrencyPair converted = exchangeService.convertExchangePair(exchange, currencyPair);

        assertEquals(CurrencyPair.BTC_USDT, converted);
    }

    @Test
    public void testConvertExchangePairCounter() {
        CurrencyPair currencyPair = new CurrencyPair("USD", "BTC");
        CurrencyPair converted = exchangeService.convertExchangePair(exchange, currencyPair);

        assertEquals(new CurrencyPair("USDT", "BTC"), converted);
    }

    @Test
    public void testConvertExchangePairNeither() {
        CurrencyPair currencyPair = CurrencyPair.DOGE_BTC;
        CurrencyPair converted = exchangeService.convertExchangePair(exchange, currencyPair);

        assertEquals(CurrencyPair.DOGE_BTC, converted);
        assertEquals(currencyPair, converted);
    }
}
