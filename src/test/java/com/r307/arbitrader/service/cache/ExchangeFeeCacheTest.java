package com.r307.arbitrader.service.cache;

import com.r307.arbitrader.BaseTestCase;
import com.r307.arbitrader.service.model.ExchangeFee;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
        exchangeFeeCache.setCachedFee(exchange, currencyPair, new ExchangeFee(new BigDecimal("0.0025"), null));
        exchangeFeeCache.setCachedFee(exchange, CurrencyPair.BTC_USD, new ExchangeFee(new BigDecimal("0.0010"), new BigDecimal("0.0002")));
        exchangeFeeCache.setCachedFee(exchange, CurrencyPair.ETH_USD, new ExchangeFee(new BigDecimal("0.0030"), new BigDecimal("0.0001")));
        exchangeFeeCache.setCachedFee(exchange, CurrencyPair.BCC_USD, new ExchangeFee(new BigDecimal("0.0030"), null));

        assertTrue(exchangeFeeCache.getCachedFee(exchange, currencyPair).isPresent());
        assertEquals(new BigDecimal("0.0025"), exchangeFeeCache.getCachedFee(exchange, currencyPair).get().getTradeFee());
        assertFalse(exchangeFeeCache.getCachedFee(exchange, currencyPair).get().getMarginFee().isPresent());
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
