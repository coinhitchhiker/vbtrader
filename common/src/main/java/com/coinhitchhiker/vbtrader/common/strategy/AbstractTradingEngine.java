package com.coinhitchhiker.vbtrader.common.strategy;

import com.coinhitchhiker.vbtrader.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    protected final boolean STOP_LOSS_ENABLED;
    protected final double STOP_LOSS_PCT;

    protected OrderInfo placedBuyOrder = null;
    protected OrderInfo placedSellOrder = null;
    protected double buyFee = 0;
    protected double sellFee = 0;
    protected double trailingStopPrice = 0;
    protected double stopLossPrice = Double.MAX_VALUE;
    protected double prevPrice = 0;

    public AbstractTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache, String MODE, String SYMBOL,
                                 String QUOTE_CURRENCY, double LIMIT_ORDER_PREMIUM, String EXCHANGE, double FEE_RATE, boolean TRADING_ENABLED,
                                 boolean TRAILING_STOP_ENABLED, double TS_TRIGGER_PCT, double TS_PCT, boolean STOP_LOSS_ENABLED, double STOP_LOSS_PCT) {
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

        this.STOP_LOSS_ENABLED = STOP_LOSS_ENABLED;
        this.STOP_LOSS_PCT = STOP_LOSS_PCT;
    }

    private void placeBuyOrderLong(double curPrice, double buySignalStrength) {
        double availableBalance = exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade();
        double buyPrice = curPrice * (1 + LIMIT_ORDER_PREMIUM/100.0D);
        double cost = availableBalance * buySignalStrength;
        double amount = cost / buyPrice;
        OrderInfo buyOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.BUY, buyPrice, amount);

        if(!TRADING_ENABLED) {
            LOGGER.info("-------------------TRADING DISABLED!-------------------");
            LOGGER.info("[PREPARED BUYORDER] {}", buyOrder.toString());
            return;
        }

        OrderInfo placedBuyOrder = exchange.placeOrder(buyOrder);
        LOGGER.info("[PLACED BUY ORDER] {}", placedBuyOrder.toString());
        LOGGERBUYSELL.info("[PLACED BUY ORDER] {}", placedBuyOrder.toString());
        this.buyFee = placedBuyOrder.getAmountExecuted() * placedBuyOrder.getPriceExecuted() * FEE_RATE / 100.0D;;
        this.placedBuyOrder = placedBuyOrder;
    }

    private void placeSellOrderLong() {
        LOGGER.info("-----------------------------SELL IT!!!!!!----------------------");
        double sellPrice = orderBookCache.getBestBid() * (1 - LIMIT_ORDER_PREMIUM/100.0D);
        double sellAmount = placedBuyOrder.getAmountExecuted();
        OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, sellPrice, sellAmount);
        OrderInfo placedSellOrder = exchange.placeOrder(sellOrder);
        LOGGER.info("[PLACED SELL ORDER] {}", placedSellOrder.toString());
        LOGGERBUYSELL.info("[PLACED SELL ORDER] {}", placedSellOrder.toString());
        this.sellFee = placedSellOrder.getAmountExecuted() * placedSellOrder.getPriceExecuted() * FEE_RATE / 100.0D;
        this.placedSellOrder = placedSellOrder;
    }

    private void placeSellOrderShort(double curPrice, double sellSignalStrength) {
        double availableBalance = exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade();
        double sellPrice = curPrice * (1 - LIMIT_ORDER_PREMIUM/100.0D);
        double cost = availableBalance * sellSignalStrength;
        double amount = cost / sellPrice;
        OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, sellPrice, amount);

        if(!TRADING_ENABLED) {
            LOGGER.info("-------------------TRADING DISABLED!-------------------");
            LOGGER.info("[PREPARED SELLORDER] {}", sellOrder.toString());
            return;
        }

        OrderInfo placedSellOrder = exchange.placeOrder(sellOrder);
        LOGGER.info("[PLACED SELL ORDER] {}", placedSellOrder.toString());
        LOGGERBUYSELL.info("[PLACED SELL ORDER] {}", placedSellOrder.toString());
        this.sellFee = placedSellOrder.getAmountExecuted() * placedSellOrder.getPriceExecuted() * FEE_RATE / 100.0D;;
        this.placedSellOrder = placedSellOrder;
    }

    private void placeBuyOrderShort() {
        double buyPrice = orderBookCache.getBestAsk() * (1 + LIMIT_ORDER_PREMIUM/100.0D);
        double amount = this.placedSellOrder.getAmountExecuted();
        OrderInfo buyOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.BUY, buyPrice, amount);
        OrderInfo placedBuyOrder = exchange.placeOrder(buyOrder);
        LOGGER.info("[PLACED BUY ORDER] {}", placedBuyOrder.toString());
        LOGGERBUYSELL.info("[PLACED BUY ORDER] {}", placedBuyOrder.toString());
        this.buyFee = placedBuyOrder.getAmountExecuted() * placedBuyOrder.getPriceExecuted() * FEE_RATE / 100.0D;;
        this.placedBuyOrder = placedBuyOrder;
    }

    private TradeResult calcProfit() {
        double profit = (placedSellOrder.getPriceExecuted() - placedBuyOrder.getPriceExecuted()) * placedSellOrder.getAmountExecuted();
        double netProfit =  profit - (buyFee + sellFee);

        LOGGERBUYSELL.info("[{}] [{}] netProfit={}, profit={}, fee={}, sellPrice={} buyPrice={}, amount={},  ",
                MODE,
                netProfit >= 0 ? "PROFIT" : "LOSS",
                netProfit,
                profit,
                buyFee + sellFee,
                placedSellOrder.getPriceExecuted(),
                placedBuyOrder.getPriceExecuted(),
                placedBuyOrder.getAmountExecuted());

        return new TradeResult(EXCHANGE, SYMBOL, QUOTE_CURRENCY, netProfit, profit, placedSellOrder.getPriceExecuted(), placedBuyOrder.getPriceExecuted(), placedBuyOrder.getAmountExecuted(), buyFee + sellFee);
    }

    protected TradeResult placeBuyOrder(double curPrice, double buySignalStrength) {
        if(MODE.equals("LONG")) {
            try {
                placeBuyOrderLong(curPrice, buySignalStrength);
                if(STOP_LOSS_ENABLED) setStopLoss();
            } catch(Exception e) {
                LOGGER.error("Error placing buy order", e);
            }
            return null;
        } else {        // SHORT
            if(placedSellOrder == null) return null;
            try {
                placeBuyOrderShort();
            } catch(Exception e) {
                LOGGER.error("Error placing buy order", e);
                return null;
            }
            TradeResult tradeResult = calcProfit();
            // clear out saved order status in the trading engine
            this.clearOutOrders();
            return tradeResult;
        }
    }

    protected TradeResult placeSellOrder(double curPrice, double sellSignalStrength) {
        if(MODE.equals("LONG")) {
            if(placedBuyOrder == null) return null;
            try {
                placeSellOrderLong();
            } catch(Exception e) {
                LOGGER.error("Error placing sell order", e);
                return null;
            }
            TradeResult tradeResult = calcProfit();
            this.clearOutOrders();
            return tradeResult;
        } else {    // SHORT
            try {
                placeSellOrderShort(curPrice, sellSignalStrength);
                if(STOP_LOSS_ENABLED) setStopLoss();
            } catch(Exception e) {
                LOGGER.error("Error placing sell order", e);
            }
            return null;
        }
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

    private void setStopLoss() {
        if(MODE.equals("LONG") && STOP_LOSS_ENABLED && this.placedBuyOrder != null) {
            double buyPriceExecuted = placedBuyOrder.getPriceExecuted();
            if(this.stopLossPrice == Double.MAX_VALUE) {
                this.stopLossPrice = buyPriceExecuted * (1 - STOP_LOSS_PCT/100.0);
            }
        }

        if(MODE.equals("SHORT") && STOP_LOSS_ENABLED && this.placedSellOrder != null) {
            double sellPriceExecuted = placedSellOrder.getPriceExecuted();
            if(this.stopLossPrice == Double.MAX_VALUE) {
                double stopLossPrice = sellPriceExecuted * (1 + STOP_LOSS_PCT/100.0);
                this.stopLossPrice = stopLossPrice;
            }
        }
    }

    private void clearOutOrders() {
        this.placedBuyOrder = null;
        this.placedSellOrder = null;
        this.buyFee = 0;
        this.sellFee = 0;
        this.trailingStopPrice = 0;
        this.stopLossPrice = Double.MAX_VALUE;
        this.prevPrice = 0;
    }

    public boolean stopLossHit(double curPrice) {
        if(MODE.equals("LONG"))
            return STOP_LOSS_ENABLED && this.placedBuyOrder != null && curPrice < this.stopLossPrice && this.stopLossPrice < Double.MAX_VALUE;
        else
            return STOP_LOSS_ENABLED && this.placedSellOrder != null && curPrice > this.stopLossPrice && this.stopLossPrice < Double.MAX_VALUE;
    }

    public boolean trailingStopHit(double curPrice) {
        if(MODE.equals("LONG"))
            return TRAILING_STOP_ENABLED && placedBuyOrder != null && curPrice < this.trailingStopPrice && this.trailingStopPrice > 0.0;
        else    // SHORT
            return TRAILING_STOP_ENABLED && placedSellOrder != null && curPrice > this.trailingStopPrice && this.trailingStopPrice > 0.0;
    }

    public double getTrailingStopPrice() {
        return trailingStopPrice;
    }

    public double getStopLossPrice() {
        return stopLossPrice;
    }
}
