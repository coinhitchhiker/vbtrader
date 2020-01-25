package com.coinhitchhiker.vbtrader.common.model;

import com.coinhitchhiker.vbtrader.common.indicator.Indicator;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.ArrayList;
import java.util.List;

public class Chart {

    private final TimeFrame timeframe;
    private final String symbol;
    private List<Candle> candles = new ArrayList<>();
    private List<Indicator> indicators = new ArrayList<>();

    private Chart(TimeFrame timeframe, String symbol) {
        this.timeframe = timeframe;
        this.symbol = symbol;
    }

    public static Chart of(TimeFrame timeframe, String symbol) {
        Chart result = new Chart(timeframe, symbol);
        return result;
    }

    public void addIndicator(Indicator indicator) {
        String indiName = indicator.getName();
        if(getIndicatorByName(indiName) != null) {
            throw new RuntimeException("Dupulicate indicator name : " + indiName);
        }
        this.indicators.add(indicator);
    }

    private Candle nextCandle(double curPrice, long curTimestamp, double curVol) {
        DateTime timestamp = new DateTime(curTimestamp, DateTimeZone.UTC);
        long open = timestamp.minuteOfDay().roundFloorCopy().getMillis();
        long close = open + timeframe.toSeconds() * 1000 - 1;
        // public Candle(String symbol, String interval, long openTime, long closeTime, double openPrice, double highPrice, double lowPrice, double closePrice, double volume) {
        return new Candle(symbol, timeframe.toString(), open, close, curPrice, curPrice, curPrice, curPrice, curVol);
    }

    public void onTick(double curPrice, long curTimestamp, double curVol) {
        if(candles.size() == 0) {
            this.candles.add(nextCandle(curPrice, curTimestamp, curVol));
        } else {
            Candle curCandle = this.candles.get(this.candles.size() - 1);
            if(curTimestamp > curCandle.getCloseTime()) {
                this.candles.add(nextCandle(curPrice, curTimestamp, curVol));
            } else {
                curCandle.setClosePrice(curPrice);
                if(curCandle.getHighPrice() < curPrice) curCandle.setHighPrice(curPrice);
                if(curCandle.getLowPrice() > curPrice) curCandle.setLowPrice(curPrice);
                curCandle.setVolume(curCandle.getVolume() + curVol);
            }
        }

        this.indicators.forEach(i -> i.onTick(this.candles));
    }

    public Indicator getIndicatorByName(String name) {
        for(Indicator indicator : this.indicators) {
            String result = indicator.getName();
            if(result.equals(name)) {
                return indicator;
            }
        }
        return null;
    }

    public List<Candle> getCandles() {
        return candles;
    }
}
