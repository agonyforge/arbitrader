package com.r307.arbitrader.service.paper;

import com.r307.arbitrader.config.PaperConfiguration;
import com.r307.arbitrader.service.ExchangeService;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.*;
import org.knowm.xchange.exceptions.FundsExceededException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class PaperAccountService implements AccountService {

    private PaperExchange paperExchange;
    private final AccountService accountService;
    private final ExchangeService exchangeService;
    private Map<Currency, BigDecimal> balances;


    public PaperAccountService (PaperExchange paperExchange, AccountService accountService, Currency homeCurrency, ExchangeService exchangeService, PaperConfiguration paper) {
        this.paperExchange = paperExchange;
        this.accountService=accountService;
        this.balances=new HashMap<>();
        this.exchangeService=exchangeService;
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
        } else if (!exchangeService.getExchangeMetadata(paperExchange).getMargin() && getBalance(currency).add(amount).compareTo(BigDecimal.ZERO) < 0){
            throw new FundsExceededException();
        } else if (getBalance(currency).add(amount).compareTo(BigDecimal.ZERO) == 0){
            balances.remove(currency);
        } else {
            balances.put(currency,getBalance(currency).add(amount));
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
