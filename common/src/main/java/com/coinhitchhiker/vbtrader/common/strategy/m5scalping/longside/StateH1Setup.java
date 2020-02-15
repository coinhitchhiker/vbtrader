package com.coinhitchhiker.vbtrader.common.strategy.m5scalping.longside;

import com.coinhitchhiker.vbtrader.common.model.Chart;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import static org.joda.time.DateTimeZone.UTC;

public class StateH1Setup implements State {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateH1Setup.class);

    private final Chart m5chart;
    private final Chart h1chart;
    private final M5ScalpingLongEngine engine;
    private final ApplicationEventPublisher publisher;

    public static final String M5_21EMA = "m5_21ema";
    public static final String M5_13EMA = "m5_13ema";
    public static final String M5_8EMA = "m5_8ema";

    private static final String H1_21EMA = "h1_21ema";
    private static final String H1_8EMA = "h1_8ema";

    public StateH1Setup(M5ScalpingLongEngine engine, ApplicationEventPublisher publisher) {
        this.engine = engine;
        this.m5chart = engine.getM5Chart();
        this.h1chart = engine.getH1Chart();
        this.publisher = publisher;
    }

    @Override
    public StateEnum getStateEnum() {
        return StateEnum.H1SETUUP;
    }

    @Override
    public State nextState(double curPrice, long curTimestamp, double curVol) {
        if(engine.m5PullbackDetected()) {
            LOGGER.info("[Transition into StateORDERPLACED]");
            LOGGER.info("[Transition into StateORDERPLACED] curTimestamp {}", new DateTime(curTimestamp, UTC));
            LOGGER.info("[Transition into StateORDERPLACED]");
            return new StateORDERPLACED(engine, publisher);
        } else if(!engine.isH1LongTrending()) {
            LOGGER.info("[GOING BACK TO StateINIT]");
            LOGGER.info("[GOING BACK TO StateINIT] curTimestamp {}", new DateTime(curTimestamp, UTC));
            LOGGER.info("[GOING BACK TO StateINIT]");
            return new StateINIT(engine, publisher);
        } else {
            return this;
        }
    }
}
