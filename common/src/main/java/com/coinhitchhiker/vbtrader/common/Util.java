package com.coinhitchhiker.vbtrader.common;

import com.coinhitchhiker.vbtrader.common.model.Candle;
import com.coinhitchhiker.vbtrader.common.model.Repository;
import com.coinhitchhiker.vbtrader.common.model.TimeFrame;
import com.coinhitchhiker.vbtrader.common.model.TradingWindow;
import com.coinhitchhiker.vbtrader.common.strategy.vb.VolatilityBreakout;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.joda.time.DateTimeZone.UTC;

public class Util {

    private static final Logger LOGGER = LoggerFactory.getLogger(Util.class);

    public static DateTime getClosestMin(DateTime now) {
        int y = now.getYear();
        int m = now.getMonthOfYear();
        int d = now.getDayOfMonth();
        int h = now.getHourOfDay();
        int mm = now.getMinuteOfHour();

        DateTime closestMin = new DateTime(y,m,d,h,mm, DateTimeZone.UTC);
        return closestMin;
    }

    public static DateTime getClosestCandleOpen(DateTime now, TimeFrame timeFrame) {
        if(timeFrame.equals(TimeFrame.M1)) {
            return Util.getClosestMin(now);
        }

        int y = now.getYear();
        int m = now.getMonthOfYear();
        int d = now.getDayOfMonth();
        int h = now.getHourOfDay();
        int mm = now.getMinuteOfHour();

        if(timeFrame.equals(TimeFrame.M5)) {
            mm = ((int)mm / 5) * 5;
        } else if(timeFrame.equals(TimeFrame.M15)) {
            mm = ((int)mm / 15) * 15;
        } else if(timeFrame.equals(TimeFrame.M30)) {
            mm = ((int)mm / 30) * 30;
        } else if(timeFrame.equals(TimeFrame.H1)) {
            mm = 0;
        } else if(timeFrame.equals(TimeFrame.H4)) {
            mm = 0;
            h = ((int)h / 4) * 4;
        } else if(timeFrame.equals(TimeFrame.D1)) {
            mm = 0;
            h = 0;
        } else {
            throw new RuntimeException("Unsupported timeframe");
        }

        return new DateTime(y,m,d,h,mm, DateTimeZone.UTC);
    }

    public static List<TradingWindow> getLastNTradingWindow(List<TradingWindow> tradingWindows, int n) {
        return tradingWindows.stream().limit(n).collect(Collectors.toList());
    }

    public static TradingWindow constructCurrentTradingWindow(String SYMBOL, int TRADING_WINDOW_SIZE, double midPrice, long timestamp, Repository repository) {

        TradingWindow result =  new TradingWindow(SYMBOL, timestamp, timestamp + TRADING_WINDOW_SIZE * 60 * 1000 - 1, midPrice);

        List<Candle> candles = repository.getCandles(SYMBOL, timestamp, timestamp + TRADING_WINDOW_SIZE * 60 * 1000);
        if(candles.size() > 0) {
            TradingWindow twFromBinanceData = TradingWindow.of(candles);
            result.setHighPrice(twFromBinanceData.getHighPrice());
            result.setOpenPrice(twFromBinanceData.getOpenPrice());
            result.setLowPrice(twFromBinanceData.getLowPrice());
        } else {
            // wait for 10s to ensure receiving orderbook data
            try {Thread.sleep(10_000L); } catch(Exception e) {}
            if(midPrice == 0.0) {
                throw new RuntimeException("orderbook mid price is 0.0!!!!!");
            }
            result.setOpenPrice(midPrice);
            result.setHighPrice(midPrice);
            result.setLowPrice(midPrice);
        }
        return result;
    }

    public static List<TradingWindow> constructPastTradingWindows(long curTimestamp, int TRADING_WINDOW_SIZE, int TRADING_WINDOW_LOOK_BEHIND, String SYMBOL, Repository repository) {

        DateTime closestMin = getClosestMin(new DateTime(curTimestamp, UTC));

        List<TradingWindow> result = new ArrayList<>();
        long closestMinMillis = closestMin.getMillis();

        long windowEnd = closestMinMillis - 1000;
        long windowStart = windowEnd - TRADING_WINDOW_SIZE * 60 * 1000;

        for(int i = 0; i <= TRADING_WINDOW_LOOK_BEHIND; i++) {
            List<Candle> candles = repository.getCandles(SYMBOL, windowStart, windowEnd);
            if(candles == null || candles.size() == 0) {
                LOGGER.warn("No candle data was found from repository");
            } else {
                TradingWindow tw = TradingWindow.of(candles);
                LOGGER.debug("{}/{} {}", i, TRADING_WINDOW_LOOK_BEHIND, tw.toString());
                result.add(tw);
            }

            windowEnd -= TRADING_WINDOW_SIZE * 60 * 1000;
            windowStart -= TRADING_WINDOW_SIZE * 60 * 1000;

            if(!repository.getClass().getCanonicalName().contains("SimulatorRepositoryImpl")) {
                try {Thread.sleep(300L);} catch(Exception e) {}
            }
        }

        return result;
    }

}
