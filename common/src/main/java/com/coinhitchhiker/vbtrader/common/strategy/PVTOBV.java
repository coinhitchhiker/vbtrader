package com.coinhitchhiker.vbtrader.common.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PVTOBV {

    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");

    private List<Double> lastNPvtValues = new ArrayList<>();
    private List<Double> lastNObvValues = new ArrayList<>();
    private List<Double> lastNPrices = new ArrayList<>();

    private int PVT_LOOK_BEHIND;
    private int PVT_SIGNAL_THRESHOLD;
    private int OBV_LOOK_BEHIND;
    private int OBV_BUY_SIGNAL_THRESHOLD;
    private int OBV_SELL_SIGNAL_THRESHOLD;

    public PVTOBV(int PVT_LOOK_BEHIND, int PVT_SIGNAL_THRESHOLD, int OBV_LOOK_BEHIND, int OBV_BUY_SIGNAL_THRESHOLD, int OBV_SELL_SIGNAL_THRESHOLD) {
        this.PVT_LOOK_BEHIND = PVT_LOOK_BEHIND;
        this.PVT_SIGNAL_THRESHOLD = PVT_SIGNAL_THRESHOLD;
        this.OBV_LOOK_BEHIND = OBV_LOOK_BEHIND;
        this.OBV_BUY_SIGNAL_THRESHOLD = OBV_BUY_SIGNAL_THRESHOLD;
        this.OBV_SELL_SIGNAL_THRESHOLD = OBV_SELL_SIGNAL_THRESHOLD;
    }

    public void addPvtValue(double pvt) {
        this.lastNPvtValues.add(pvt);
        if(this.lastNPvtValues.size() > PVT_LOOK_BEHIND) {
            this.lastNPvtValues.remove(0);
        }
    }

    public double pvtDelta() {
        if(this.lastNPvtValues.size() <= 0) return 0;

        double delta = 0;

        for(int i = 0; i < lastNPvtValues.size()-1; i++) {
            delta += lastNPvtValues.get(i+1) - lastNPvtValues.get(i);
        }

//        delta = this.lastNPvtValues.get(this.lastNObvValues.size()-1) - this.lastNPvtValues.get(0);

        return delta / PVT_LOOK_BEHIND;
    }

    public void addObvValue(double obv) {
        this.lastNObvValues.add(obv);
        if(this.lastNObvValues.size() > OBV_LOOK_BEHIND) {
            this.lastNObvValues.remove(0);
        }
    }

    public double obvDelta() {
        if(this.lastNObvValues.size() <= 0) return 0;

        double delta = 0;

        for(int i = 0; i < lastNObvValues.size()-1; i++) {
            delta += lastNObvValues.get(i+1) - lastNObvValues.get(i);
        }

//        delta = this.lastNObvValues.get(this.lastNObvValues.size()-1) - this.lastNObvValues.get(0);

        return delta / OBV_LOOK_BEHIND;
    }

    public void addPrice(double price) {
        this.lastNPrices.add(price);
        if(this.lastNPrices.size() > PVT_LOOK_BEHIND) {
            this.lastNPrices.remove(0);
        }
    }

    public double priceDelta() {
        if(this.lastNPrices.size() <= 0) return 0;

        double delta = 0;

        return (this.lastNPrices.get(this.lastNPrices.size()-1) - this.lastNPrices.get(0))/10;
    }

    public double buySignalStrength() {
        double pvtDelta = pvtDelta();
        double obvDelta = obvDelta();
        double priceDelta = priceDelta();

        boolean pvtCondition = PVT_SIGNAL_THRESHOLD < pvtDelta;
        boolean obvCondition = OBV_BUY_SIGNAL_THRESHOLD < obvDelta;
        boolean priceCondition = priceDelta < -25;
//        boolean obvCondition = true;

        if(pvtCondition && obvCondition && priceCondition) {
            LOGGERBUYSELL.info("pvtDelta {} obvDelta {} priceDelta {} PVT_SIGNAL_THRESHOLD {} OBV_BUY_SIGNAL_THRESHOLD {}",
                    pvtDelta,
                    obvDelta,
                    PVT_SIGNAL_THRESHOLD,
                    OBV_BUY_SIGNAL_THRESHOLD);
        }
        return pvtCondition && obvCondition && priceCondition ? 1 : 0;
    }

    public double sellSignalStrength() {
        double pvtDelta = pvtDelta();
        double obvDelta = obvDelta();
        double priceDelta = priceDelta();

        boolean pvtCondition = pvtDelta < PVT_SIGNAL_THRESHOLD;
        boolean obvCondition = obvDelta < OBV_SELL_SIGNAL_THRESHOLD;
        boolean priceCondition = priceDelta > 25;

        if(pvtCondition && obvCondition) {
            LOGGERBUYSELL.info("pvtDelta {} obvDelta {} PVT_SIGNAL_THRESHOLD {} OBV_SELL_SIGNAL_THRESHOLD {}",
                    pvtDelta,
                    obvDelta,
                    PVT_SIGNAL_THRESHOLD,
                    OBV_SELL_SIGNAL_THRESHOLD);
        }
        return pvtCondition && obvCondition ? 1 : 0;
    }

    public int getPVT_LOOK_BEHIND() {
        return PVT_LOOK_BEHIND;
    }

    public int getPVT_SIGNAL_THRESHOLD() {
        return PVT_SIGNAL_THRESHOLD;
    }

    public int getOBV_LOOK_BEHIND() {
        return OBV_LOOK_BEHIND;
    }

    public int getOBV_BUY_SIGNAL_THRESHOLD() {
        return OBV_BUY_SIGNAL_THRESHOLD;
    }

    public int getOBV_SELL_SIGNAL_THRESHOLD() {
        return OBV_SELL_SIGNAL_THRESHOLD;
    }
}
