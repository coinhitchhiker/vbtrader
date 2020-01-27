package com.coinhitchhiker.vbtrader.common.model;

import org.joda.time.DateTime;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.joda.time.DateTimeZone.UTC;

public class Candle implements Serializable {
    //symbol interval openTime closeTime openPrice highPrice lowPrice closePrice volume
    private String symbol;
    private String interval;
    private long openTime;    // milli
    private long closeTime;
    private double openPrice;
    private double highPrice;
    private double lowPrice;
    private double closePrice;
    private double volume;
    private double pvt;
    private double obv;

    public Candle(String symbol, String interval, long openTime, long closeTime, double openPrice, double highPrice, double lowPrice, double closePrice, double volume) {
        this.symbol = symbol;
        this.interval = interval;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }

    public static Candle fromBinanceCandle(String symbol, String interval, List<Object> data) {

        long openTime = ((Double) data.get(0)).longValue();
        long closeTime = ((Double) data.get(6)).longValue();
        double open = Double.valueOf((String) data.get(1));
        double high = Double.valueOf((String) data.get(2));
        double low = Double.valueOf((String) data.get(3));
        double close = Double.valueOf((String) data.get(4));
        double volume = Double.valueOf((String) data.get(5));

        return new Candle(symbol, interval, openTime, closeTime, open, high, low, close, volume);
    }

    public static Candle fromBitMexCandle(String symbol, String interval, Map<String, Object> data) {
        long openTime = new DateTime((String)data.get("timestamp"), UTC).getMillis();
        long closeTime = new DateTime((String)data.get("timestamp"), UTC).plusSeconds(59).getMillis();
        double open = (double)data.get("open");
        double high = (double)data.get("high");
        double low = (double)data.get("low");
        double close = (double)data.get("close");
        double volume = (double)data.get("volume");

        return new Candle(symbol, interval, openTime, closeTime, open, high, low, close, volume);
    }

    public static Candle fromOKExCandle(String symbol, int intervalSec, List<Object> data) {
        long openTime = ((Double) data.get(0)).longValue();
        long closeTime = openTime + intervalSec * 1000 - 1;
        double open = Double.valueOf((String)data.get(1));
        double high = Double.valueOf((String)data.get(2));
        double low = Double.valueOf((String)data.get(3));
        double close = Double.valueOf((String)data.get(4));
        double volume = Double.valueOf((String)data.get(5));

        return new Candle(symbol, String.valueOf(intervalSec), openTime, closeTime, open, high, low, close, volume);
    }

    public Candle clone() {
        return new Candle(this.symbol, this.interval, this.openTime, this.closeTime, this.openPrice, this.highPrice, this.lowPrice, this.closePrice, this.volume);
    }

    //-----------------------------------------------------

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public long getOpenTime() {
        return openTime;
    }

    public void setOpenTime(long openTime) {
        this.openTime = openTime;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(long closeTime) {
        this.closeTime = closeTime;
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

    public double getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(double lowPrice) {
        this.lowPrice = lowPrice;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(double closePrice) {
        this.closePrice = closePrice;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public double getPvt() {
        return pvt;
    }

    public void setPvt(double pvt) {
        this.pvt = pvt;
    }

    public double getObv() {
        return obv;
    }

    public void setObv(double obv) {
        this.obv = obv;
    }

    @Override
    public String toString() {
        return "Candle{" +
                "symbol='" + symbol + '\'' +
                ", interval='" + interval + '\'' +
                ", openTime=" + new DateTime(openTime, UTC) +
                ", closeTime=" + new DateTime(closeTime, UTC) +
                ", openPrice=" + openPrice +
                ", highPrice=" + highPrice +
                ", lowPrice=" + lowPrice +
                ", closePrice=" + closePrice +
                ", volume=" + volume +
                ", pvt=" + pvt +
                ", obv=" + obv +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Candle candle = (Candle) o;
        return openTime == candle.openTime &&
                closeTime == candle.closeTime &&
                Objects.equals(symbol, candle.symbol) &&
                Objects.equals(interval, candle.interval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, interval, openTime, closeTime);
    }
}
