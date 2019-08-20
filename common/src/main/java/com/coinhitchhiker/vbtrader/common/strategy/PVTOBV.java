package com.coinhitchhiker.vbtrader.common.strategy;

import com.coinhitchhiker.vbtrader.common.model.Candle;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class PVTOBV {

    private static final Logger LOGGER = LoggerFactory.getLogger(PVTOBV.class);

    private final int PVT_LOOK_BEHIND_SIZE = 10;    // to find out optimal value...

    private List<Double> lastNPvtValues = new ArrayList<>();
    private List<Double> lastNObvValues = new ArrayList<>();

    public void addPvtValue(double pvt) {
        this.lastNPvtValues.add(pvt);
        if(this.lastNPvtValues.size() > PVT_LOOK_BEHIND_SIZE) {
            this.lastNPvtValues.remove(0);
        }
    }

    public double pvtDelta() {
        return (this.lastNPvtValues.get(this.lastNPvtValues.size()-1) - this.lastNPvtValues.get(0)) / Math.abs(this.lastNPvtValues.get(0)) * 100;
    }

    public void addObvValue(double obv) {
        this.lastNObvValues.add(obv);
        if(this.lastNObvValues.size() > PVT_LOOK_BEHIND_SIZE) {
            this.lastNObvValues.remove(0);
        }
    }

    public double obvDelta() {
        return (this.lastNObvValues.get(this.lastNObvValues.size()-1) - this.lastNObvValues.get(0)) / Math.abs(this.lastNObvValues.get(0)) * 100;
    }

    public double buySignalStrength() {
        double pvtDelta = pvtDelta();
        double obvDelta = obvDelta();

        boolean pvtCondition = -4 < pvtDelta && pvtDelta < 4;
        boolean obvCondition = obvDelta > 150;

        if(pvtCondition && obvCondition) {
            LOGGER.info("pvtDelta {} obvDelta {}", pvtDelta, obvDelta);
        }
        return pvtCondition && obvCondition ? 1 : 0;
    }
}
