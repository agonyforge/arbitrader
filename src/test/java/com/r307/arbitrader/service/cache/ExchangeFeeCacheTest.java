package com.r307.arbitrader.service.cache;

import com.r307.arbitrader.BaseTestCase;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExchangeFeeCacheTest extends BaseTestCase {
    @Mock
    private Exchange exchange;

    @Mock
    private ExchangeSpecification exchangeSpecification;

    private CurrencyPair currencyPair;
    private ExchangeFeeCache exchangeFeeCache;

    @Before
    public void setUp() {
        when(exchange.getExchangeSpecification()).thenReturn(exchangeSpecification);
        when(exchangeSpecification.getExchangeName()).thenReturn("WhatCoin");

        currencyPair = new CurrencyPair("COIN", "USD");

        exchangeFeeCache = new ExchangeFeeCache();
    }

    @Test
    public void testSetAndGet() {
        exchangeFeeCache.setCachedFee(exchange, currencyPair, new BigDecimal("0.0025"));
        exchangeFeeCache.setCachedFee(exchange, CurrencyPair.BTC_USD, new BigDecimal("0.0010"));
        exchangeFeeCache.setCachedFee(exchange, CurrencyPair.ETH_USD, new BigDecimal("0.0030"));

        assertEquals(Optional.of(new BigDecimal("0.0025")), exchangeFeeCache.getCachedFee(exchange, currencyPair));
    }

    @Test
    public void testGetUnknownPair() {
        CurrencyPair altPair = new CurrencyPair("FAKE", "USD");

        assertEquals(Optional.empty(), exchangeFeeCache.getCachedFee(exchange, altPair));
    }

    @Test
    public void testGetUnknownExchange() {
        Exchange altExchange = mock(Exchange.class);
        ExchangeSpecification altSpec = mock(ExchangeSpecification.class);

        when(altExchange.getExchangeSpecification()).thenReturn(altSpec);
        when(altSpec.getExchangeName()).thenReturn("AltEx");

        assertEquals(Optional.empty(), exchangeFeeCache.getCachedFee(altExchange, currencyPair));
    }
}
