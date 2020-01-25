package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.*;
import com.coinhitchhiker.vbtrader.common.model.Candle;
import com.coinhitchhiker.vbtrader.common.model.ExchangeEnum;
import com.coinhitchhiker.vbtrader.common.model.Repository;
import com.coinhitchhiker.vbtrader.common.model.TradingWindow;
import com.coinhitchhiker.vbtrader.simulator.db.SimulatorDAO;
import com.google.gson.Gson;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.joda.time.DateTimeZone.UTC;

public class SimulatorRepositoryImpl implements Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorRepositoryImpl.class);

    private long currentTimestamp;
    private ExchangeEnum exchange;
    private boolean REPO_USE_DB;

    private List<Candle> allCandles = new ArrayList<>();
    private SimulatorDAO simulatorDAO;

    public SimulatorRepositoryImpl(ExchangeEnum exchange,
                                   String symbol,
                                   long simulStart,
                                   long simulEnd,
                                   SimulatorDAO simulatorDAO,
                                   boolean REPO_USE_DB)  {

        this.currentTimestamp = simulStart;
        this.simulatorDAO = simulatorDAO;
        this.exchange = exchange;
        this.REPO_USE_DB = REPO_USE_DB;

        List<Candle> result = deserCandles(makeFileName(exchange, symbol, simulStart, simulEnd, REPO_USE_DB));
        if(result != null && result.size() > 0) {
            allCandles.addAll(result);
        } else {
            if(this.exchange.equals(ExchangeEnum.BINANCE)) {
                if(REPO_USE_DB)
                    result = loadCandlesFromDB(exchange, symbol, simulStart, simulEnd);
                else
                    result = loadCandlesFromBinance(symbol, simulStart, simulEnd);
            } else if(this.exchange.equals(ExchangeEnum.BITMEX)) {
                result = loadCandlesFromBitMex(symbol, simulStart, simulEnd);
            } else if(this.exchange.equals(ExchangeEnum.OKEX)) {
                result = loadCandlesFromOKEX(symbol, simulStart, simulEnd);
            }
            serCandles(result, makeFileName(exchange, symbol, simulStart, simulEnd, REPO_USE_DB));
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
        List<Candle> result = new ArrayList<>();
        for(Candle candle : this.allCandles) {
            if(startTime <= candle.getOpenTime() && candle.getOpenTime() < endTime) {
                result.add(candle);
            }
        }

        // if requested time range is not included in pre-loaded candles, we call exchange API to get candle data
        if(result.size() == 0) {
            if(this.exchange.equals(ExchangeEnum.BINANCE)) {
                result = this.loadCandlesFromBinance(symbol, startTime, endTime);
            } else {
                throw new RuntimeException("Unsupported exchange for post-loading candles");
            }
        }
        return result;
    }

    public Candle getCurrentCandle(long curTimestamp) {
        int curCandleIndex = getCurrentCandleIndex(curTimestamp);
        if(curCandleIndex == -1) return null;
        return allCandles.get(curCandleIndex);
    }

    private List<Candle> loadCandlesFromDB(ExchangeEnum exchange, String symbol, long simulStart, long simulEnd) {
        if(!exchange.equals(ExchangeEnum.BINANCE)) {
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
            String url = "https://api.binance.com/api/v1/klines?symbol="+symbol+"&interval="+interval+"&limit=1000&startTime="+startTime+"&endTime="+simulEnd;
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

    private List<Candle> loadCandlesFromOKEX(String symbol, long simulStart, long simulEnd) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new RESTAPIResponseErrorHandler());
        Gson gson = new Gson();

        HttpHeaders headers = new HttpHeaders();
        headers.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0");

        // symbol string comes from v3 API. It gives symbols in upper cased letters like this -- "BTC-USDT".
        // However v1 kline API expected lower-cased symbol connected with underscore, not hyphen. Conversion needed.
        String lowerCasedSymbol = symbol.toLowerCase().replace("-", "_");
        List<Candle> candles = new ArrayList<>();
        long size = (long)(simulEnd - simulStart)/1000/300;
        while(true) {
            String response;
            try {
                final UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("https://www.okex.com/api/v1/kline.do")
                        .queryParam("symbol", lowerCasedSymbol)
                        .queryParam("type", "5min")     // okex supports maximum 2000 historocal data points. decided to use 5min candle to get as much data as possible
                        .queryParam("size", size)
                        .queryParam("since", simulStart);
                String url = builder.toUriString();
                LOGGER.info(url);
                response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null, headers), String.class).getBody();
            } catch (Exception e) {
                LOGGER.error("Error reading okex candles. Retrying...", e);
                continue;
            }

            List<List<Object>> list = gson.fromJson(response, List.class);
            LOGGER.info("data cnt {}", list.size());
            for(List<Object> data : list) {
                candles.add(Candle.fromOKExCandle(symbol, 300, data));
            }
            if(list.size() < 2000) {
                break;
            }
            simulStart = candles.get(candles.size()-1).getCloseTime() + 1;
            try {Thread.sleep(300L);} catch(Exception e) {}
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

    private String makeFileName(ExchangeEnum exchange, String symbol, long simulStart, long simulEnd, boolean REPO_USE_DB) {
        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyyMMdd");

        String s = dtfOut.print(new DateTime(simulStart, UTC));
        String e = dtfOut.print(new DateTime(simulEnd, UTC));
        return exchange.name() + "-" + symbol + "-" + s + "-" + e + (REPO_USE_DB ? "_DB" : "");
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

    public void setCurrentTimestamp(long currentTimestamp) {
        this.currentTimestamp = currentTimestamp;
    }

}
