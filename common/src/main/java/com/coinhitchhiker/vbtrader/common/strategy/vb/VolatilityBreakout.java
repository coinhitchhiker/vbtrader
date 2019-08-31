package com.coinhitchhiker.vbtrader.common.strategy.vb;

import com.coinhitchhiker.vbtrader.common.model.TradingWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class VolatilityBreakout  {

    private static final Logger LOGGER = LoggerFactory.getLogger(VolatilityBreakout.class);

    public static double getPriceMAScore(
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

    public static double getVolumeMAScore_conservative(final List<TradingWindow> lookbehindTradingWindows
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

    public static double getVolumeMAScore_aggressive(final List<TradingWindow> lookbehindTradingWindows,
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

    public static double getKValue(List<TradingWindow> lookbehindTradingWindows) {
        return lookbehindTradingWindows.stream().mapToDouble(TradingWindow::getNoiseRatio).average().getAsDouble();
    }

//    private double shortSellSignalStrength(double curPrice,
//                                          TradingWindow curTradingWindow,
//                                          List<TradingWindow> lookbehindTradingWindows,
//                                          long curTimeStamp) {
//        if(curTradingWindow.getHighPrice() == 0 || curTradingWindow.getLowPrice() == 0) return 0;
//
//        double k = this.getKValue(lookbehindTradingWindows);
//        boolean priceBreakout =  curPrice < curTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange();
//        if(!priceBreakout) {
//            LOGGER.info("[---------------------NO SELL SIGNAL DETECTED----------------------------]");
//            LOGGER.info("curPrice {} > {} (openPrice {} - k {} * prevRange {})",
//                    curPrice ,
//                    curTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange() ,
//                    curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
//            return 0;
//        }
//
////        double volume = curTradingWindow.getVolume();
////        double volumeMAScore = VolatilityBreakoutRules.getVolumeMAScore_conservative(lookbehindTradingWindows, volume, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
//        double priceMAScore = this.getPriceMAScore(lookbehindTradingWindows, curPrice, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
//        double volumeMAScore = this.getVolumeMAScore_aggressive(lookbehindTradingWindows, curTradingWindow, MA_MIN, TRADING_WINDOW_LOOK_BEHIND, TRADING_WINDOW_SIZE, curTimeStamp);
//        double weightedMAScore = (PRICE_MA_WEIGHT*priceMAScore + VOLUME_MA_WEIGHT*volumeMAScore) / (PRICE_MA_WEIGHT + VOLUME_MA_WEIGHT);
//
//        if(weightedMAScore > 0) {
//            LOGGER.info("[---------------------SELL SIGNAL DETECTED----------------------------]");
//            LOGGER.info("curPrice {} < {} (openPrice {} - k {} * prevRange {})",
//                    curPrice ,
//                    curTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange() ,
//                    curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
//        } else {
//            LOGGER.info("[-----------------SELL SIGNAL DETECTED BUT COST IS 0------------------------]");
//            LOGGER.info("curPrice {} < {} (openPrice {} - k {} * prevRange {})",
//                    curPrice ,
//                    curTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange() ,
//                    curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
//        }
//
//        return weightedMAScore;
//    }
}
