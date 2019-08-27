package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.*;
import com.coinhitchhiker.vbtrader.common.model.Candle;
import com.coinhitchhiker.vbtrader.common.model.Repository;
import com.coinhitchhiker.vbtrader.common.model.TradingWindow;
import com.coinhitchhiker.vbtrader.simulator.db.SimulatorDAO;
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
import java.util.Map;

import static org.joda.time.DateTimeZone.UTC;

public class SimulatorRepositoryImpl implements Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorRepositoryImpl.class);

    private long currentTimestamp;
    private String exchange;

    private List<Candle> allCandles = new ArrayList<>();
    private SimulatorDAO simulatorDAO;

    public SimulatorRepositoryImpl(String exchange,
                                   String symbol,
                                   long simulStart,
                                   long simulEnd,
                                   SimulatorDAO simulatorDAO)  {

        this.currentTimestamp = simulStart;
        this.simulatorDAO = simulatorDAO;
        this.exchange = exchange;

        List<Candle> result = deserCandles(makeFileName(exchange, symbol, simulStart, simulEnd));
        if(result != null && result.size() > 0) {
            allCandles.addAll(result);
        } else {
            if(this.exchange.equals("BINANCE")) {
                result = loadCandlesFromBinance(symbol, simulStart, simulEnd);
//                result = loadCandlesFromDB(exchange, symbol, simulStart, simulEnd);
            } else if(this.exchange.equals("BITMEX")) {
                result = loadCandlesFromBitMex(symbol, simulStart, simulEnd);
            }
            serCandles(result, makeFileName(exchange, symbol, simulStart, simulEnd));
            allCandles.addAll(result);
        }
    }

    private int getCurrentCandleIndex(long curTimestamp) {
        int i = 0;
        for(Candle candle : allCandles) {
            if(candle.getOpenTime() <= curTimestamp && curTimestamp <= candle.getCloseTime()) {
                return i;
            }
            i++;
        }
        LOGGER.warn("No candle was found for {}", new DateTime(curTimestamp, UTC));
        return -1;
    }

    public List<Candle> getCandles(String symbol, long startTime, long endTime) {
        if(exchange.equals("BINANCE")) {
//            return this.loadCandlesFromDB("BINANCE", symbol, startTime, endTime);
            return this.loadCandlesFromBinance(symbol, startTime, endTime);
        } else if(exchange.equals("BITMEX")) {
            return this.loadCandlesFromBitMex(symbol, startTime, endTime);
        } else {
            throw new RuntimeException("Unsupported exchange was given");
        }
    }

    public Candle getCurrentCandle(long curTimestamp) {
        int curCandleIndex = getCurrentCandleIndex(curTimestamp);
        return allCandles.get(curCandleIndex);
    }

    private List<Candle> loadCandlesFromDB(String exchange, String symbol, long simulStart, long simulEnd) {
        if(!exchange.equals("BINANCE")) {
            throw new RuntimeException("Only BINANCE is supported");
        }

        return simulatorDAO.getBinanceCandles(symbol, "1m", simulStart, simulEnd);
    }

    private List<Candle> loadCandlesFromBinance(String symbol, long simulStart, long simulEnd) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new RESTAPIResponseErrorHandler());

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

    private List<Candle> loadCandlesFromBitMex(String symbol, long simulStart, long SimulEnd) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new RESTAPIResponseErrorHandler());

        Gson gson = new Gson();
        List<Candle> candles = new ArrayList<>();

        String startTime = new DateTime(simulStart, UTC).toDateTimeISO().toString();
        String endTime = new DateTime(SimulEnd, UTC).toDateTimeISO().toString();

        while(true) {
            String url = "https://www.bitmex.com/api/v1/trade/bucketed?symbol="+symbol+"&binSize=1m&count=750&start=0&startTime="+startTime+"&endTime="+endTime;
            LOGGER.info(url);
            String response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null, null), String.class).getBody();
            List<Map<String, Object>> list = gson.fromJson(response, List.class);
            LOGGER.info("data cnt {}", list.size());
            for(Map<String, Object> data : list) {
                candles.add(Candle.fromBitMexCandle(symbol, "1m", data));
            }
            if(list.size() < 750) break;
            startTime = new DateTime(candles.get(candles.size()-1).getCloseTime() + 1000, UTC).toDateTimeISO().toString();
            try {Thread.sleep(2500);} catch(Exception e){}
        }
        return candles;
    }

    private String makeFileName(String exchange, String symbol, long simulStart, long simulEnd) {
        return exchange + "-" + symbol + "-" + simulStart + "-" + simulEnd;
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

//
//    @Override
//    public List<TradingWindow> getLastNTradingWindow(int n, long curTimestamp) {
//        List<TradingWindow> result = new ArrayList();
//        for(int i = this.tradingWindows.size()-1; i >= 0; i--) {
//            TradingWindow curTw = this.tradingWindows.get(i);
//            if(curTw.getEndTimeStamp() < curTimestamp) {
//                result.add(curTw);
//                if(result.size() == n) {
//                    break;
//                }
//            }
//        }
//        return result;
//    }
//
//    @Override
//    public void refreshTradingWindows() {
//        DateTime now = new DateTime(this.currentTimestamp, DateTimeZone.UTC);
//        DateTime closestMin = Util.getClosestMin(now);
//        long timestamp = closestMin.getMillis();
//
//        for(TradingWindow tradingWindow : tradingWindows) {
//            if(tradingWindow.isBetween(timestamp)) {
//                //TradingWindow(String symbol, long startTimeStamp, long endTimeStamp, double openPrice, double highPrice, double closePrice, double lowPrice, double volume) {
//                TradingWindow tw = new TradingWindow(tradingWindow.getSymbol(),
//                        tradingWindow.getStartTimeStamp(),
//                        tradingWindow.getEndTimeStamp(),
//                        tradingWindow.getOpenPrice(),
//                        tradingWindow.getHighPrice(),
//                        tradingWindow.getClosePrice(),
//                        tradingWindow.getLowPrice(),
//                        tradingWindow.getVolume());
//                tw.setCandles(tradingWindow.getCandles());
//                tw.setTS_TRIGGER_PCT(tsTriggerPct);
//                tw.setTS_PCT(tsPct);
//                this.currentTradingWindow = tw;
//                return;
//            }
//        }
//
//        throw new RuntimeException("unreachable code path");
//    }

    public void setCurrentTimestamp(long currentTimestamp) {
        this.currentTimestamp = currentTimestamp;
    }

}
