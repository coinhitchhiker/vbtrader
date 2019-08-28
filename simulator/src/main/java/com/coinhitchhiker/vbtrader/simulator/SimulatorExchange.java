package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulatorExchange implements Exchange {

    private SimulatorRepositoryImpl repository;
    private SimulatorOrderBookCache orderBookCache;
    private long curTimestamp;
    private double curPrice;
    private double SLIPPAGE;
    private Map<String, Balance> balanceMap = new HashMap<>();
    private double START_BALANCE = 10000;
    private TradingEngine tradingEngine;

    public SimulatorExchange(SimulatorRepositoryImpl repository, SimulatorOrderBookCache orderBookCache, double SLIPPAGE) {
        this.repository = repository;
        this.orderBookCache = orderBookCache;
        this.SLIPPAGE = SLIPPAGE;

        Balance b = new Balance();
        b.setBalanceTotal(START_BALANCE);
        b.setAvailableForWithdraw(START_BALANCE);
        b.setAvailableForTrade(START_BALANCE);
        b.setExchange("BINANCE");
        b.setCoin("USDT");

        balanceMap.put("USDT", b);

        Balance b2 = new Balance();
        b2.setBalanceTotal(START_BALANCE);
        b2.setAvailableForWithdraw(START_BALANCE);
        b2.setAvailableForTrade(START_BALANCE);
        b2.setExchange("BITMEX");
        b2.setCoin("XBt");

        balanceMap.put("XBt", b2);

    }

    public void setTimestampAndPrice(long curTimestamp, double curPrice) {
        this.curTimestamp = curTimestamp;
        this.curPrice = curPrice;
        repository.setCurrentTimestamp(curTimestamp);
        orderBookCache.setCurPrice(curPrice);

        TradeEvent e = new TradeEvent("SIMUL", "SIMULSYMBOL", curPrice, curTimestamp, 0, null, null, null);
        tradingEngine.onTradeEvent(e);
    }

    @Override
    public OrderInfo placeOrder(OrderInfo orderInfo) {
        if(orderInfo.getStopPrice() > 0) {
            orderInfo.setOrderStatus(OrderStatus.PENDING);
            return orderInfo;
        }

        orderInfo.setAmountExecuted(orderInfo.getAmount());
        orderInfo.setOrderStatus(OrderStatus.COMPLETE);
        orderInfo.setExecTimestamp(this.curTimestamp);
        if(orderInfo.getOrderSide() == OrderSide.BUY) {
            double trailingStopPrice = tradingEngine.getTrailingStopPrice();
            if(trailingStopPrice > 0) {
                orderInfo.setPriceExecuted(trailingStopPrice * (1+SLIPPAGE));
            } else {
                double stopLossPrice = tradingEngine.getStopLossPrice();
                if(stopLossPrice < Double.MAX_VALUE) {
                    orderInfo.setPriceExecuted(stopLossPrice * (1+SLIPPAGE));
                } else {
                    orderInfo.setPriceExecuted(orderInfo.getPrice() * (1+SLIPPAGE));
                }
            }
        } else {
            double trailingStopPrice = tradingEngine.getTrailingStopPrice();
            if(trailingStopPrice > 0.0) {
                orderInfo.setPriceExecuted(trailingStopPrice * (1-SLIPPAGE));
            } else {
                double stopLossPrice = tradingEngine.getStopLossPrice();
                if(stopLossPrice < Double.MAX_VALUE) {
                    orderInfo.setPriceExecuted(stopLossPrice * (1-SLIPPAGE));
                } else {
                    orderInfo.setPriceExecuted(orderInfo.getPrice() * (1-SLIPPAGE));
                }
            }
        }
        return orderInfo;
    }

    @Override
    public OrderInfo cancelOrder(OrderInfo orderInfo) {
        return null;
    }

    @Override
    public OrderInfo getOrder(OrderInfo orderInfo) {
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
        return null;
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
