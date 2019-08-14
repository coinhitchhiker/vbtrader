package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.Candle;
import com.coinhitchhiker.vbtrader.common.Repository;
import com.coinhitchhiker.vbtrader.common.TradingWindow;
import com.google.gson.Gson;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class SimulatorRepositoryImpl implements Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorRepositoryImpl.class);
    private List<TradingWindow> tradingWindows = new ArrayList<>();
    private TradingWindow currentTradingWindow = null;
    private long currentTimestamp;

    public SimulatorRepositoryImpl(String symbol,
                                   long simulStart,
                                   long simulEnd,
                                   int tradingWindowSizeInMinutes) {

        List<Candle> result = deserCandles(makeFileName(symbol, simulStart, simulEnd));
        if(result != null && result.size() > 0) {
            convertCandleListToTradingWindows(result, tradingWindowSizeInMinutes);
        } else {
            result = loadCandlesFromBinance(symbol, simulStart, simulEnd);
            serCandles(result, makeFileName(symbol, simulStart, simulEnd));
            convertCandleListToTradingWindows(result, tradingWindowSizeInMinutes);
        }

        this.currentTimestamp = simulStart;
        this.refreshTradingWindows();
    }

    private List<Candle> loadCandlesFromBinance(String symbol, long simulStart, long simulEnd) {
        RestTemplate restTemplate = new RestTemplate();
        Gson gson = new Gson();
        List<Candle> candles = new ArrayList<>();
        long startTime = simulStart;
        String interval = "1m";
        while(true) {
            String url = "https://api.binance.com//api/v1/klines?symbol="+symbol+"&interval="+interval+"&limit=1000&startTime="+startTime+"&endTime="+simulEnd;
            LOGGER.info(url);
            String response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null, null), String.class).getBody();
            List<List<Object>> list = gson.fromJson(response, List.class);
            LOGGER.info("data cnt {}", list.size());
            for(List<Object> data : list) {
                candles.add(Candle.fromBinanceCandle(symbol, interval, data));
            }
            startTime = candles.get(candles.size()-1).getCloseTime() + 1;
            if(list.size() < 1000) break;
            try {Thread.sleep(500);} catch(Exception e){}
        }
        return candles;
    }

    private String makeFileName(String symbol, long simulStart, long simulEnd) {
        return symbol + "-" + simulStart + "-" + simulEnd;
    }

    private void serCandles(List<Candle> candles, String filename) {
        try{
            FileOutputStream fos= new FileOutputStream(filename);
            ObjectOutputStream oos= new ObjectOutputStream(fos);
            oos.writeObject(candles);
            oos.close();
            fos.close();
        }catch(Exception e){
            LOGGER.info("serCandles Exception", e);
        }
    }

    private List<Candle> deserCandles(String filename) {
        ArrayList<Candle> arraylist= new ArrayList<>();
        try
        {
            FileInputStream fis = new FileInputStream(filename);
            ObjectInputStream ois = new ObjectInputStream(fis);
            arraylist = (ArrayList) ois.readObject();
            ois.close();
            fis.close();
        }catch(Exception e){
            LOGGER.info("deserCandles Exception", e);
        }
        return arraylist;
    }

    private void convertCandleListToTradingWindows(List<Candle> candles, int tradingWindowSizeInMin) {
        List<Candle> tempList = new ArrayList<>();
        for(int i = 1; i <= candles.size(); i++) {
            tempList.add(candles.get(i-1));
            if(i % tradingWindowSizeInMin == 0) {
                this.tradingWindows.add(TradingWindow.of(tempList));
                tempList = new ArrayList<>();
            }
        }

        if(tempList.size() > 0) {
            this.tradingWindows.add(TradingWindow.of(tempList));
        }
    }

    public List<TradingWindow> getTradingWindows() {
        return this.tradingWindows;
    }

    @Override
    public List<TradingWindow> getLastNTradingWindow(int n, long curTimestamp) {
        List<TradingWindow> result = new ArrayList();
        for(int i = this.tradingWindows.size()-1; i >= 0; i--) {
            TradingWindow curTw = this.tradingWindows.get(i);
            if(curTw.getEndTimeStamp() < curTimestamp) {
                result.add(curTw);
                if(result.size() == n) break;
            }
        }
        return result;
    }

    @Override
    public TradingWindow getCurrentTradingWindow(long curTimestamp) {
        return this.currentTradingWindow;
    }

    @Override
    public void refreshTradingWindows() {
        DateTime now = new DateTime(this.currentTimestamp, DateTimeZone.UTC);
        DateTime closestMin = getClosestMin(now);
        long timestamp = closestMin.getMillis();

        for(TradingWindow tradingWindow : tradingWindows) {
            if(tradingWindow.isBetween(timestamp)) {
                //TradingWindow(String symbol, long startTimeStamp, long endTimeStamp, double openPrice, double highPrice, double closePrice, double lowPrice, double volume) {
                TradingWindow tw = new TradingWindow(tradingWindow.getSymbol(),
                        tradingWindow.getStartTimeStamp(),
                        tradingWindow.getEndTimeStamp(),
                        tradingWindow.getOpenPrice(),
                        tradingWindow.getHighPrice(),
                        tradingWindow.getClosePrice(),
                        tradingWindow.getLowPrice(),
                        tradingWindow.getVolume());
                tw.setCandles(tradingWindow.getCandles());
                this.currentTradingWindow = tw;
                return;
            }
        }

        throw new RuntimeException("unreacheable code path");
    }

    private DateTime getClosestMin(DateTime now) {
        int y = now.getYear();
        int m = now.getMonthOfYear();
        int d = now.getDayOfMonth();
        int h = now.getHourOfDay();
        int mm = now.getMinuteOfHour();

        DateTime closestMin = new DateTime(y,m,d,h,mm,DateTimeZone.UTC);
        return closestMin;
    }

    public void setCurrentTimestamp(long currentTimestamp) {
        this.currentTimestamp = currentTimestamp;
    }
}
