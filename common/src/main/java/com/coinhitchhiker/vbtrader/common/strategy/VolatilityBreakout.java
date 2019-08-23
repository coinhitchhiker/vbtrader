package com.coinhitchhiker.vbtrader.common.strategy;

import com.coinhitchhiker.vbtrader.common.model.Repository;
import com.coinhitchhiker.vbtrader.common.model.TradingWindow;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.joda.time.DateTimeZone.UTC;

public class VolatilityBreakout implements Strategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(VolatilityBreakout.class);

    private final int TRADING_WINDOW_LOOK_BEHIND;
    private final int MA_MIN;
    private final int TRADING_WINDOW_SIZE;
    private final double PRICE_MA_WEIGHT;
    private final double VOLUME_MA_WEIGHT;

    public VolatilityBreakout(int TRADING_WINDOW_LOOK_BEHIND, int MA_MIN, int TRADING_WINDOW_SIZE, double PRICE_MA_WEIGHT, double VOLUME_MA_WEIGHT) {

        this.TRADING_WINDOW_LOOK_BEHIND = TRADING_WINDOW_LOOK_BEHIND;
        this.MA_MIN = MA_MIN;
        this.TRADING_WINDOW_SIZE = TRADING_WINDOW_SIZE;
        this.PRICE_MA_WEIGHT = PRICE_MA_WEIGHT;
        this.VOLUME_MA_WEIGHT = VOLUME_MA_WEIGHT;
    }

    private double getPriceMAScore(
            final List<TradingWindow> lookbehindTradingWindows
            , final double curPrice
            , final int MA_MIN
            , final int TRADING_WINDOW_LOOK_BEHIND) {

        int aboveMaCnt = 0;
        for(int i = MA_MIN; i <= TRADING_WINDOW_LOOK_BEHIND; i++) {
            double closePriceSum = 0.0D;
            for(int j = 0; j < i; j++) {
                double closePrice = lookbehindTradingWindows.get(j).getClosePrice();
                closePriceSum += closePrice;
            }
            if(curPrice > (closePriceSum / i)) {
                aboveMaCnt++;
            }
        }
        return (aboveMaCnt * 1.0) / (TRADING_WINDOW_LOOK_BEHIND - MA_MIN + 1);
    }

    private double getVolumeMAScore_conservative(final List<TradingWindow> lookbehindTradingWindows
            , final double curVolume
            , final int MA_MIN
            , final int TRADING_WINDOW_LOOK_BEHIND) {

        int aboveMaCnt = 0;
        for(int i = MA_MIN; i <= TRADING_WINDOW_LOOK_BEHIND; i++) {
            double volumeSum = 0.0D;
            for(int j = 0; j < i; j++) {
                double volume = lookbehindTradingWindows.get(j).getVolume();
                volumeSum += volume;
            }
            if(curVolume > (volumeSum / i)) {
                aboveMaCnt++;
            }
        }
        return (aboveMaCnt * 1.0) / (TRADING_WINDOW_LOOK_BEHIND - MA_MIN + 1);
    }

    public double getVolumeMAScore_aggresive(final List<TradingWindow> lookbehindTradingWindows,
                                                    final TradingWindow curTradingWindow,
                                                    final int MA_MIN,
                                                    final int TRADING_WINDOW_LOOK_BEHIND,
                                                    final int TRADING_WINDOW_SIZE,
                                                    final long now) {
        List<TradingWindow> allTWs = new ArrayList<>(lookbehindTradingWindows);
        int min = (int)((now - curTradingWindow.getStartTimeStamp())/1000/60);

        // add current trading window's volume into n candles
        List<Double> candleVolumes = new ArrayList<>();
        for(int i = 0; i < min; i++) {
            candleVolumes.add(curTradingWindow.getVolume() / min);
        }

        // put past trading windows candles volumes
        for(TradingWindow tw : allTWs) {
            int candleSize = tw.getCandles().size();
            for(int j = candleSize - 1; j >= 0; j--) {
                candleVolumes.add(tw.getCandles().get(j).getVolume());
            }
        }

        // slice them into size of TRADING_WINDOW_SIZE
        List<Double> volumes = new ArrayList<>();
        double vol = 0.0D;
        for(int i = 0; i < candleVolumes.size(); i++) {
            if((i+1) % TRADING_WINDOW_SIZE == 0) {
                volumes.add(vol);
                vol = 0.0D;
            } else {
                vol += candleVolumes.get(i);
            }
        }
        // add residual vol to the end of volumes array
        volumes.add(vol);

        double curVol = volumes.get(0);
        volumes.remove(0);

        int aboveMaCnt = 0;
        for(int i = MA_MIN; i <= TRADING_WINDOW_LOOK_BEHIND; i++) {
            double volumeSum = 0.0D;
            for(int j = 0; j < i; j++) {
                double volume = volumes.get(j);
                volumeSum += volume;
            }
            if(curVol > (volumeSum / i)) {
                aboveMaCnt++;
            }
        }
        return (aboveMaCnt * 1.0) / (TRADING_WINDOW_LOOK_BEHIND - MA_MIN + 1);
    }

    private double getKValue(List<TradingWindow> lookbehindTradingWindows) {
        return lookbehindTradingWindows.stream().mapToDouble(TradingWindow::getNoiseRatio).average().getAsDouble();
    }

    private double longBuySignalStrength(double curPrice,
                                         TradingWindow curTradingWindow,
                                         List<TradingWindow> lookbehindTradingWindows,
                                         long curTimeStamp) {

        if(curTradingWindow.getHighPrice() == 0 || curTradingWindow.getLowPrice() == 0) return 0;

        double k = this.getKValue(lookbehindTradingWindows);

        boolean priceBreakout = curPrice > curTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange();

        if(!priceBreakout) {
            LOGGER.info("[---------------------NO BUY SIGNAL DETECTED----------------------------]");
            LOGGER.info("curPrice {} < {} (openPrice {} + k {} * prevRange {})",
                    curPrice ,
                    curTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange() ,
                    curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
            return 0;
        }

//        double volume = curTradingWindow.getVolume();
//        double volumeMAScore = VolatilityBreakoutRules.getVolumeMAScore_conservative(lookbehindTradingWindows, volume, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
        double priceMAScore = this.getPriceMAScore(lookbehindTradingWindows, curPrice, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
        double volumeMAScore = this.getVolumeMAScore_aggresive(lookbehindTradingWindows, curTradingWindow, MA_MIN, TRADING_WINDOW_LOOK_BEHIND, TRADING_WINDOW_SIZE, curTimeStamp);
        double weightedMAScore = (PRICE_MA_WEIGHT*priceMAScore + VOLUME_MA_WEIGHT*volumeMAScore) / (PRICE_MA_WEIGHT + VOLUME_MA_WEIGHT);

        if(weightedMAScore > 0) {
            LOGGER.info("[---------------------BUY SIGNAL DETECTED----------------------------]");
            LOGGER.info("curPrice {} > {} (openPrice {} + k {} * prevRange {})",
                    curPrice,
                    curTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange(),
                    curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
        } else {
            LOGGER.info("[-----------------BUY SIGNAL DETECTED BUT COST IS 0------------------------]");
            LOGGER.info("curPrice {} > {} (openPrice {} + k {} * prevRange {})",
                    curPrice ,
                    curTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange() ,
                    curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
            LOGGER.info("tradingWindow endTime {}", new DateTime(curTradingWindow.getEndTimeStamp(), UTC));
        }

        return weightedMAScore;
    }

    private double longSellSignalStrength(double curPrice,
                                          TradingWindow curTradingWindow,
                                          List<TradingWindow> lookbehindTradingWindows,
                                          long curTimeStamp) {
        if(curTradingWindow.getHighPrice() == 0 || curTradingWindow.getLowPrice() == 0) return 0;

        double k = this.getKValue(lookbehindTradingWindows);
        boolean priceBreakout =  curPrice < curTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange();
        if(!priceBreakout) {
            LOGGER.info("[---------------------NO SELL SIGNAL DETECTED----------------------------]");
            LOGGER.info("curPrice {} > {} (openPrice {} - k {} * prevRange {})",
                    curPrice ,
                    curTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange() ,
                    curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
            return 0;
        }

//        double volume = curTradingWindow.getVolume();
//        double volumeMAScore = VolatilityBreakoutRules.getVolumeMAScore_conservative(lookbehindTradingWindows, volume, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
        double priceMAScore = this.getPriceMAScore(lookbehindTradingWindows, curPrice, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
        double volumeMAScore = this.getVolumeMAScore_aggresive(lookbehindTradingWindows, curTradingWindow, MA_MIN, TRADING_WINDOW_LOOK_BEHIND, TRADING_WINDOW_SIZE, curTimeStamp);
        double weightedMAScore = (PRICE_MA_WEIGHT*priceMAScore + VOLUME_MA_WEIGHT*volumeMAScore) / (PRICE_MA_WEIGHT + VOLUME_MA_WEIGHT);

        if(weightedMAScore > 0) {
            LOGGER.info("[---------------------SELL SIGNAL DETECTED----------------------------]");
            LOGGER.info("curPrice {} < {} (openPrice {} - k {} * prevRange {})",
                    curPrice ,
                    curTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange() ,
                    curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
        } else {
            LOGGER.info("[-----------------SELL SIGNAL DETECTED BUT COST IS 0------------------------]");
            LOGGER.info("curPrice {} < {} (openPrice {} - k {} * prevRange {})",
                    curPrice ,
                    curTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange() ,
                    curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
        }

        return weightedMAScore;
    }

    @Override
    public double sellSignalStrength(Map<String, Object> params) {
        double curPrice = (double)params.get("curPrice");
        long curTimestamp = (long)params.get("curTimestamp");
        String mode = (String)params.get("mode");
        Repository repository = (Repository)params.get("repository");

        TradingWindow curTradingWindow = repository.getCurrentTradingWindow(curTimestamp);
        if(curTradingWindow.getBuyOrder() == null) return 0;

        List<TradingWindow> lookbehindTradingWindows = repository.getLastNTradingWindow(TRADING_WINDOW_LOOK_BEHIND+1, curTimestamp);

        // VB SELLING LOGIC
        if(mode.equals("LONG") && curTimestamp > curTradingWindow.getEndTimeStamp()) {
            return this.longSellSignalStrength(curPrice, curTradingWindow, lookbehindTradingWindows, curTimestamp);
        } else {
            LOGGER.error("Unknown mode was given. Returning 0 sellSignalStrength");
            return 0;
        }
    }

    @Override
    public double buySignalStrength(Map<String, Object> params) {
        double curPrice = (double)params.get("curPrice");
        Repository repository = (Repository)params.get("repository");
        long curTimestamp = (long)params.get("curTimestamp");
        String mode = (String)params.get("mode");

        TradingWindow curTradingWindow = repository.getCurrentTradingWindow(curTimestamp);

        // load one more trading window to
        List<TradingWindow> lookbehindTradingWindows = repository.getLastNTradingWindow(TRADING_WINDOW_LOOK_BEHIND, curTimestamp);

        if(mode.equals("LONG")) {
            return this.longBuySignalStrength(curPrice, curTradingWindow, lookbehindTradingWindows, curTimestamp);
        } else {
            LOGGER.error("Unknown mode was given. Returning 0 buySignalStrength");
            return 0;
        }
    }

    public boolean checkPrecondition(Map<String, Object> params) {
        Repository repository = (Repository)params.get("repository");
        long curTimestamp = (long)params.get("curTimestamp");

        TradingWindow curTradingWindow = repository.getCurrentTradingWindow(curTimestamp);
        if(curTradingWindow == null) {
            LOGGER.debug("curTradingWindow is null");
            return false;
        }

        List<TradingWindow> lookbehindTradingWindows = repository.getLastNTradingWindow(TRADING_WINDOW_LOOK_BEHIND+1, curTimestamp);
        if(lookbehindTradingWindows.size() < TRADING_WINDOW_LOOK_BEHIND+1) {
            LOGGER.debug("lookbehindTradingWindows.size() {} < TRADING_WINDOW_LOOK_BEHIND {}", lookbehindTradingWindows.size(), TRADING_WINDOW_LOOK_BEHIND);
            return false;
        }
        return true;
    }
}
