package com.coinhitchhiker.vbtrader.trader.exchange.binance;

import com.coinhitchhiker.vbtrader.common.*;
import com.coinhitchhiker.vbtrader.trader.db.TraderDAO;
import com.google.gson.Gson;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BinanceRepository implements Repository {

    public static final String BEAN_NAME_REPOSITORY_BINANCE = "repo-binance";

    private static final Logger LOGGER = LoggerFactory.getLogger(BinanceRepository.class);
    private List<TradingWindow> pastTradingWindows;
    private TradingWindow currentTradingWindow;

    private RestTemplate restTemplate = new RestTemplateBuilder().setConnectTimeout(Duration.ofMillis(1000L)).setReadTimeout(Duration.ofMillis(5000L)).build();
    private boolean refreshingTradingWindows = false;
    private double pendingTradeVol = 0.0D;
    private Gson gson = new Gson();

    @Value("${trading.symbol}") private String TRADING_SYMBOL;
    @Value("${trading.window.size}") private int TRADING_WINDOW_SIZE;
    @Value("${trading.look.behind}") private int TRADING_WINDOW_LOOK_BEHIND;
    @Value("${trading.iceberg.order}") private boolean doIcebergOrder;
    @Value("${trading.ts.trigger.pct}") private double TS_TRIGGER_PCT;
    @Value("${trading.ts.pct}") private double TS_PCT;

    @Autowired private OrderBookCache orderBookCache;
    @Autowired private TraderDAO traderDAO;

    @PostConstruct
    public void init() {

        if(TS_TRIGGER_PCT <= TS_PCT) {
            throw new RuntimeException(String.format("tsTriggerPct should be bigger than tsPct (tsTriggetPct %.2f <= tsPct %.2f)", TS_TRIGGER_PCT, TS_PCT));
        }

        this.refreshTradingWindows();
    }

    @Override
    public void logCompleteTradingWindow(TradingWindow tradingWindow) {
        this.traderDAO.logCompleteTransaction(new Gson().toJson(tradingWindow));
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
            List<Candle> candles = this.getCandles(TRADING_SYMBOL, windowStart, windowEnd);
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

    private DateTime getClosestMin(DateTime now) {
        int y = now.getYear();
        int m = now.getMonthOfYear();
        int d = now.getDayOfMonth();
        int h = now.getHourOfDay();
        int mm = now.getMinuteOfHour();

        DateTime closestMin = new DateTime(y,m,d,h,mm,DateTimeZone.UTC);
        return closestMin;
    }

    private TradingWindow constructTradingWindow(DateTime startTime) {
        long timestamp = startTime.getMillis();

        TradingWindow result =  new TradingWindow(this.TRADING_SYMBOL, timestamp, timestamp + TRADING_WINDOW_SIZE * 60 * 1000, orderBookCache.getMidPrice());
        result.setTS_TRIGGER_PCT(TS_TRIGGER_PCT);
        result.setTS_PCT(TS_PCT);

        List<Candle> candles = getCandles(TRADING_SYMBOL, timestamp, timestamp + TRADING_WINDOW_SIZE * 60 * 1000);
        if(candles.size() > 0) {
            TradingWindow twFromBinanceData = TradingWindow.of(candles);
            result.setHighPrice(twFromBinanceData.getHighPrice());
            result.setOpenPrice(twFromBinanceData.getOpenPrice());
        } else {
            // wait for 10s to ensure receiving orderbook data
            try {Thread.sleep(10_000L); } catch(Exception e) {}
            double price = orderBookCache.getMidPrice();
            if(price == 0.0) LOGGER.warn("orderbook mid price is 0.0!!!!!");
            result.setOpenPrice(price);
            result.setHighPrice(price);
        }
        return result;
    }

    private List<Candle> getCandles(String symbol, long startTime, long endTime) {
        List<Candle> candles = new ArrayList<>();
        String interval = "1m";
        while(true) {
            String url = "https://api.binance.com//api/v1/klines?symbol="+symbol+"&interval="+interval+"&limit=1000&startTime="+startTime+"&endTime="+endTime;
            LOGGER.info(url);
            String response;
            try {
                response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null, null), String.class).getBody();
            } catch (Exception e) {
                LOGGER.error("Error reading binance candles. Retrying...", e);
                continue;
            }

            List<List<Object>> list = gson.fromJson(response, List.class);
            LOGGER.info("data cnt {}", list.size());
            for(List<Object> data : list) {
                candles.add(Candle.fromBinanceCandle(symbol, interval, data));
            }
            if(list.size() < 1000) {
                break;
            }
            startTime = candles.get(candles.size()-1).getCloseTime() + 1;
            try {Thread.sleep(300L);} catch(Exception e) {}
        }
        return candles;
    }

    public void onTradeEvent(Map<String, Object> trade) {

        if(this.refreshingTradingWindows) {
            pendingTradeVol += Double.parseDouble((String)trade.get("q"));
            LOGGER.debug("RefreshingTradingWindows is ongoing. PendingTradeVol {}", pendingTradeVol);
            return;
        }

        double price = Double.parseDouble(((String)trade.get("p")));
        long tradeTime = ((Double)trade.get("T")).longValue();
        double amount = Double.parseDouble((String)trade.get("q"));
        String tradeId = String.valueOf(((Double)trade.get("t")).longValue());
        String buyOrderId = String.valueOf(((Double)trade.get("b")).longValue());
        String sellOrderId = String.valueOf(((Double)trade.get("a")).longValue());

        if(pendingTradeVol > 0) {
            amount += pendingTradeVol;
            LOGGER.debug("pendingTradeVol {} was added to the TradeEvent", pendingTradeVol);
            pendingTradeVol = 0.0D;
        }

        TradeEvent e = new TradeEvent("BINANCE", this.TRADING_SYMBOL, price, tradeTime , amount, tradeId, buyOrderId, sellOrderId);

        this.currentTradingWindow.updateWindowData(e);

    }
}
