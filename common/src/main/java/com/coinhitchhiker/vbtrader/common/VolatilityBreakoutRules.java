package com.coinhitchhiker.vbtrader.common;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import static org.joda.time.DateTimeZone.UTC;

public class VolatilityBreakoutRules {

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

    public static double getVolumeMAScore_aggresive(final List<TradingWindow> lookbehindTradingWindows,
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

    public static DateTime getClosestMin(DateTime now) {
        int y = now.getYear();
        int m = now.getMonthOfYear();
        int d = now.getDayOfMonth();
        int h = now.getHourOfDay();
        int mm = now.getMinuteOfHour();

        DateTime closestMin = new DateTime(y,m,d,h,mm, DateTimeZone.UTC);
        return closestMin;
    }

}
