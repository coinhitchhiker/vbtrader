package com.coinhitchhiker.vbtrader.common;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.ArrayList;
import java.util.List;

public class TradingWindow {

    private static final double TS_TRIGGER_PCT = 0.7/100D; // 1% profit
    private static final double TS = 0.2/100D; // fire TS when 0.25% loss

    private final String symbol;
    private final long startTimeStamp;    // unix epoch in millis
    private final long endTimeStamp;

    private double openPrice;
    private double highPrice;
    private double closePrice;
    private double lowPrice;
    private double volume;

    private long curTimeStamp;

    private OrderInfo buyOrder;
    private OrderInfo sellOrder;
    private double trailingStopPrice;
    private double prevPrice;

    private double buyFee;
    private double sellFee;
    private double profit;
    private double netProfit;

    private List<Candle> candles;

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

    public double getNoiseRatio() {
        return  1 - Math.abs(openPrice - closePrice) / (highPrice - lowPrice);
    }

    public boolean isBetween(long timestamp) {
        return startTimeStamp <= timestamp && timestamp <= endTimeStamp;
    }

    public double getRange() {
        return highPrice - lowPrice;
    }

    public boolean isBuySignal(double curPrice, double k, TradingWindow prevTradingWindow) {
        if(highPrice == 0 || lowPrice == 0) return false;

        return  curPrice > openPrice + k * prevTradingWindow.getRange();
    }

    public boolean isSellSignal(double curPrice, double k, TradingWindow prevTradingWindow) {
        if(highPrice == 0 || lowPrice == 0) return false;

        return curPrice < openPrice - k * prevTradingWindow.getRange();
    }

    public void updateWindowData(TradeEvent e) {

        if(this.highPrice < e.getPrice()) this.highPrice = e.getPrice();
        if(this.lowPrice > e.getPrice()) this.lowPrice = e.getPrice();
        this.closePrice = e.getPrice();

        this.volume += e.getAmount();
        this.curTimeStamp = e.getTradeTime();

    }

    public void priceReceived(double curPrice) {
        if(this.buyOrder != null) {
            if(this.trailingStopPrice == 0) {
                if(curPrice > buyOrder.getPriceExecuted() * (1.0 + TS_TRIGGER_PCT)) {
                    System.out.println(String.format("[LONG] prevPrice=%.2f, curPrice=%.2f, old TS=%.2f, new TS=%.2f", prevPrice, curPrice, this.trailingStopPrice, curPrice * (1.0 - TS)));
                    this.trailingStopPrice = curPrice * (1.0 - TS);
                }
            } else {
                if(curPrice > prevPrice) {
                    System.out.println(String.format("[LONG] prevPrice=%.2f, curPrice=%.2f, old TS=%.2f, new TS=%.2f", prevPrice, curPrice, this.trailingStopPrice, curPrice * (1.0 - TS)));
                    this.trailingStopPrice = curPrice * (1.0 - TS);
                }
            }
        }

        if(this.sellOrder != null) {
            if(this.trailingStopPrice == 0) {
                if(curPrice < sellOrder.getPriceExecuted() * (1.0 - TS_TRIGGER_PCT)) {
                    System.out.println(String.format("[SHORT] prevPrice=%.2f, curPrice=%.2f, old TS=%.2f, new TS=%.2f", prevPrice, curPrice, this.trailingStopPrice, curPrice * (1.0 + TS)));
                    this.trailingStopPrice = curPrice * (1.0 + TS);
                }
            } else {
                if(curPrice < prevPrice) {
                    System.out.println(String.format("[SHORT] prevPrice=%.2f, curPrice=%.2f, old TS=%.2f, new TS=%.2f", prevPrice, curPrice, this.trailingStopPrice, curPrice * (1.0 + TS)));
                    this.trailingStopPrice = curPrice * (1.0 + TS);
                }
            }
        }
        this.prevPrice = curPrice;
    }

    public double getTrailingStopPrice() {
        return trailingStopPrice;
    }

    public double getPrevPrice() {
        return prevPrice;
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

    @Override
    public String toString() {
        return "TradingWindow{" +
                "symbol='" + symbol + '\'' +
                ", curTimestamp=" + new DateTime(curTimeStamp).withZone(DateTimeZone.UTC).toString() +
                ", start=" + new DateTime(startTimeStamp).withZone(DateTimeZone.UTC).toString() +
                ", end=" + new DateTime(endTimeStamp).withZone(DateTimeZone.UTC).toString() +
                ", openPrice=" + openPrice +
                ", highPrice=" + highPrice +
                ", closePrice=" + closePrice +
                ", lowPrice=" + lowPrice +
                ", volume=" + volume +
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
}
