package com.coinhitchhiker.vbtrader.common.indicator;

import com.coinhitchhiker.vbtrader.common.model.Candle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WMA implements Indicator {

    private final int length;
    private final String name;

    private Map<Long, Double> keyValues = new LinkedHashMap<>();

    public WMA(String name, int length) {
        this.name = name;
        this.length = length;
    }

    @Override
    public IndicatorType getIndicatorType() {
        return IndicatorType.WMA;
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
        int start = size - cnt;
        int count = 1;
        for(int i = start; i < size; i++) {
            sum += candles.get(i).getClosePrice() * (count++);
        }
        double wma = sum / (cnt * (cnt+1) / 2);
        keyValues.put(candles.get(size-1).getOpenTime(), wma);
    }

    @Override
    public Object getKeyValues() {
        return this.keyValues;
    }

}
