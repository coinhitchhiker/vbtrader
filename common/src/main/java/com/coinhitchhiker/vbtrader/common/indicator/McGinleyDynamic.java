package com.coinhitchhiker.vbtrader.common.indicator;

import com.coinhitchhiker.vbtrader.common.model.Candle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McGinleyDynamic implements Indicator<Double> {

    private final double K = 0.6;

    private String name;
    private int length;

    private List<Double> values = new ArrayList<>();
    private Map<Long, Double> keyValues = new LinkedHashMap<>();

    private Indicator<Double> ema;

    public McGinleyDynamic(String name, int length) {
        this.name = name;
        this.length = length;

        ema = new EMA("ema", length);
    }

    @Override
    public String getName() {
        return name;
    }


    //https://www.investopedia.com/articles/forex/09/mcginley-dynamic-indicator.asp
    //http://www2.wealth-lab.com/WL5Wiki/McGinleyDynamic.ashx
    @Override
    public void onTick(List<Candle> candles) {

        this.ema.onTick(candles);

        int size = candles.size();

        double closePrice = candles.get(size-1).getClosePrice();

        double ema_prev = this.ema.getValueReverse(0);
        if(ema_prev == 0.0D) return;        // this means EMA hasn't received enough data to build. wait for more data...

        double value = ema_prev + ((closePrice - ema_prev) / (closePrice / ema_prev * 125));

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
