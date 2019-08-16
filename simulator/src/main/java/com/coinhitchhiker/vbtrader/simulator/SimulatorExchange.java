package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.*;
import java.util.List;
import java.util.Map;

public class SimulatorExchange implements Exchange {

    private Repository repository;
    private long currentTimestamp;
    private double SLIPPAGE;

    public SimulatorExchange(Repository repository, double SLIPPAGE) {
        this.repository = repository;
        this.SLIPPAGE = SLIPPAGE;
    }

    public void setCurrentTimestamp(long currentTimestamp) {
        this.currentTimestamp = currentTimestamp;
        ((SimulatorRepositoryImpl)repository).setCurrentTimestamp(currentTimestamp);
    }

    @Override
    public OrderInfo placeOrder(OrderInfo orderInfo) {
        orderInfo.setAmountExecuted(orderInfo.getAmount());
        orderInfo.setOrderStatus(OrderStatus.COMPLETE);
        orderInfo.setExecTimestamp(this.currentTimestamp);
        if(orderInfo.getOrderSide() == OrderSide.BUY) {
            orderInfo.setPriceExecuted(orderInfo.getPrice() * (1+SLIPPAGE));
        } else {
            orderInfo.setPriceExecuted(orderInfo.getPrice() * (1-SLIPPAGE));
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
        TradingWindow tw = repository.getCurrentTradingWindow(this.currentTimestamp);
        List<Candle> candles = tw.getCandles();
        for(Candle candle : candles) {
            if(candle.getOpenTime() <= currentTimestamp && currentTimestamp < candle.getCloseTime()) {
                return candle.getClosePrice();
            }
        }
        throw new RuntimeException("unreachable code path");
    }

    @Override
    public Map<String, Balance> getBalance() {
        return null;
    }

    @Override
    public double getBalanceForTrade(String quoteCurrency) {
        return 0;
    }
}
