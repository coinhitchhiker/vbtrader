package com.coinhitchhiker.vbtrader.common.strategy.m5scalping.longside;

import com.coinhitchhiker.vbtrader.common.indicator.EMA;
import com.coinhitchhiker.vbtrader.common.model.Candle;
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
        if(checkM5TradeSetup()) {
            LOGGER.info("[Transition into StateORDERPLACED]");
            LOGGER.info("[Transition into StateORDERPLACED] curTimestamp {}", new DateTime(curTimestamp, UTC));
            LOGGER.info("[Transition into StateORDERPLACED]");
            return new StateORDERPLACED(engine, publisher);
        } else if(!checkH1TradeSetup()) {
            LOGGER.info("[GOING BACK TO StateINIT]");
            LOGGER.info("[GOING BACK TO StateINIT] curTimestamp {}", new DateTime(curTimestamp, UTC));
            LOGGER.info("[GOING BACK TO StateINIT]");
            return new StateINIT(engine, publisher);
        } else {
            return this;
        }
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

        // EMA8 increase should be more than 0.1%
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
                return true;
            } else {
                LOGGER.info("[checkH1TradeSetup invalidated]");
                LOGGER.info("[checkH1TradeSetup invalidated]\ncur candle {}", this.h1chart.getCandleReverse(0));
                LOGGER.info("[checkH1TradeSetup invalidated]");
            }
        }

        return false;
    }

    private boolean checkM5TradeSetup() {

        EMA m5_8ema = (EMA)m5chart.getIndicatorByName(M5_8EMA);
        EMA m5_13ema = (EMA)m5chart.getIndicatorByName(M5_13EMA);
        EMA m5_21ema = (EMA)m5chart.getIndicatorByName(M5_21EMA);

        boolean ema_cond1 = m5_8ema.getValueReverse(1) > m5_13ema.getValueReverse(1)  &&
                m5_13ema.getValueReverse(1) > m5_21ema.getValueReverse(1);

        boolean ema_cond2 = m5_8ema.getValueReverse(2) > m5_13ema.getValueReverse(2)  &&
                m5_13ema.getValueReverse(2) > m5_21ema.getValueReverse(2);

        boolean ema_cond3 = m5_8ema.getValueReverse(3) > m5_13ema.getValueReverse(3)  &&
                m5_13ema.getValueReverse(3) > m5_21ema.getValueReverse(3);

        boolean ema_cond4 = m5_8ema.getValueReverse(4) > m5_13ema.getValueReverse(4)  &&
                m5_13ema.getValueReverse(4) > m5_21ema.getValueReverse(4);

        Candle prev1_candle = m5chart.getCandleReverse(1);

        boolean pullback_candle = prev1_candle.getOpenPrice() > prev1_candle.getClosePrice() && // negative candle
                m5_8ema.getValueReverse(1)  > prev1_candle.getLowPrice() &&
                prev1_candle.getClosePrice() > m5_21ema.getValueReverse(1);

        return ema_cond1 && ema_cond2 && ema_cond3 && ema_cond4 && pullback_candle;
    }
}
