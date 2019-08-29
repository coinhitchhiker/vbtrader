package com.coinhitchhiker.vbtrader.common.strategy.pvtobv;

import com.coinhitchhiker.vbtrader.common.model.Repository;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.joda.time.DateTimeZone.UTC;

public class PVTOBV {

    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");
    private static final Logger LOGGER = LoggerFactory.getLogger(PVTOBV.class);

    private List<Double> lastNPvtValues = new ArrayList<>();
    private List<Double> lastNObvValues = new ArrayList<>();
    private List<Double> lastNPrices = new ArrayList<>();

    private Repository repository;

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

    public void buildMinuteTechnicalIndicator(Map<String, Object> params) {

        long curTimestamp = (long)params.get("curTimestamp");
        double curPrice = (double)params.get("curPrice");
        Repository repository = (Repository)params.get("repository");

        double pvt = 0, obv = 0;

        try {
            // now 1min passed... build indicator for the 1min candle
//            pvt = repository.getPVT(curTimestamp);
//            obv = repository.getOBV(curTimestamp);

            this.addPvtValue(pvt);
            this.addObvValue(obv);
            this.addPrice(curPrice);

            DateTime dt = new DateTime(curTimestamp, UTC);
            DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd H:m");
            String str = fmt.print(dt);

//        System.out.println(str + "," + curTimestamp + "," + pvt + "," + pvtobv.pvtDelta() + "," + obv + "," + pvtobv.obvDelta());

        } catch (Exception e) {
            LOGGER.error("curTimestamp={}, price={}, pvt={}, obv={}", curTimestamp, curPrice, pvt, obv, e);
        }
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
