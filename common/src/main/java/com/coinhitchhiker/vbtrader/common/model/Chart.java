package com.coinhitchhiker.vbtrader.common.model;

import com.coinhitchhiker.vbtrader.common.Util;
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
            throw new RuntimeException("Duplicate indicator name : " + indiName);
        }
        this.indicators.add(indicator);
    }

    private Candle nextCandle(double curPrice, long curTimestamp, double curVol) {
        DateTime timestamp = new DateTime(curTimestamp, DateTimeZone.UTC);
        long open = timestamp.minuteOfDay().roundFloorCopy().getMillis();
        long close = open + timeframe.toSeconds() * 1000 - 1;
        return new Candle(symbol, timeframe.toString(), open, close, curPrice, curPrice, curPrice, curPrice, curVol);
    }

    public Candle onTick(double curPrice, long curTimestamp, double curVol) {
        Candle newCandle = null;
        if(candles.size() == 0) {
            newCandle = nextCandle(curPrice, curTimestamp, curVol);
            this.candles.add(newCandle);
        } else {
            Candle curCandle = this.candles.get(this.candles.size() - 1);
            if(curTimestamp > curCandle.getCloseTime()) {
                newCandle = nextCandle(curPrice, curTimestamp, curVol);
                this.candles.add(newCandle);
            } else {
                curCandle.setClosePrice(curPrice);
                if(curCandle.getHighPrice() < curPrice) curCandle.setHighPrice(curPrice);
                if(curCandle.getLowPrice() > curPrice) curCandle.setLowPrice(curPrice);
                curCandle.setVolume(curCandle.getVolume() + curVol);
            }
        }

        this.indicators.forEach(i -> i.onTick(this.candles));

        if(newCandle != null) {
            return newCandle.clone();
        } else {
            return null;
        }
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

    public Candle getCandleReverse(int index) {
        return this.candles.get(this.candles.size() - index - 1);
    }

    public boolean isNBullishCandles(int startIndex, int lastNCandles) {
        boolean result = true;
        for(int i = startIndex; i <= lastNCandles; i++ ) {
            result &= getCandleReverse(i).isBullishCandle();
        }
        return result;
    }

    public double getLocalSwingHigh(int startIndex, int lastNCandles) {
        double result = 0;
        for(int i = startIndex; i <= lastNCandles; i++ ) {
            result = Util.greatest(result, getCandleReverse(i).getHighPrice());
        }
        return result;
    }

    public boolean isBullishEngulfer() {
        Candle prev1 = getCandleReverse(1);
        Candle prev2 = getCandleReverse(2);

        if((prev2.isBearishCandle() && prev1.isBullishCandle()) &&
            (prev2.getOpenPrice() < prev1.getClosePrice()) &&
            (prev2.getBodyLength() < prev1.getBodyLength() * 0.7)) {
            return true;
        }
        return false;
    }

}
