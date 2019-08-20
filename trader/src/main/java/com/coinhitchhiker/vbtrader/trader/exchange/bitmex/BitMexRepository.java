package com.coinhitchhiker.vbtrader.trader.exchange.bitmex;

import com.coinhitchhiker.vbtrader.common.*;
import com.coinhitchhiker.vbtrader.common.model.*;
import com.google.gson.Gson;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.joda.time.DateTimeZone.UTC;

public class BitMexRepository implements Repository {

    public static final String BEAN_NAME_REPOSITORY_BITMEX = "repo-bitmex";

    private static final Logger LOGGER = LoggerFactory.getLogger(BitMexRepository.class);
    private List<TradingWindow> pastTradingWindows;
    private TradingWindow currentTradingWindow;

    private boolean refreshingTradingWindows = false;
    private double pendingTradeVol = 0.0D;

    @Value("${trading.symbol}") private String TRADING_SYMBOL;
    @Value("${trading.window.size}") private int TRADING_WINDOW_SIZE;
    @Value("${trading.look.behind}") private int TRADING_WINDOW_LOOK_BEHIND;
    @Value("${trading.iceberg.order}") private boolean doIcebergOrder;
    @Value("${trading.ts.trigger.pct}") private double TS_TRIGGER_PCT;
    @Value("${trading.ts.pct}") private double TS_PCT;

    @Autowired
    OrderBookCache orderBookCache;

    @PostConstruct
    public void init() {

        if(TS_TRIGGER_PCT <= TS_PCT) {
            throw new RuntimeException(String.format("tsTriggerPct should be bigger than tsPct (tsTriggetPct %.2f <= tsPct %.2f)", TS_TRIGGER_PCT, TS_PCT));
        }

        this.refreshTradingWindows();
    }
    @Override
    public List<TradingWindow> getLastNTradingWindow(int n, long curTimestamp) {
        return this.pastTradingWindows.stream().limit(n).collect(Collectors.toList());
    }

    @Override
    public TradingWindow getCurrentTradingWindow(long curTimestamp) {
        return this.currentTradingWindow;
    }

    @Override
    public void refreshTradingWindows() {
        this.refreshingTradingWindows = true;
        LOGGER.debug("refreshingTradingWindows is set to TRUE");

        DateTime now = DateTime.now(DateTimeZone.UTC);
        DateTime closestMin = getClosestMin(now);

        this.currentTradingWindow = constructTradingWindow(closestMin);
        LOGGER.debug("Refreshed curTW {}", this.currentTradingWindow.toString());

        this.pastTradingWindows = new ArrayList<>();
        long curTimestamp = closestMin.getMillis();

        long windowEnd = curTimestamp - 1000;
        long windowStart = windowEnd - TRADING_WINDOW_SIZE * 60 * 1000;

        for(int i = 0; i <= TRADING_WINDOW_LOOK_BEHIND; i++) {
            List<Candle> candles = getCandles(TRADING_SYMBOL, windowStart, windowEnd);
            TradingWindow tw = TradingWindow.of(candles);
            LOGGER.debug("{}/{} {}", i, TRADING_WINDOW_LOOK_BEHIND, tw.toString());
            pastTradingWindows.add(tw);
            windowEnd -= TRADING_WINDOW_SIZE * 60 * 1000;
            windowStart -= TRADING_WINDOW_SIZE * 60 * 1000;
            try {Thread.sleep(300L);} catch(Exception e) {}
        }
        this.refreshingTradingWindows = false;

        LOGGER.debug("-----------CURRENT TRADING WINDOW REFRESHED-----------------------");
        LOGGER.debug("curTimestamp {}", now);
        LOGGER.debug("{}", this.currentTradingWindow);
        LOGGER.debug("refreshingTradingWindows is set to FALSE");
    }

