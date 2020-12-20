package com.r307.arbitrader.service.model;

import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbasepro.dto.account.CoinbaseProAccount;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import si.mazi.rescu.SynchronizedValueFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PaperExchange implements Exchange {

    Exchange exchange;

    PaperTradeService tradeService;
    PaperAccountService accountService;

    public PaperExchange(Exchange exchange, Currency homeCurrency) {
        this.exchange=exchange;
        this.tradeService=new PaperTradeService(this, exchange.getTradeService());
        this.accountService=new PaperAccountService(exchange.getAccountService(),homeCurrency);
    }

    @Override
    public ExchangeSpecification getExchangeSpecification() {
        return exchange.getExchangeSpecification();
    }

    @Override
    public ExchangeMetaData getExchangeMetaData() {
        return exchange.getExchangeMetaData();
    }

    @Override
    public List<CurrencyPair> getExchangeSymbols() {
        return exchange.getExchangeSymbols();
    }

    @Override
    public SynchronizedValueFactory<Long> getNonceFactory() {
        return exchange.getNonceFactory();
    }

    @Override
    public ExchangeSpecification getDefaultExchangeSpecification() {
        return exchange.getDefaultExchangeSpecification();
    }

    @Override
    public void applySpecification(ExchangeSpecification exchangeSpecification) {
        exchange.applySpecification(exchangeSpecification);
    }

    @Override
    public MarketDataService getMarketDataService() {
        return exchange.getMarketDataService();
    }

    @Override
    public TradeService getTradeService() {
        return tradeService;
    }

    @Override
    public AccountService getAccountService() {
        return accountService;
    }

    @Override
    public void remoteInit() throws IOException, ExchangeException {
        exchange.remoteInit();
    }
}
