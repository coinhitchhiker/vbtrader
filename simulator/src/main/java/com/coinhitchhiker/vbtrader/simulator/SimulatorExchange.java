package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.model.event.TradeEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulatorExchange implements Exchange {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired private SimulatorRepositoryImpl repository;
    @Autowired private SimulatorOrderBookCache orderBookCache;
    @Autowired private TradingEngine tradingEngine;

    private long curTimestamp;
    private double curPrice;
    private double SLIPPAGE;
    private Map<String, Balance> balanceMap = new HashMap<>();
    private double START_BALANCE = 10000;

    private Map<String, OrderInfo> limitOrders = new HashMap<>();

    public SimulatorExchange(double SLIPPAGE) {
        this.SLIPPAGE = SLIPPAGE;

        Balance b = new Balance();
        b.setBalanceTotal(START_BALANCE);
        b.setAvailableForWithdraw(START_BALANCE);
        b.setAvailableForTrade(START_BALANCE);
        b.setExchange(ExchangeEnum.BINANCE);
        b.setCoin("USDT");

        balanceMap.put("USDT", b);

        Balance b2 = new Balance();
        b2.setBalanceTotal(START_BALANCE);
        b2.setAvailableForWithdraw(START_BALANCE);
        b2.setAvailableForTrade(START_BALANCE);
        b2.setExchange(ExchangeEnum.BITMEX);
        b2.setCoin("XBt");

        balanceMap.put("XBt", b2);

    }

    public void setTimestampAndPrice(long curTimestamp, double curPrice, double curVol) {
        for(Map.Entry<String, OrderInfo> entry : this.limitOrders.entrySet()) {
            OrderInfo limitOrder = entry.getValue();
            if(!limitOrder.getOrderStatus().equals(OrderStatus.COMPLETE)) {
                OrderSide side = limitOrder.getOrderSide();
                if(side.equals(OrderSide.BUY)) {
                    if(curPrice >= limitOrder.getPrice()) {
                        limitOrder.setPriceExecuted(limitOrder.getPrice());
                        limitOrder.setAmountExecuted(limitOrder.getAmount());
                        limitOrder.setOrderStatus(OrderStatus.COMPLETE);
                        limitOrder.setExecTimestamp(this.curTimestamp);
                    }
                } else {
                    if(curPrice <= limitOrder.getPrice()) {
                        limitOrder.setPriceExecuted(limitOrder.getPrice());
                        limitOrder.setAmountExecuted(limitOrder.getAmount());
                        limitOrder.setOrderStatus(OrderStatus.COMPLETE);
                        limitOrder.setExecTimestamp(this.curTimestamp);
                    }
                }
            }
        }

        this.curTimestamp = curTimestamp;
        this.curPrice = curPrice;

        repository.setCurrentTimestamp(curTimestamp);
        orderBookCache.setCurPrice(curPrice);
        this.orderBookCache.onTradeEvent(curPrice, curTimestamp, curVol);
    }

    @Override
    public OrderInfo placeOrder(OrderInfo orderInfo) {
        if(orderInfo.getOrderType().equals(OrderType.LIMIT_MAKER) ||
                orderInfo.getOrderType().equals(OrderType.LIMIT)) {
            orderInfo.setExternalOrderId(String.valueOf(Math.random() * 1000000));
            this.limitOrders.put(orderInfo.getExternalOrderId(), orderInfo.clone());
            return orderInfo;
        }

        // OK it's market order...
        orderInfo.setAmountExecuted(orderInfo.getAmount());
        orderInfo.setOrderStatus(OrderStatus.COMPLETE);
        orderInfo.setExecTimestamp(this.curTimestamp);
        if(orderInfo.getOrderSide() == OrderSide.BUY) {
            double trailingStopPrice = tradingEngine.getTrailingStopPrice();
            if(curPrice >= trailingStopPrice && trailingStopPrice > 0) {
                orderInfo.setPriceExecuted(trailingStopPrice * (1+SLIPPAGE));
            } else {
                double stopLossPrice = tradingEngine.getStopLossPrice();
                if(curPrice >= stopLossPrice && stopLossPrice < Double.MAX_VALUE) {
                    orderInfo.setPriceExecuted(stopLossPrice * (1+SLIPPAGE));
                } else {
                    orderInfo.setPriceExecuted(orderInfo.getPrice() * (1+SLIPPAGE));
                }
            }
        } else {
            double trailingStopPrice = tradingEngine.getTrailingStopPrice();
            if(curPrice < trailingStopPrice) {
                orderInfo.setPriceExecuted(trailingStopPrice * (1-SLIPPAGE));
            } else {
                double stopLossPrice = tradingEngine.getStopLossPrice();
                if(this.curPrice < stopLossPrice && stopLossPrice < Double.MAX_VALUE) {
                    orderInfo.setPriceExecuted(stopLossPrice * (1-SLIPPAGE));
                } else {
                    orderInfo.setPriceExecuted(this.curPrice * (1-SLIPPAGE));
                }
            }
        }
        return orderInfo;
    }

    @Override
    public OrderInfo cancelOrder(OrderInfo orderInfo) {
        return this.limitOrders.remove(orderInfo.getExternalOrderId());
    }

    @Override
    public OrderInfo getOrder(OrderInfo orderInfo) {
        OrderInfo limitOrder = this.limitOrders.get(orderInfo.getExternalOrderId());
        if(limitOrder != null) {
            return limitOrder;
        }

        double stopLossPrice = orderInfo.getStopPrice();
        if(stopLossPrice > 0) {
            if(orderInfo.getOrderStatus() == OrderStatus.PENDING) {
                if(curPrice < orderInfo.getPrice()) {
                    double rnd = Math.random();
                    if(rnd > 0.5) {
                        orderInfo.setOrderStatus(OrderStatus.COMPLETE);
                        orderInfo.setAmountExecuted(orderInfo.getAmount());
                        orderInfo.setExecTimestamp(this.curTimestamp);
                    } else {
                        orderInfo.setOrderStatus(OrderStatus.PARTIALLY_FILLED);
                        orderInfo.setAmountExecuted(orderInfo.getAmount()/2);
                        orderInfo.setExecTimestamp(this.curTimestamp);
                    }
                    return orderInfo;
                } else {
                    return orderInfo;
                }
            } else if(orderInfo.getOrderStatus() == OrderStatus.PARTIALLY_FILLED) {
                if(curPrice > orderInfo.getStopPrice()) {
                    double rnd = Math.random();
                    if(rnd > 0.5) {
                        orderInfo.setOrderStatus(OrderStatus.COMPLETE);
                        orderInfo.setAmountExecuted(orderInfo.getAmount());
                        orderInfo.setExecTimestamp(this.curTimestamp);
                    } else {
                        orderInfo.setOrderStatus(OrderStatus.PARTIALLY_FILLED);
                        orderInfo.setAmountExecuted(orderInfo.getAmount()/2);
                        orderInfo.setExecTimestamp(this.curTimestamp);
                    }
                    return orderInfo;
                }
            }
        }
        return orderInfo;
    }

    @Override
    public List<CoinInfo> getCoinInfoList() {
        return null;
    }

    @Override
    public CoinInfo getCoinInfoBySymbol(String symbol) {
        if(symbol.equals("BTCUSDT")) {
            //ExchangeEnum exchange, String market, String coin, String symbol, Double unitAmount, Double unitPrice, Double minUnitAmount, Double minUnitPrice) {
            return new CoinInfo(ExchangeEnum.BINANCE, "USDT", "BTC", "BTCUSDT", 0.00000001, 0.01, 0.00001, 10.0);
        }
        throw new RuntimeException("Unknown symbol was given");
    }

    @Override
    public double getCurrentPrice(String symbol) {
        return curPrice;
    }

    @Override
    public Map<String, Balance> getBalance() {
        return this.balanceMap;
    }

    public double getSTART_BALANCE() {
        return START_BALANCE;
    }

    public TradingEngine getTradingEngine() {
        return tradingEngine;
    }

    public void setTradingEngine(TradingEngine tradingEngine) {
        this.tradingEngine = tradingEngine;
    }
}
