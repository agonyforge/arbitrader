package com.r307.arbitrader.service.paper;

import com.r307.arbitrader.config.ExchangeConfiguration;
import com.r307.arbitrader.config.PaperConfiguration;
import com.r307.arbitrader.exception.MarginNotSupportedException;
import com.r307.arbitrader.service.ExchangeService;
import com.r307.arbitrader.service.TickerService;
import com.r307.arbitrader.service.model.ExchangeFee;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.*;
import org.knowm.xchange.exceptions.FundsExceededException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;
import static com.r307.arbitrader.DecimalConstants.USD_SCALE;
import static com.r307.arbitrader.config.FeeComputation.SERVER;
import static org.knowm.xchange.dto.Order.OrderType.ASK;

public class PaperTradeService extends BaseExchangeService<PaperExchange> implements TradeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaperTradeService.class);

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
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                PaperTradeService.this.updateOrders();
            }
        }, 0, 2000);
    }

    public OpenOrders getOpenOrders() {
        return new OpenOrders(orders.stream().filter(order -> order.getStatus().isOpen()).collect(Collectors.toList()));
    }

    public OpenOrders getOpenOrders(OpenOrdersParams params) {
        return getOpenOrders();
    }

    public String placeLimitOrder(LimitOrder limitOrder) {
        //Check if the order would keep our balance positive (for non margin accounts)
        verifyOrder(limitOrder);

        LimitOrder limit = LimitOrder.Builder.from(limitOrder)
            .id(UUID.randomUUID().toString())
            .timestamp(new Date())
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

    /**
     * Check if the account as enough fund to pass the order if filled with this averagePrice.
     * @see #verifyOrder(LimitOrder)
     * @param order the order to check against the funds
     * @param averagePrice the price to fill the order at
     */
    private void verifyOrder(LimitOrder order, BigDecimal averagePrice) {
        verifyOrder(LimitOrder.Builder.from(order).averagePrice(averagePrice).build());
    }

    /**
     * Check if the account as enough fund to pass the order.
     * Throw a FundsExceededException if the funds are not sufficient
     */
    public void verifyOrder(LimitOrder order) {
        if(!useMargin(order)) {
            BigDecimal counterDelta = getCounterDelta(order);
            BigDecimal baseDelta = getBaseDelta(order);

            //we don't have enough cash
            if(exchange.getPaperAccountService().getBalance(order.getCurrencyPair().counter).add(counterDelta).compareTo(BigDecimal.ZERO) <0)
                throw new FundsExceededException();

            //we don't have enough crypto
            if(exchange.getPaperAccountService().getBalance(order.getCurrencyPair().base).add(baseDelta).compareTo(BigDecimal.ZERO) <0)
                throw new FundsExceededException();
        } else {
            // TODO after implementing leverage in the paper exchange, we should check here if we have sufficient funds to cover the leverage
            // For now we can consider that we have unlimited funds for margin orders
        }
    }

    public void verifyOrder(MarketOrder marketOrder) {
        throw new NotYetImplementedForExchangeException("Market orders are not supported by paper trading exchanges.");
    }

    public Collection<Order> getOrder(String... orderIds) {
        if(orderIds == null || orderIds.length==0)
            return new ArrayList<>();
        return orders.stream().filter(order -> Arrays.asList(orderIds).contains(order.getId())).collect(Collectors.toList());
    }

    public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) {
        List<String> orderIds = Arrays.stream(orderQueryParams).map(OrderQueryParams::getOrderId).collect(Collectors.toList());
        String[] orderIdsArray = new String[orderIds.size()];
        return getOrder(orderIds.toArray(orderIdsArray));
    }

    /**
     * Update all the open orders according to real market data
     */
    private void updateOrders() {
        for(LimitOrder order: orders) {
            if(order.getStatus().isOpen()) {
                if(autoFill) {
                    fillOrder(order, order.getLimitPrice());
                } else {
                    Order.OrderType type = order.getType();
                    Ticker ticker = tickerService.getTicker(exchange, order.getCurrencyPair());

                    LOGGER.debug("Ticker fetch for paper trading: {}/{}", ticker.getBid(), ticker.getAsk());

                    //Check if limit price was reached
                    boolean matchedOrder = type == Order.OrderType.BID && ticker.getAsk().compareTo(order.getLimitPrice()) <= 0 || type == ASK && ticker.getBid().compareTo(order.getLimitPrice()) >= 0;
                    if (matchedOrder) {
                        //TODO randomize the average price to simulate real conditions
                        fillOrder(order, order.getLimitPrice());
                    }
                }
            }
        }
    }

    /**
     * Fill an order at an averagePrice
     * @param order the order to fill
     * @param averagePrice the average price to fill the order at
     */
    void fillOrder(LimitOrder order, BigDecimal averagePrice) {
        verifyOrder(order, averagePrice);

        //Fill the order at the average price
        order.setOrderStatus(Order.OrderStatus.FILLED);
        order.setAveragePrice(averagePrice);
        order.setCumulativeAmount(order.getOriginalAmount());

        //LimitOrder object cannot store the fees in crypto, only the fees in fiat
        order.setFee(getCounterFee(order));

        //Find the total cost of the order
        BigDecimal counterDelta = getCounterDelta(order);

        //Find the total volume of the order
        BigDecimal baseDelta = getBaseDelta(order);

        LOGGER.info("{} paper exchange: filled {}",
            exchange.getExchangeSpecification().getExchangeName(),
            order.toString());

        //Update balances
        exchange.getPaperAccountService().putCoin(order.getCurrencyPair().counter, counterDelta);
        exchange.getPaperAccountService().putCoin(order.getCurrencyPair().base, baseDelta);

        LOGGER.info("{} paper account: {}",
            exchange.getExchangeSpecification().getExchangeName(),
            exchange.getPaperAccountService().getAccountInfo().toString());

        //Populate trade history
        userTrades.getUserTrades().add(new UserTrade(order.getType(),
            order.getOriginalAmount(),
            order.getInstrument(),
            order.getLimitPrice(),
            order.getTimestamp(),
            order.getId(),
            order.getId(),
            order.getFee(),
            Currency.USD,
            order.getUserReference()));
    }

    /**
     * Check if this order requires margin. This means, check if the exchange need to borrow money/crypto in order to
     * fulfil this order. This method throws a {@link MarginNotSupportedException} if the order requires margin
     * but the exchange does not support margin trades.
     * @param order the order to check
     * @return true if this order requires margin and the exchange has margin enabled. False otherwise
     */
    private boolean useMargin(LimitOrder order) {
        if(order.getLeverage() != null && !exchangeService.getExchangeMetadata(exchange).getMargin()) {
            throw new MarginNotSupportedException(exchange.getExchangeSpecification().getExchangeName());
        }

        return order.getLeverage() != null && exchangeService.getExchangeMetadata(exchange).getMargin();
    }

    /**
     * Get the amount of currency.counter (ie. fiat) that will be added/subtracted to the fiat balance
     * If available, this use the order average price; if not available this uses the limit price instead (for new orders)
     * @param order the order
     * @return the currency.base delta
     */
    private BigDecimal getCounterDelta(LimitOrder order) {
        BigDecimal cumulativeCounterAmount = order.getAveragePrice() != null ? order.getOriginalAmount().multiply(order.getAveragePrice()) : order.getOriginalAmount().multiply(order.getLimitPrice());

        //Calculate fees in fiat currency
        BigDecimal counterFee = getCounterFee(order);

        //Find the total cost of the order
        BigDecimal counterDelta = order.getType()== ASK ? cumulativeCounterAmount: cumulativeCounterAmount.negate();
        counterDelta = counterDelta.subtract(counterFee).setScale(USD_SCALE, RoundingMode.FLOOR);
        return counterDelta;
    }

    /**
     * Get the amount of currency.base (ie. crypto) that will be added/subtracted to the balance
     * @param order the order
     * @return the currency.base delta
     */
    private BigDecimal getBaseDelta(LimitOrder order) {
        BigDecimal feePercentage = getFee(order);

        //Calculate fees in crypto currency
        BigDecimal baseFee = exchangeService.getExchangeMetadata(exchange).getFeeComputation() == SERVER ? BigDecimal.ZERO : order.getOriginalAmount().multiply(feePercentage).setScale(BTC_SCALE,RoundingMode.HALF_EVEN);

        //Find the total volume of the order
        BigDecimal baseDelta = order.getType()== ASK ? order.getOriginalAmount().negate(): order.getOriginalAmount();
        baseDelta = baseDelta.subtract(baseFee);
        return baseDelta;
    }

    /**
     * Get the fiat fees for the order
     * @param order the order
     * @return the fees in the counter currency
     */
    private BigDecimal getCounterFee(LimitOrder order) {
        BigDecimal cumulativeCounterAmount = order.getAveragePrice() != null ? order.getOriginalAmount().multiply(order.getAveragePrice()) : order.getOriginalAmount().multiply(order.getLimitPrice());
        BigDecimal feePercentage = getFee(order);
        return exchangeService.getExchangeMetadata(exchange).getFeeComputation() == SERVER ? cumulativeCounterAmount.multiply(feePercentage) : BigDecimal.ZERO;
    }

    private BigDecimal getFee(LimitOrder order) {
        final String exchangeName = exchange.getExchangeSpecification().getExchangeName();
        final ExchangeFee exchangeFee = exchangeService.getExchangeFee(exchange, (CurrencyPair) order.getInstrument(), false);
        final ExchangeConfiguration exchangeConfiguration = exchangeService.getExchangeMetadata(exchange);

        BigDecimal fee = useMargin(order)  && exchangeFee.getMarginFee().isPresent() ?
            exchangeFee.getMarginFee().get().add(exchangeFee.getTradeFee()) : exchangeFee.getTradeFee();
        LOGGER.info("{} paper exchange: margin enabled:{}|margin required:{}| order using {} fee",
            exchangeName, exchangeConfiguration.getMargin(), useMargin(order), fee);

        return fee;
    }
}
