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
        repository.getCurrentTradingWindow(curTimestamp).updateWindowData(e);
    }

    @Override
    public OrderInfo placeOrder(OrderInfo orderInfo) {
        orderInfo.setAmountExecuted(orderInfo.getAmount());
        orderInfo.setOrderStatus(OrderStatus.COMPLETE);
        orderInfo.setExecTimestamp(this.curTimestamp);
        if(orderInfo.getOrderSide() == OrderSide.BUY) {
            double trailingStopPrice = repository.getCurrentTradingWindow(curTimestamp).getTrailingStopPrice();
            if(trailingStopPrice > 0) {
                orderInfo.setPriceExecuted(trailingStopPrice * (1+SLIPPAGE));
            } else {
                double stopLossPrice = repository.getCurrentTradingWindow(curTimestamp).getStopLossPrice();
                if(stopLossPrice > 0) {
                    orderInfo.setPriceExecuted(stopLossPrice * (1+SLIPPAGE));
                } else {
                    orderInfo.setPriceExecuted(orderInfo.getPrice() * (1+SLIPPAGE));
                }
            }
        } else {
            double tralingStopPrice = repository.getCurrentTradingWindow(curTimestamp).getTrailingStopPrice();
            if(tralingStopPrice > 0.0) {
                orderInfo.setPriceExecuted(tralingStopPrice * (1-SLIPPAGE));
            } else {
                double stopLossPrice = repository.getCurrentTradingWindow(curTimestamp).getStopLossPrice();
                if(stopLossPrice > 0) {
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
        return null;
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
}
