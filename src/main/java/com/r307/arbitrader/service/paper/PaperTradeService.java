package com.r307.arbitrader.service.paper;

import com.r307.arbitrader.config.ExchangeConfiguration;
import com.r307.arbitrader.config.PaperConfiguration;
import com.r307.arbitrader.service.ExchangeService;
import com.r307.arbitrader.service.TickerService;
import com.r307.arbitrader.service.model.ExchangeFee;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.*;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PaperTradeService extends BaseExchangeService<PaperExchange> implements TradeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaperTradeService.class);
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private final boolean autoFill;
    private final TickerService tickerService;
    private final ExchangeService exchangeService;
    private final TradeService tradeService;
    private final List<LimitOrder> orders= new ArrayList<>();
    private final UserTrades userTrades = new UserTrades (new ArrayList<>(), Trades.TradeSortType.SortByTimestamp);

    public PaperTradeService(PaperExchange exchange, TradeService tradeService, TickerService tickerService, ExchangeService exchangeService, PaperConfiguration paper) {
        super(exchange);
        this.tradeService=tradeService;
        this.autoFill = paper.isAutoFill();
        this.tickerService=tickerService;
        this.exchangeService=exchangeService;
        PaperTradeService.scheduler.schedule(this::updateOrders,10, TimeUnit.SECONDS);
    }

    public OpenOrders getOpenOrders() {
        updateOrders();
        return new OpenOrders(orders.stream().filter(order -> order.getStatus().isOpen()).collect(Collectors.toList()));
    }

    public OpenOrders getOpenOrders(OpenOrdersParams params) {
        return getOpenOrders();
    }

    public String placeLimitOrder(LimitOrder limitOrder) {
        updateOrders();
        LimitOrder limit = new LimitOrder.Builder(limitOrder.getType(), limitOrder.getInstrument())
            .id(UUID.randomUUID().toString())
            .originalAmount(limitOrder.getOriginalAmount())
            .timestamp(new Date())
            .limitPrice(limitOrder.getLimitPrice())
            .orderStatus(Order.OrderStatus.NEW)
            .build();

        orders.add(limit);

        LOGGER.info("{} paper exchange: order {} for currency pair {} placed with limit {} and amount {}",
            exchange.getExchangeSpecification().getExchangeName(),
            limit.getId(),
            limit.getInstrument(),
            limit.getLimitPrice(),
            limit.getOriginalAmount()
        );
        return limit.getId();
    }

    public boolean cancelOrder(String orderId) {
        updateOrders();
        Optional<Order> optionalOrder = getOrder(orderId).stream().findFirst();
        if(!optionalOrder.isPresent()) {
            LOGGER.warn("{} paper exchange: order {} to cancel not found.",
                exchange.getExchangeSpecification().getExchangeName(),
                orderId);
            return false;
        }
        if(optionalOrder.get().getStatus().isOpen()) {
            LOGGER.warn("{} paper exchange: cannot cancel order {} because order is not open.",
                exchange.getExchangeSpecification().getExchangeName(),
                orderId);
            return false;
        }
        optionalOrder.get().setOrderStatus(Order.OrderStatus.CANCELED);
        return true;
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
        updateOrders();
        if(orderIds == null || orderIds.length==0)
            return new ArrayList<>();
        return orders.stream().filter(order -> Arrays.asList(orderIds).contains(order.getId())).collect(Collectors.toList());
    }

    public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) {
        List<String> orderIds = Arrays.stream(orderQueryParams).map(OrderQueryParams::getOrderId).collect(Collectors.toList());
        String[] orderIdsArray = new String[orderIds.size()];
        return getOrder(orderIds.toArray(orderIdsArray));
    }


    private void updateOrders() {
        for(LimitOrder order: orders) {
            if(order.getStatus().isOpen()) {
                if(autoFill) {
                    fillOrder(order);
                } else {
                    Order.OrderType type = order.getType();
                    Ticker ticker = tickerService.getTicker(exchange, (CurrencyPair) order.getInstrument());

                    LOGGER.debug("Ticker fetch for paper trading: {}/{}", ticker.getBid(), ticker.getAsk());

                    //Check if limit price was reached
                    boolean matchedOrder = type == Order.OrderType.BID && ticker.getAsk().compareTo(order.getLimitPrice()) <= 0 || type == Order.OrderType.ASK && ticker.getBid().compareTo(order.getLimitPrice()) >= 0;
                    if (matchedOrder) {
                        fillOrder(order);
                    }
                }
            }
        }
    }


    private void fillOrder(LimitOrder order) {
        final ExchangeFee exchangeFee = exchangeService.getExchangeFee(exchange, order.getCurrencyPair(), false);
        final String exchangeName = exchange.getExchangeSpecification().getExchangeName();
        final ExchangeConfiguration exchangeConfiguration = exchangeService.getExchangeMetadata(exchange);

        // If leverage is empty it means it is a long order. If it is NOT empty then it is a short order
        if (order.getLeverage() != null && !order.getLeverage().isEmpty() && !exchangeFee.getShortFee().isPresent()) {
            LOGGER.error("exchange:{}|missing short fee configuration. Go to application.yml and set a shot fee for this exchange", exchangeName);
            throw new RuntimeException("Missing short fee configuration for exchange " + exchangeName);
        }

        final BigDecimal fee;
        if (order.getLeverage() == null) {
            fee = exchangeFee.getLongFee();
            LOGGER.info("exchange:{}|marginExchange:{}|short order using {} fee", exchangeName, exchangeConfiguration.getMargin(), fee);
        }
        else {
            fee = exchangeFee.getShortFee().get();
            LOGGER.info("exchange:{}|marginExchange:{}|long order using {} fee", exchangeName, exchangeConfiguration.getMargin(), fee);
        }

        order.setOrderStatus(Order.OrderStatus.FILLED);
        order.setAveragePrice(order.getLimitPrice());
        order.setCumulativeAmount(order.getOriginalAmount());
        order.setFee(order.getCumulativeCounterAmount().multiply(fee));
        LOGGER.info("{} paper exchange: Order {} filled for {}{}, with {} fees.",
            exchangeName,
            order.getId(),
            order.getCumulativeCounterAmount(),
            ((CurrencyPair)order.getInstrument()).counter,
            order.getFee());

        if(order.getType()== Order.OrderType.ASK) {
            //Sell order
            exchange.getPaperAccountService().setBalance(exchange.getPaperAccountService().getBalance().add(order.getCumulativeCounterAmount()));
        } else {
            exchange.getPaperAccountService().setBalance(exchange.getPaperAccountService().getBalance().subtract(order.getCumulativeCounterAmount()));
        }
        exchange.getPaperAccountService().setBalance(exchange.getPaperAccountService().getBalance().subtract(order.getFee()));
        LOGGER.info("{} paper account: new balance is {}", exchangeName, exchange.getPaperAccountService().getBalance());
        userTrades.getUserTrades().add(new UserTrade(order.getType(), order.getOriginalAmount(),
            order.getInstrument(),
            order.getLimitPrice(),
            order.getTimestamp(),
            order.getId(),
            order.getId(),
            order.getFee(),
            Currency.USD,
            order.getUserReference()));
    }
}
