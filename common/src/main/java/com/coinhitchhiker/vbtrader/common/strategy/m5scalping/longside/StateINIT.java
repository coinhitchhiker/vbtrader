package com.coinhitchhiker.vbtrader.common.strategy.m5scalping.longside;

import com.coinhitchhiker.vbtrader.common.indicator.EMA;
import com.coinhitchhiker.vbtrader.common.model.Candle;
import com.coinhitchhiker.vbtrader.common.model.Chart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

public class StateINIT implements State {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateINIT.class);

    private final Chart h1chart;
    private final Chart m5chart;
    private final ApplicationEventPublisher publisher;
    private final M5ScalpingLongEngine engine;

    private static final String H1_21EMA = "h1_21ema";
    private static final String H1_8EMA = "h1_8ema";

    public StateINIT(M5ScalpingLongEngine engine, ApplicationEventPublisher publisher) {
        this.engine = engine;
        this.h1chart = engine.getH1Chart();
        this.m5chart = engine.getM5Chart();
        this.publisher = publisher;
    }

    public State nextState(double curPrice, long curTimestamp, double curVol) {
        if(checkH1TradeSetup()) {
            return new StateH1Setup(engine, publisher);
        } else {
            return this;
        }
    }

    @Override
    public StateEnum getStateEnum() {
        return StateEnum.INIT;
    }

    private boolean checkH1TradeSetup() {
        Double ema8_1 = ((EMA)this.h1chart.getIndicatorByName(H1_8EMA)).getValueReverse(1);
        Double ema21_1 = ((EMA)this.h1chart.getIndicatorByName(H1_21EMA)).getValueReverse(1);
        double prev1_diff = ema8_1 - ema21_1;

        Double ema8_2 = ((EMA)this.h1chart.getIndicatorByName(H1_8EMA)).getValueReverse(2);
        Double ema21_2 = ((EMA)this.h1chart.getIndicatorByName(H1_21EMA)).getValueReverse(2);
        double prev2_diff = ema8_2 - ema21_2;

        Double ema8_3 = ((EMA)this.h1chart.getIndicatorByName(H1_8EMA)).getValueReverse(3);
        Double ema21_3 = ((EMA)this.h1chart.getIndicatorByName(H1_21EMA)).getValueReverse(3);
        double prev3_diff = ema8_3 - ema21_3;

        // EMA increase should be more than 0.1%
        if(prev1_diff > 0 && prev2_diff > 0 && prev3_diff > 0 &&
                (ema8_1 - ema8_2)/ema8_2*100 > 0.1 &&
                (ema8_2 - ema8_3)/ema8_3*100 > 0.1
        ) {
            Candle prev1 = this.h1chart.getCandleReverse(1);
            Candle prev2 = this.h1chart.getCandleReverse(2);
            Candle prev3 = this.h1chart.getCandleReverse(3);

            if(prev1.getOpenPrice() > ema8_1 && prev1.getClosePrice() > ema8_1 &&
                    prev2.getOpenPrice() > ema8_2 && prev2.getClosePrice() > ema8_2 &&
                    prev3.getOpenPrice() > ema8_3 && prev3.getClosePrice() > ema8_3
            ) {
                LOGGER.info("[CheckH1TradeSetup]");
                LOGGER.info("[CheckH1TradeSetup]\n[prev2] {}\n[prev1] {}\n[cur] {}", prev2, prev1, this.h1chart.getCandleReverse(0));
                LOGGER.info("[CheckH1TradeSetup]");
                return true;
            }
        }

        return false;
    }

}
