package com.r307.arbitrader.service.model;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.*;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.params.DefaultWithdrawFundsParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.WithdrawFundsParams;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PaperAccountService implements AccountService {

    AccountService accountService;

    Currency homeCurrency;

    public PaperAccountService (AccountService accountService, Currency homeCurrency) {
        this.accountService=accountService;
        this.homeCurrency=homeCurrency;
    }

    public AccountInfo getAccountInfo() {
        List<Wallet> wallets = new ArrayList<>();
        List<Balance> balances = new ArrayList<>();
        balances.add(
            new Balance(
                this.homeCurrency,
                new BigDecimal(100),
                new BigDecimal(100),
                new BigDecimal(0)));
        wallets.add(Wallet.Builder.from(balances).id(UUID.randomUUID().toString()).build());
        return new AccountInfo (wallets);
    }

    public Map<Instrument, Fee> getDynamicTradingFeesByInstrument() throws IOException {
        return accountService.getDynamicTradingFeesByInstrument();
    }

    public Map<CurrencyPair, Fee> getDynamicTradingFees() throws IOException {
        return accountService.getDynamicTradingFees();
    }
}
