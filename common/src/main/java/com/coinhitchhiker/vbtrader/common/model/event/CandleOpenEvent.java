package com.coinhitchhiker.vbtrader.common.model.event;

import com.coinhitchhiker.vbtrader.common.model.Candle;
import com.coinhitchhiker.vbtrader.common.model.TimeFrame;

public class CandleOpenEvent {

    private final TimeFrame timeFrame;
    private final Candle newCandle;

    public CandleOpenEvent(TimeFrame timeFrame, Candle newCandle) {
        this.timeFrame = timeFrame;
        this.newCandle = newCandle;
    }

    public TimeFrame getTimeFrame() {
        return timeFrame;
    }

    public Candle getNewCandle() {
        return newCandle;
    }
}
