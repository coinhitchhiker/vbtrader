package com.coinhitchhiker.vbtrader.trader.exchange.binance;

import com.coinhitchhiker.vbtrader.common.*;
import com.coinhitchhiker.vbtrader.common.model.*;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BinanceRepository implements Repository {

    public static final String BEAN_NAME_REPOSITORY_BINANCE = "repo-binance";

    private static final Logger LOGGER = LoggerFactory.getLogger(BinanceRepository.class);
    private List<TradingWindow> pastTradingWindows;
    private TradingWindow currentTradingWindow;
    private List<Candle> allCandles = new LinkedList<>();

    private RestTemplate restTemplate = new RestTemplateBuilder().setConnectTimeout(Duration.ofMillis(1000L)).setReadTimeout(Duration.ofMillis(5000L)).build();
    private boolean refreshingTradingWindows = false;
    private double pendingTradeVol = 0.0D;
    private Gson gson = new Gson();

    @Value("${trading.symbol}") private String TRADING_SYMBOL;

    @Autowired private OrderBookCache orderBookCache;
    @Autowired private TraderDAO traderDAO;

    @PostConstruct
    public void init() {
        this.restTemplate.setErrorHandler(new RESTAPIResponseErrorHandler());
    }

    @Override
    public void logCompleteTradingWindow(TradingWindow tradingWindow) {
        this.traderDAO.logCompleteTransaction(new Gson().toJson(tradingWindow));
    }

    @Override
    public Candle getCurrentCandle(long curTimestamp) {
        throw new UnsupportedOperationException();
    }

    private List<Candle> getCandlesFromDB(String symbol, long starTime, long endTime) {
        return traderDAO.getBinanceCandles(symbol, "1m", starTime, endTime);
    }

    @Override
    public List<Candle> getCandles(String symbol, long startTime, long endTime) {
        List<Candle> candles = new ArrayList<>();
        String interval = "1m";
        while(true) {
            String url = "https://api.binance.com/api/v1/klines?symbol="+symbol+"&interval="+interval+"&limit=1000&startTime="+startTime+"&endTime="+endTime;
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