    public void onTradeEvent(List<Map<String, Object>> trades) {
        List<TradeEvent> converted = new ArrayList<>();
        for(Map<String, Object> trade : trades) {
            String symbol = (String)trade.get("symbol");
            double price = (double)trade.get("price");
            long tradeTime = new DateTime((String)trade.get("timestamp"), DateTimeZone.UTC).getMillis();
            double amount = (double)trade.get("size");
            String tradeId = (String)trade.get("trdMatchID");

            TradeEvent e = new TradeEvent("BITMEX", symbol, price, tradeTime, amount, tradeId, null, null);
            converted.add(e);
        }

        if(this.refreshingTradingWindows) {
            pendingTradeVol += converted.stream().mapToDouble(TradeEvent::getAmount).sum();
            LOGGER.debug("RefreshingTradingWindows is ongoing. PendingTradeVol {}", pendingTradeVol);
            return;
        }

        if(pendingTradeVol > 0) {
            TradeEvent firstEvent = converted.get(0);
            firstEvent.setAmount(pendingTradeVol + firstEvent.getAmount());
            LOGGER.debug("pendingTradeVol {} was added to the TradeEvent", pendingTradeVol);
            pendingTradeVol = 0.0D;
        }

        for(TradeEvent e : converted) {
            if(this.currentTradingWindow != null) {
                this.currentTradingWindow.updateWindowData(e);
            }
        }
    }

    private DateTime getClosestMin(DateTime now) {
        int y = now.getYear();
        int m = now.getMonthOfYear();
        int d = now.getDayOfMonth();
        int h = now.getHourOfDay();
        int mm = now.getMinuteOfHour();

        DateTime closestMin = new DateTime(y,m,d,h,mm, DateTimeZone.UTC);
        return closestMin;
    }

    private TradingWindow constructTradingWindow(DateTime startTime) {

        long timestamp = startTime.getMillis();

        TradingWindow result =  new TradingWindow(this.TRADING_SYMBOL, timestamp, timestamp + TRADING_WINDOW_SIZE * 60 * 1000, 0.0D);
        result.setTS_TRIGGER_PCT(TS_TRIGGER_PCT);
        result.setTS_PCT(TS_PCT);

        List<Candle> candles = getCandles(TRADING_SYMBOL, timestamp, timestamp + TRADING_WINDOW_SIZE * 60 * 1000);
        if(candles.size() > 0) {
            TradingWindow twFromBinanceData = TradingWindow.of(candles);
            result.setHighPrice(twFromBinanceData.getHighPrice());
            result.setOpenPrice(twFromBinanceData.getOpenPrice());
            result.setLowPrice(twFromBinanceData.getLowPrice());
        } else {
            // wait for 10s to ensure receiving orderbook data
            try {Thread.sleep(10_000L); } catch(Exception e) {}
            double price = orderBookCache.getMidPrice();
            if(price == 0.0) {
                throw new RuntimeException("orderbook mid price is 0.0!!!!!");
            }
            result.setOpenPrice(price);
            result.setHighPrice(price);
            result.setLowPrice(price);
        }
        return result;
    }

    public List<Candle> getCandles(String symbol, long windowStart, long windowEnd) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new RESTAPIResponseErrorHandler());

        Gson gson = new Gson();
        String startTime = new DateTime(windowStart, UTC).toDateTimeISO().toString();
        String endTime = new DateTime(windowEnd, UTC).toDateTimeISO().toString();

        List<Candle> candles = new ArrayList<>();
        while(true) {
            String url = "https://www.bitmex.com/api/v1/trade/bucketed?symbol="+symbol+"&binSize=1m&count=750&start=0&startTime="+startTime+"&endTime="+endTime;
            LOGGER.info(url);

            String response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null, null), String.class).getBody();
            List<Map<String, Object>> data = gson.fromJson(response, List.class);
            LOGGER.info("data cnt {}", data.size());
            for(Map<String, Object> c : data) {
                candles.add(Candle.fromBitMexCandle(symbol, "1m", c));
            }
            if(data.size() < 750) break;
            startTime = new DateTime(candles.get(candles.size()-1).getCloseTime()+1000, UTC).toDateTimeISO().toString();
            try {Thread.sleep(2100);} catch(Exception e){}
        }
        return candles;
    }

}
