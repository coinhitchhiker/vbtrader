package com.coinhitchhiker.vbtrader.common.model;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TradingWindow {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradingWindow.class);

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

    private TradeEvent prevTradeEvent;

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
        }

        this.prevTradeEvent = e;
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

    public boolean isDownCandle() {
        return this.closePrice < this.openPrice;
    }

    public boolean isUpCandle() {
        return !isDownCandle();
    }

    public double tailLengthRatio() {
        if(isDownCandle()) {
            return (this.closePrice - this.lowPrice) / (this.highPrice - this.lowPrice);
        } else {
            return (this.openPrice - this.lowPrice) / (this.highPrice - this.lowPrice);
        }
    }

    public double headLengthRatio() {
        if(isDownCandle()) {
            return (this.highPrice - this.openPrice) / (this.highPrice - this.lowPrice);
        } else {
            return (this.highPrice - this.closePrice) / (this.highPrice - this.lowPrice);
        }
    }

    //----------------------------------------------------------------------------------------------------------------

    public List<Candle> getCandles() {
        return candles;
    }

    public void setCandles(List<Candle> candles) {
        this.candles = candles;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    @Override
    public String toString() {
        return "TradingWindow{" +
                "symbol='" + symbol + '\'' +
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
                ", prevTradeEvent=" + prevTradeEvent +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradingWindow that = (TradingWindow) o;
        return startTimeStamp == that.startTimeStamp &&
                endTimeStamp == that.endTimeStamp &&
                Double.compare(that.openPrice, openPrice) == 0 &&
                Double.compare(that.highPrice, highPrice) == 0 &&
                Double.compare(that.closePrice, closePrice) == 0 &&
                Double.compare(that.lowPrice, lowPrice) == 0 &&
                Double.compare(that.volume, volume) == 0 &&
                Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, startTimeStamp, endTimeStamp, openPrice, highPrice, closePrice, lowPrice, volume);
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
