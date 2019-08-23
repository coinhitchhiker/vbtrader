package com.coinhitchhiker.vbtrader.common.strategy;

import java.util.Map;

public interface Strategy {

    double sellSignalStrength(Map<String, Object> params);

    double buySignalStrength(Map<String, Object> params);

    default void buildMinuteTechnicalIndicator(Map<String, Object> params) {
        return;
    }

    default boolean checkPrecondition(Map<String, Object> params) {
        return false;
    }

}
