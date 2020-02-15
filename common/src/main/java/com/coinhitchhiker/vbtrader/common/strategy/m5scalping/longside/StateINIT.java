package com.coinhitchhiker.vbtrader.common.strategy.m5scalping.longside;

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
        if(engine.isH1LongTrending()) {
            return new StateH1Setup(engine, publisher);
        } else {
            return this;
        }
    }

    @Override
    public StateEnum getStateEnum() {
        return StateEnum.INIT;
    }

}
