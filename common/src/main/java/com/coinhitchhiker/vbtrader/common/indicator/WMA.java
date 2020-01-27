package com.coinhitchhiker.vbtrader.common.indicator;

import com.coinhitchhiker.vbtrader.common.model.Candle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WMA implements Indicator<Double> {

    private final int length;
    private final String name;

    private List<Double> values = new ArrayList<>();
    private Map<Long, Double> keyValues = new LinkedHashMap<>();

    public WMA(String name, int length) {
        this.name = name;
        this.length = length;
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
        double value = sum / (cnt * (cnt+1) / 2);

        Double prevVal = this.keyValues.put(candles.get(size-1).getOpenTime(), value);
        if(prevVal == null) {
            this.values.add(this.keyValues.get(candles.get(size-1).getOpenTime()));
        } else {
            this.values.remove(this.values.size()-1);
            this.values.add(value);
        }
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
