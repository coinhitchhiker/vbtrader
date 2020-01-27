package com.coinhitchhiker.vbtrader.common.indicator;

import com.coinhitchhiker.vbtrader.common.model.Candle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// https://searchcode.com/codesearch/view/64893970/
public class HullMovingAverage implements Indicator<Double> {

    private final String name;
    private final int length;

    private List<Double> values = new ArrayList<>();
    private Map<Long, Double> keyValues = new LinkedHashMap<>();

    public HullMovingAverage(String name, int length) {
        if(length <= 0) throw new RuntimeException("Invalid length " + length);

        this.name = name;
        this.length = length;
    }

    @Override
    public String getName() {
        return name;
    }

    /*
     * private utility to compute the weighted moving average of an array of
     * doubles, of given length, ending at the given position of the array
     */
    private double WMA(final double[] values, final int end, int len) {
        if (len <= 1) // special cases, return the element at end
            return values[end];

        int start = end - len + 1;
        if (start < 0) { // update start pos if there are not enough elements
            start = 0;
            len = end + 1;
        }

        double wma = 0;
        double count = 1;

        for (int bar = start; bar <= end; bar++) {
            wma += values[bar] * (count++);
        }

        return wma / (len * (len + 1) / 2);
    }

    @Override
    public void onTick(List<Candle> candles) {
        int size = candles.size();
        if (size == 0) return;

        // in the beginning we don't have enough data, so use only what's
        // already there in the quote history
        int len = length < size ? length : size;

        int sqrlen = (int) Math.sqrt(len);
        if (sqrlen < 1) sqrlen = 1;

        // we need len + sqrlen number of data points for the rest of the
        // algorithm to work
        int arraysize = len + sqrlen;   // or less if we don't have that much yet
        if (arraysize > size) arraysize = size;

        // copy the needed number of price data to an array of doubles
        // to speed up computation
        double[] values = new double[arraysize];
        for (int i = 0; i < values.length; i++) {
            values[i] = candles.get(size - arraysize + i).getClosePrice();
        }

        double[] wmavalues = new double[sqrlen];

        // calculate 2*WMA(period/2) - WMA(period) for sequences ending
        // at now, now-1, now-2, ..., now-sqrlen
        for (int i = 0; i < sqrlen; i++) {
            double dfull = WMA(values, arraysize - 1 - i, len); // full WMA
            double dhalf = WMA(values, arraysize - 1 - i, (int)Math.round(len*1.0 / 2)); // half len
            // store the WMA diff values in a temp array
            wmavalues[sqrlen - 1 - i] = 2 * dhalf - dfull;
        }

        // do another WMA on the values calculated above for smoothing
        double value = WMA(wmavalues, sqrlen - 1, sqrlen);

        Double prevVal = this.keyValues.put(candles.get(size-1).getOpenTime(), value);
        if(prevVal == null) {
            this.values.add(this.keyValues.get(candles.get(size-1).getOpenTime()));
        } else {
            this.values.remove(this.values.size()-1);
            this.values.add(value);
        }
    }

    @Override
    //0 = current, 1 = past 1, 2 = past 2
    public Double getValueReverse(int index) {
        return this.values.get(this.values.size() - index - 1);
    }

    @Override
    public Double getValue(int index) {
        return this.values.get(index);
    }
}
