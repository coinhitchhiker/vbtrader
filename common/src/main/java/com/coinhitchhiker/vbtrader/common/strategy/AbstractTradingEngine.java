package com.coinhitchhiker.vbtrader.common.strategy;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.strategy.vb.VBLongTradingEngine;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.joda.time.DateTimeZone.UTC;

public class AbstractTradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTradingEngine.class);
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");

    protected final Repository repository;
    protected final Exchange exchange;
    protected final OrderBookCache orderBookCache;

    protected final String MODE;
    protected final String SYMBOL;
    protected final String QUOTE_CURRENCY;
    protected final double LIMIT_ORDER_PREMIUM;
    protected final String EXCHANGE;
    protected final double FEE_RATE;
    protected final boolean TRADING_ENABLED;

    protected final boolean TRAILING_STOP_ENABLED;
    protected final double TS_TRIGGER_PCT;
    protected final double TS_PCT;

    protected OrderInfo placedBuyOrder = null;
    protected OrderInfo placedSellOrder = null;
    protected double buyFee = 0;
    protected double sellFee = 0;
    protected double trailingStopPrice = 0;
    protected double stopLossPrice = 0;
    protected double prevPrice = 0;

    public AbstractTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache, String MODE, String SYMBOL,
                                 String QUOTE_CURRENCY, double LIMIT_ORDER_PREMIUM, String EXCHANGE, double FEE_RATE, boolean TRADING_ENABLED,
                                 boolean TRAILING_STOP_ENABLED, double TS_TRIGGER_PCT, double TS_PCT) {
        this.repository = repository;
        this.exchange = exchange;
        this.orderBookCache = orderBookCache;

        this.MODE = MODE;
        this.SYMBOL = SYMBOL;
        this.QUOTE_CURRENCY = QUOTE_CURRENCY;
        this.LIMIT_ORDER_PREMIUM = LIMIT_ORDER_PREMIUM;
        this.EXCHANGE = EXCHANGE;
        this.FEE_RATE = FEE_RATE;
        this.TRADING_ENABLED = TRADING_ENABLED;

        this.TRAILING_STOP_ENABLED = TRAILING_STOP_ENABLED;
        this.TS_TRIGGER_PCT = TS_TRIGGER_PCT;
        this.TS_PCT = TS_PCT;
    }

    protected void placeBuyOrder(double curPrice, double buySignalStrength) {
        double availableBalance = exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade();
        double buyPrice = curPrice * (1 + LIMIT_ORDER_PREMIUM/100.0D);
        double cost = availableBalance * buySignalStrength;
        double amount = cost / buyPrice;
        OrderInfo buyOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.BUY, buyPrice, amount);
        double buyFee = 0;

        try {
            if(!TRADING_ENABLED) {
                LOGGER.info("-------------------TRADING DISABLED!-------------------");
                LOGGER.info("[PREPARED BUYORDER] {}", buyOrder.toString());
                return;
            }

            OrderInfo placedBuyOrder = exchange.placeOrder(buyOrder);
            LOGGER.info("[PLACED BUY ORDER] {}", placedBuyOrder.toString());
            LOGGERBUYSELL.info("[PLACED BUY ORDER] {}", placedBuyOrder.toString());
            buyFee = placedBuyOrder.getAmountExecuted() * placedBuyOrder.getPriceExecuted() * FEE_RATE / 100.0D;
            this.placedBuyOrder = placedBuyOrder;
            this.buyFee = buyFee;
        } catch(Exception e) {
            LOGGER.error("Placing buy order failed", e);
        }
    }

    protected TradeResult placeSellOrder(long curTimestamp) {
        TradeResult tradeResult = null;

        if(placedBuyOrder == null) return null;

        LOGGER.info("-----------------------------SELL IT!!!!!!----------------------");
        double bestBid = orderBookCache.getBestBid();
        double sellPrice = bestBid * (1 - LIMIT_ORDER_PREMIUM/100.0D);
        double sellAmount = placedBuyOrder.getAmountExecuted();
        OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, sellPrice, sellAmount);
        OrderInfo placedSellOrder = null;

        try {
            placedSellOrder = exchange.placeOrder(sellOrder);
            LOGGER.info("[PLACED SELL ORDER] {}", placedSellOrder.toString());
            LOGGERBUYSELL.info("[PLACED SELL ORDER] {}", placedSellOrder.toString());

            double sellFee = placedSellOrder.getAmountExecuted() * placedSellOrder.getPriceExecuted() * FEE_RATE / 100.0D;
            double profit = (placedSellOrder.getPriceExecuted() - placedBuyOrder.getPriceExecuted()) * sellAmount;
            double netProfit =  profit - (buyFee + sellFee);

            LOGGERBUYSELL.info("[LONG] [{}] netProfit={}, profit={}, fee={}, sellPrice={} buyPrice={}, amount={},  ",
                    netProfit >= 0 ? "PROFIT" : "LOSS",
                    netProfit,
                    profit,
                    buyFee + sellFee,
                    placedSellOrder.getPriceExecuted(),
                    placedBuyOrder.getPriceExecuted(),
                    placedBuyOrder.getAmountExecuted());

            tradeResult = new TradeResult(EXCHANGE, SYMBOL, QUOTE_CURRENCY, netProfit, profit, placedSellOrder.getPriceExecuted(), placedBuyOrder.getPriceExecuted(), placedBuyOrder.getAmountExecuted(), buyFee + sellFee);
        } catch(Exception e) {
            LOGGER.error("Placing sell order error", e);
        }

        // clear out saved order status in the trading engine
        this.clearOutOrders();

        return tradeResult;
    }

    protected void updateTrailingStopPrice(double curPrice) {
        if(MODE.equals("LONG") && this.placedBuyOrder != null) {
            if(this.trailingStopPrice == 0) {
                if(curPrice > placedBuyOrder.getPriceExecuted() * (100.0 + TS_TRIGGER_PCT)/100) {
                    LOGGER.info(String.format("[LONG] prevPrice=%.2f, curPrice=%.2f, old TS=%.2f, new TS=%.2f", prevPrice, curPrice, this.trailingStopPrice, curPrice * (100.0 - TS_PCT)/100.0));
                    this.trailingStopPrice = curPrice * (100.0 - TS_PCT)/100.0;
                }
            } else {
                if(curPrice > prevPrice) {
                    if(this.trailingStopPrice < curPrice * (100.0 - TS_PCT)/100.0) {
                        LOGGER.info(String.format("[LONG] prevPrice=%.2f, curPrice=%.2f, old TS=%.2f, new TS=%.2f", prevPrice, curPrice, this.trailingStopPrice, curPrice * (100.0 - TS_PCT)/100.0));
                        this.trailingStopPrice = curPrice * (100.0 - TS_PCT)/100.0;
                    }
                }
            }
        }

        if(MODE.equals("SHORT") && this.placedSellOrder != null) {
            if(this.trailingStopPrice == 0) {
                if(curPrice < placedSellOrder.getPriceExecuted() * (100.0 - TS_TRIGGER_PCT)/100.0) {
                    LOGGER.info(String.format("[SHORT] prevPrice=%.2f, curPrice=%.2f, old TS=%.2f, new TS=%.2f", prevPrice, curPrice, this.trailingStopPrice, curPrice * (100.0 + TS_PCT)/100.0));
                    this.trailingStopPrice = curPrice * (100.0 + TS_PCT)/100.0;
                }
            } else {
                if(curPrice < prevPrice) {
                    if(this.trailingStopPrice > curPrice * (100.0 + TS_PCT)/100.0) {
                        LOGGER.info(String.format("[SHORT] prevPrice=%.2f, curPrice=%.2f, old TS=%.2f, new TS=%.2f", prevPrice, curPrice, this.trailingStopPrice, curPrice * (100.0 + TS_PCT)/100.0));
                        this.trailingStopPrice = curPrice * (100.0 + TS_PCT)/100.0;
                    }
                }
            }
        }

        this.prevPrice = curPrice;
    }

    private void clearOutOrders() {
        this.placedBuyOrder = null;
        this.placedSellOrder = null;
        this.buyFee = 0;
        this.sellFee = 0;
        this.trailingStopPrice = 0;
        this.prevPrice = 0;
    }

    public double getTrailingStopPrice() {
        return trailingStopPrice;
    }

    public double getStopLossPrice() {
        return stopLossPrice;
    }
}
