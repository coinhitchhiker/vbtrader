package com.coinhitchhiker.vbtrader.common.strategy.m5scalping.longside;

import com.coinhitchhiker.vbtrader.common.model.Exchange;

public interface State {

    State nextState(double curPrice, long curTimestamp, double curVol);

    StateEnum getStateEnum();

    default void enter(Exchange exchange, String symbol) {
        return;
    }

}
