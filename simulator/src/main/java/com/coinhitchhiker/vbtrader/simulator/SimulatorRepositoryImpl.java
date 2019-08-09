package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.Candle;
import com.coinhitchhiker.vbtrader.common.OrderInfo;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SimulatorRepositoryImpl implements Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorRepositoryImpl.class);
    private List<TradingWindow> tradingWindows = new ArrayList<>();

    public SimulatorRepositoryImpl(String symbol, long simulStart, long simulEnd, int tradingWindowSizeInMinites) {

        List<Candle> result = deserCandles(makeFileName(symbol, simulStart, simulEnd));
        if(result != null && result.size() > 0) {
            convertCandleListToTradingWindows(result, tradingWindowSizeInMinites);
        } else {
            result = loadCandlesFromBinance(symbol, simulStart, simulEnd);
            serCandles(result, makeFileName(symbol, simulStart, simulEnd));
            convertCandleListToTradingWindows(result, tradingWindowSizeInMinites);
        }
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

    public SimulatorRepositoryImpl(String dataFile, int tradingWindowSizeInMinutes) throws IOException {
        try(BufferedReader br = Files.newBufferedReader(Paths.get(dataFile))) {
            //symbol interval openTime closeTime openPrice highPrice lowPrice closePrice volume
            //BTCUSDT|1m|1551398400000|1551398459999|3814.2600000000|3814.5000000000|3813.5500000000|3814.3300000000|9.3097660000

            List<Candle> tempList = new ArrayList<>();
            DateTime tradingWindowCloseTime = null, candleCloseTime = null;
            Candle candle = null;
            while(true) {
                String s = br.readLine();

                if(s != null) {
                    candle = parseCandleDataLine(s);
                    candleCloseTime = new DateTime(candle.getCloseTime(), DateTimeZone.UTC);
                    // we only expect 1m interval data from binance candle
                    if(!candle.getInterval().equals("1m")) continue;
                    if(tradingWindowCloseTime == null) {
                        tradingWindowCloseTime = new DateTime(candle.getOpenTime(), DateTimeZone.UTC).plusMinutes(tradingWindowSizeInMinutes).minusSeconds(1);
                    }
                    tempList.add(candle);
                } else {
                    break;
                }

                if(tradingWindowCloseTime.isBefore(candleCloseTime)) {
                    tradingWindows.add(TradingWindow.of(tempList));
                    tempList = new ArrayList<>();
                    tradingWindowCloseTime = null;
                }
            }
        }
    }

    private Candle parseCandleDataLine(String s) {
        String[] strs = s.split("\\|");
        String symbol = strs[0];
        String interval = strs[1];

        long openTime = Long.valueOf(strs[2]);
        long closeTime = Long.valueOf(strs[3]);
        double openPrice = Double.valueOf(strs[4]);
        double highPrice = Double.valueOf(strs[5]);
        double lowPrice = Double.valueOf(strs[6]);
        double closePrice = Double.valueOf(strs[7]);
        double volume = Double.valueOf(strs[8]);

        return new Candle(symbol, interval, openTime, closeTime, openPrice, highPrice, lowPrice, closePrice, volume);
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
        for(int i = this.tradingWindows.size()-1; i >= 0; i--) {
            TradingWindow curTw = this.tradingWindows.get(i);
            if(curTw.isBetween(curTimestamp)) {
                return curTw;
            }
        }
        return null;
    }

    @Override
    public void recordOrder(TradingWindow tradingWindow, OrderInfo orderInfo) {

    }
}
