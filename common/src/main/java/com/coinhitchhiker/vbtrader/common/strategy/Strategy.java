package com.coinhitchhiker.vbtrader.common.strategy;

import java.util.Map;

public interface Strategy {

    double sellSignalStrength(Map<String, Object> params);

    double buySignalStrength(Map<String, Object> params);

}
