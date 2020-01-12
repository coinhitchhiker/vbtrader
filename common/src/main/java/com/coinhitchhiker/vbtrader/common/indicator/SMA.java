package com.coinhitchhiker.vbtrader.common.indicator;

import com.coinhitchhiker.vbtrader.common.model.Candle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SMA implements Indicator {

    private final int length;
    private final String name;

    private Map<Long, Double> keyValues = new LinkedHashMap<>();

    public SMA(String name, int length) {
        if(length <= 0) throw new RuntimeException("Invalid SMA length");

        this.length = length;
        this.name = name;
    }

    @Override
    public IndicatorType getIndicatorType() {
        return IndicatorType.SMA;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void onTick(List<Candle> candles) {
        int size = candles.size();
        int cnt = length < size ? length : size;
        if(cnt == 0) return;

        double sum = 0;
        for(int i = 0; i < cnt; i++) {
            sum += candles.get(size-(i+1)).getClosePrice();
        }
        double sma = sum / cnt;
        keyValues.put(candles.get(size-1).getOpenTime(), sma);
    }

    @Override
    public Object getKeyValues() {
        return this.keyValues;
    }

}
