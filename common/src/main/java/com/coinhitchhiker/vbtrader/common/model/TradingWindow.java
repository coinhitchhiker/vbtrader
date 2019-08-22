package com.coinhitchhiker.vbtrader.common.model;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TradingWindow {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradingWindow.class);

    private double TS_TRIGGER_PCT = 0.7D; // trailing when 0.7% profit is gained (default)
    private double TS_PCT = 0.2D; // run trailing stop order when 0.2% loss from highest price (default)

    private final String symbol;
    private final long startTimeStamp;    // unix epoch in millis
    private final long endTimeStamp;

    private double openPrice;
    private double highPrice;
    private double closePrice;
    private double lowPrice;
    private double volume;
    private double sellVolume;
    private double buyVolume;

    private long curTimeStamp;

    private OrderInfo buyOrder;
    private OrderInfo sellOrder;
    private double trailingStopPrice;
    private double stopLossPrice;
    private TradeEvent prevTradeEvent;

    private double buyFee;
    private double sellFee;
    private double profit;
    private double netProfit;

    private List<Candle> candles;

    private TradingWindow prevWindow;

    public TradingWindow(String symbol, long startTimeStamp, long endTimeStamp, double openPrice, double highPrice, double closePrice, double lowPrice, double volume) {
        this.symbol = symbol;
        this.startTimeStamp = startTimeStamp;
        this.endTimeStamp = endTimeStamp;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.closePrice = closePrice;
        this.lowPrice = lowPrice;
        this.volume = volume;
    }

    public TradingWindow(String symbol, long startTimeStamp, long endTimeStamp, double openPrice) {
        this.symbol = symbol;
        this.startTimeStamp = startTimeStamp;
        this.endTimeStamp = endTimeStamp;
        this.openPrice = openPrice;
        this.lowPrice = openPrice;
        this.highPrice = openPrice;
        this.closePrice = openPrice;
    }

    public static TradingWindow of(List<Candle> candles) {
        int tempListSize = candles.size();

        String symbol = candles.get(0).getSymbol();
        long openTime = candles.get(0).getOpenTime();
        long closeTime = candles.get(tempListSize-1).getCloseTime();
        double openPrice = candles.get(0).getOpenPrice();
        double highPrice = candles.stream().mapToDouble(Candle::getHighPrice).max().getAsDouble();
        double lowPrice = candles.stream().mapToDouble(Candle::getLowPrice).min().getAsDouble();
        double closePrice = candles.get(tempListSize-1).getClosePrice();
        double volume = candles.stream().mapToDouble(Candle::getVolume).sum();

        TradingWindow tw = new TradingWindow(symbol, openTime, closeTime, openPrice, highPrice, closePrice, lowPrice, volume);
        tw.setCandles(new ArrayList<>(candles));
        return tw;
    }

    public double getNoiseRatio() {
        return  1 - Math.abs(openPrice - closePrice) / (highPrice - lowPrice);
    }

    public boolean isBetween(long timestamp) {
        return startTimeStamp <= timestamp && timestamp <= endTimeStamp;
    }

    public double getRange() {
        return highPrice - lowPrice;
    }

    public void updateWindowData(TradeEvent e) {
        if(this.highPrice < e.getPrice()) this.highPrice = e.getPrice();
        if(this.lowPrice > e.getPrice()) this.lowPrice = e.getPrice();
        this.closePrice = e.getPrice();
        this.curTimeStamp = e.getTradeTime();
        this.volume += e.getAmount();

        if(this.prevTradeEvent != null) {
            if(e.getPrice() > this.prevTradeEvent.getPrice()) {
                this.buyVolume += e.getAmount();
            } else if(e.getPrice() < this.prevTradeEvent.getPrice()) {
                this.sellVolume += e.getAmount();
            }
            this.updateTrailingStop(e.getPrice());
            this.updateStopLoss(e.getPrice());
        }

        this.prevTradeEvent = e;
    }

    private void updateStopLoss(double curPrice) {
        if(this.buyOrder != null) {
            double buyPriceExecuted = buyOrder.getPriceExecuted();
            if(this.stopLossPrice == 0) {
                double stopLossPrice = buyPriceExecuted * (1 + (0.045 + 0.045 + 0.1)/100.0);
                if(curPrice > stopLossPrice) {
                    this.stopLossPrice = stopLossPrice;
                }
            }
        }

        if(this.sellOrder != null) {
            double sellPriceExecuted = sellOrder.getPriceExecuted();
            if(this.stopLossPrice == 0) {
                double stopLossPrice = sellPriceExecuted * (1 - (0.045 + 0.045 + 0.1)/100.0);
                if(curPrice < stopLossPrice) {
                    this.stopLossPrice = stopLossPrice;
                }
            }
        }
    }

    private void updateTrailingStop(double curPrice) {
        double prevPrice = this.prevTradeEvent.getPrice();

        if(this.buyOrder != null) {
            if(this.trailingStopPrice == 0) {
                if(curPrice > buyOrder.getPriceExecuted() * (100.0 + TS_TRIGGER_PCT)/100) {
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

        if(this.sellOrder != null) {
            if(this.trailingStopPrice == 0) {
                if(curPrice < sellOrder.getPriceExecuted() * (100.0 - TS_TRIGGER_PCT)/100.0) {
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
    }

    public TradeEvent getPrevTradeEvent() {
        return prevTradeEvent;
    }

    public void clearOutOrders() {
        this.buyFee = 0.0D;
        this.buyOrder = null;
        this.netProfit = 0.0D;
        this.profit = 0.0D;
        this.sellFee = 0.0D;
        this.sellOrder = null;
        this.trailingStopPrice = 0.0D;
        this.stopLossPrice = 0.0D;
    }

    // for simulation use
    public double getCurTradingWindowVol(long currentTimestamp) {
        double volume = 0.0D;
        for(Candle candle : candles) {
            if(candle.getCloseTime() < currentTimestamp) {
                volume += candle.getVolume();
            }
        }
        return volume;
    }

    //----------------------------------------------------------------------------------------------------------------

    public double getTrailingStopPrice() {
        return trailingStopPrice;
    }

    public void setBuyOrder(OrderInfo orderInfo) {
        this.buyOrder = orderInfo;
    }

    public OrderInfo getBuyOrder() {
        return this.buyOrder;
    }

    public OrderInfo getSellOrder() {
        return sellOrder;
    }

    public void setSellOrder(OrderInfo sellOrder) {
        this.sellOrder = sellOrder;
    }

    public double getBuyFee() {
        return buyFee;
    }

    public void setBuyFee(double buyFee) {
        this.buyFee = buyFee;
    }

    public double getSellFee() {
        return sellFee;
    }

    public void setSellFee(double sellFee) {
        this.sellFee = sellFee;
    }

    public double getProfit() {
        return profit;
    }

    public void setProfit(double profit) {
        this.profit = profit;
    }

    public double getNetProfit() {
        return netProfit;
    }

    public void setNetProfit(double netProfit) {
        this.netProfit = netProfit;
    }

    public List<Candle> getCandles() {
        return candles;
    }

    public void setCandles(List<Candle> candles) {
        this.candles = candles;
    }

    public double getTS_TRIGGER_PCT() {
        return TS_TRIGGER_PCT;
    }

    public void setTS_TRIGGER_PCT(double TS_TRIGGER_PCT) {
        this.TS_TRIGGER_PCT = TS_TRIGGER_PCT;
    }

    public double getTS_PCT() {
        return TS_PCT;
    }

    public void setTS_PCT(double TS_PCT) {
        this.TS_PCT = TS_PCT;
    }

    @Override
    public String toString() {
        return "TradingWindow{" +
                "TS_TRIGGER_PCT=" + TS_TRIGGER_PCT +
                ", TS_PCT=" + TS_PCT +
                ", symbol='" + symbol + '\'' +
                ", startTimeStamp=" + new DateTime(startTimeStamp, DateTimeZone.UTC).toString() +
                ", endTimeStamp=" + new DateTime(endTimeStamp, DateTimeZone.UTC).toString() +
                ", openPrice=" + openPrice +
                ", highPrice=" + highPrice +
                ", closePrice=" + closePrice +
                ", lowPrice=" + lowPrice +
                ", volume=" + volume +
                ", sellVolume=" + sellVolume +
                ", buyVolume=" + buyVolume +
                ", curTimeStamp=" + new DateTime(curTimeStamp, DateTimeZone.UTC).toString() +
                ", buyOrder=" + buyOrder +
                ", sellOrder=" + sellOrder +
                ", trailingStopPrice=" + trailingStopPrice +
                ", prevTradeEvent=" + prevTradeEvent +
                ", buyFee=" + buyFee +
                ", sellFee=" + sellFee +
                ", profit=" + profit +
                ", netProfit=" + netProfit +
                '}';
    }

//-----------------------------------------------------------------------------------

    public String getSymbol() {
        return symbol;
    }

    public long getStartTimeStamp() {
        return startTimeStamp;
    }

    public long getEndTimeStamp() {
        return endTimeStamp;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(double openPrice) {
        this.openPrice = openPrice;
    }

    public double getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(double highPrice) {
        this.highPrice = highPrice;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(double closePrice) {
        this.closePrice = closePrice;
    }

    public double getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(double lowPrice) {
        this.lowPrice = lowPrice;
    }

    public double getVolume() {
        return volume;
    }

    public double getStopLossPrice() {
        return stopLossPrice;
    }

    public void setStopLossPrice(double stopLossPrice) {
        this.stopLossPrice = stopLossPrice;
    }
}
