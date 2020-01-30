package com.coinhitchhiker.vbtrader.common.indicator;

import com.coinhitchhiker.vbtrader.common.model.Candle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EMA implements Indicator<Double> {

    private final String name;
    private final int length;
    private final double alpha;

    private List<Double> values = new ArrayList<>();
    private Map<Long, Double> keyValues = new LinkedHashMap<>();
    private long oldTimestamp = 0;

    public EMA(String name, int length) {
        this.name = name;
        this.length = length;
        this.alpha = 2. / (length + 1.);
    }

    @Override
    public String getName() {
        return name;
    }

    //https://en.wikipedia.org/wiki/Moving_average#Exponential_moving_average
    //http://www.javased.com/index.php?source_dir=trademaker/src/org/lifeform/chart/indicator/EMA.java
    @Override
    public void onTick(List<Candle> candles) {
        int size = candles.size();
        long curTimestamp = candles.get(size-1).getOpenTime();

        int lastBar = size - 1;
        int firstBar = lastBar - 5*length + 1;
        if(firstBar < 0) return;

        double ema = candles.get(firstBar).getClosePrice();

        for(int bar = firstBar; bar <= lastBar; bar++) {
            double barClose = candles.get(bar).getClosePrice();
            ema = ema + alpha * (barClose - ema);
        }

        if(oldTimestamp < curTimestamp) {
            this.values.add(ema);
            this.keyValues.put(curTimestamp, ema);
        } else {
            this.values.remove(this.values.size()-1);
            this.values.add(ema);
            this.keyValues.put(curTimestamp, ema);
        }

        this.oldTimestamp = curTimestamp;
    }

    @Override
    public Double getValueReverse(int index) {
        return this.values.get(this.values.size() - index - 1);
    }

    @Override
    public Double getValue(int index) {
        return this.values.get(index);
    }
}
