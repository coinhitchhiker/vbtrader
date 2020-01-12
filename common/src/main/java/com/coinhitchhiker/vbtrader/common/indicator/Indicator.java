package com.coinhitchhiker.vbtrader.common.indicator;

import com.coinhitchhiker.vbtrader.common.model.Candle;

import java.util.List;

public interface Indicator {

    IndicatorType getIndicatorType();
    String getName();
    void onTick(List<Candle> candles);
    Object getKeyValues();

}
