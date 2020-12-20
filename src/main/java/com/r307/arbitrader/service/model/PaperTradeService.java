package com.r307.arbitrader.service.model;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.*;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.DefaultCancelOrderParamId;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsAll;
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParam;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class PaperTradeService extends BaseExchangeService<PaperExchange> implements TradeService {

    int orderId=0;

    TradeService tradeService;

    List<LimitOrder> openOrders=new ArrayList<>();

    UserTrades userTrades = new UserTrades (new ArrayList<>(), Trades.TradeSortType.SortByTimestamp);

    public PaperTradeService(PaperExchange exchange, TradeService tradeService) {
        super(exchange);
        this.tradeService=tradeService;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                closeOrders();
            }
        }, 0, 1000);
    }

    public OpenOrders getOpenOrders() {
        return new OpenOrders(openOrders);
    }

    public OpenOrders getOpenOrders(OpenOrdersParams params) {
        return new OpenOrders(openOrders);
    }

    public String placeLimitOrder(LimitOrder limitOrder) {
        orderId++;
        LimitOrder limit = new LimitOrder(limitOrder.getType(),
            limitOrder.getOriginalAmount(),
            limitOrder.getInstrument(),
            orderId+"",
            new Date(),
            limitOrder.getLimitPrice());
        openOrders.add(limit);
        return limit.getId();
    }

    public boolean cancelOrder(String orderId) {
        return openOrders.removeIf(obj -> obj.getId().equals(orderId));
    }

    public UserTrades getTradeHistory(TradeHistoryParams params) {
        return userTrades;
    }

    public TradeHistoryParams createTradeHistoryParams() {
        return tradeService.createTradeHistoryParams();
    }

    public OpenOrdersParams createOpenOrdersParams() {
        return tradeService.createOpenOrdersParams();
    }

    public void verifyOrder(LimitOrder limitOrder) {
         //DO NOTHING
    }

    public void verifyOrder(MarketOrder marketOrder) {
        //DO NOTHING
    }

    public Collection<Order> getOrder(String... orderIds) {
        return openOrders.stream().filter(order -> Arrays.asList(orderIds).contains(order.getId())).collect(Collectors.toList());
    }

    public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) {
        List<String> orderIds = Arrays.stream(orderQueryParams).map(OrderQueryParams::getOrderId).collect(Collectors.toList());
        return openOrders.stream().filter(order -> orderIds.contains(order.getId())).collect(Collectors.toList());
    }


    private void closeOrders() {
        for(LimitOrder order: openOrders) {
            userTrades.getUserTrades().add(new UserTrade(Order.OrderType.ASK, order.getOriginalAmount(),
                order.getInstrument(),
                order.getLimitPrice(),
                order.getTimestamp(),
                order.getId(),
                order.getId(),
                order.getFee(),
                Currency.USD,
                order.getUserReference()));
        }
        openOrders.clear();
    }
}
