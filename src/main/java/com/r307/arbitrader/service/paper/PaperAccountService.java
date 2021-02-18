package com.r307.arbitrader.service.paper;

import com.r307.arbitrader.config.PaperConfiguration;
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
import java.util.*;
import java.util.stream.Collectors;

public class PaperAccountService implements AccountService {

    private final AccountService accountService;
    private final Currency homeCurrency;

    private Map<Currency, BigDecimal> balances;

    public PaperAccountService (AccountService accountService, Currency homeCurrency, PaperConfiguration paper) {
        this.accountService=accountService;
        this.homeCurrency=homeCurrency;
        this.balances=new HashMap<>();
        BigDecimal initialBalance = paper.getInitialBalance() != null ? paper.getInitialBalance() : new BigDecimal("100");
        putCoin(homeCurrency,initialBalance);
    }

    public Map<Currency, BigDecimal> getBalances() {
        return balances;
    }

    public BigDecimal getBalance(Currency currency) {
        if(balances.containsKey(currency))
            return balances.get(currency);
        return BigDecimal.ZERO;
    }

    public void putCoin(Currency currency, BigDecimal amount) {
        if(amount.compareTo(BigDecimal.ZERO) == 0) {
            //DO NOTHING
        } if(!balances.containsKey(currency)) {
            balances.put(currency, amount);
        } else if (getBalance(currency).compareTo(BigDecimal.ZERO) != 0){
            balances.put(currency,getBalance(currency).add(amount));
        } else {
            balances.remove(currency);
        }
    }

    public AccountInfo getAccountInfo() {
        List<Wallet> wallets = new ArrayList<>();
        List<Balance> walletBalances = this.balances.entrySet().stream().map(entry -> new Balance(
            entry.getKey(),
            entry.getValue(),
            entry.getValue(),
            new BigDecimal(0))).collect(Collectors.toList());
        wallets.add(Wallet.Builder.from(walletBalances).id(UUID.randomUUID().toString()).build());
        return new AccountInfo (wallets);
    }

    public Map<Instrument, Fee> getDynamicTradingFeesByInstrument() throws IOException {
        return accountService.getDynamicTradingFeesByInstrument();
    }

    public Map<CurrencyPair, Fee> getDynamicTradingFees() throws IOException {
        return accountService.getDynamicTradingFees();
    }
}
