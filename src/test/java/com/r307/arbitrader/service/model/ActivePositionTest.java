package com.r307.arbitrader.service.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r307.arbitrader.ExchangeBuilder;
import com.r307.arbitrader.config.JsonConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.r307.arbitrader.DecimalConstants.USD_SCALE;
import static org.junit.Assert.assertEquals;

public class ActivePositionTest {
    private ObjectMapper objectMapper;
    private CurrencyPair currencyPair = CurrencyPair.BTC_USD;
    private BigDecimal exitTarget = new BigDecimal("0.003");
    private Exchange longExchange = null;
    private Exchange shortExchange = null;
    private BigDecimal longVolume = new BigDecimal("1000.00");
    private BigDecimal shortVolume = new BigDecimal("1000.00");
    private BigDecimal longLimitPrice = new BigDecimal("5000.00");
    private BigDecimal shortLimitPrice = new BigDecimal("5000.00");

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        objectMapper = new JsonConfiguration().objectMapper();

        longExchange = new ExchangeBuilder("Long", CurrencyPair.BTC_USD)
            .withTradeService()
            .withOrderBook(100, 100)
            .withBalance(Currency.USD, new BigDecimal("100.00").setScale(USD_SCALE, RoundingMode.HALF_EVEN))
            .build();
        shortExchange = new ExchangeBuilder("Short", CurrencyPair.BTC_USD)
            .withBalance(Currency.USD, new BigDecimal("500.00").setScale(USD_SCALE, RoundingMode.HALF_EVEN))
            .build();
    }

    @Test
    public void testSerialization() throws IOException{
        ActivePosition activePosition = new ActivePosition();

        activePosition.setEntryTime(OffsetDateTime.now());
        activePosition.setEntryBalance(new BigDecimal("1337.00"));
        activePosition.setCurrencyPair(currencyPair);
        activePosition.setExitTarget(exitTarget);
        activePosition.getLongTrade().setOrderId(UUID.randomUUID().toString());
        activePosition.getLongTrade().setExchange(longExchange);
        activePosition.getLongTrade().setVolume(longVolume);
        activePosition.getLongTrade().setEntry(longLimitPrice);
        activePosition.getShortTrade().setOrderId(UUID.randomUUID().toString());
        activePosition.getShortTrade().setExchange(shortExchange);
        activePosition.getShortTrade().setVolume(shortVolume);
        activePosition.getShortTrade().setEntry(shortLimitPrice);

        String json = objectMapper.writeValueAsString(activePosition);

        ActivePosition deserialized = objectMapper.readValue(json.getBytes(StandardCharsets.UTF_8), ActivePosition.class);

        assertEquals(activePosition, deserialized);
    }
}
