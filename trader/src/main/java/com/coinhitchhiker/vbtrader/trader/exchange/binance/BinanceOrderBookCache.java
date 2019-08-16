package com.coinhitchhiker.vbtrader.trader.exchange.binance;

import com.coinhitchhiker.vbtrader.common.OrderBookCache;
import com.coinhitchhiker.vbtrader.common.Repository;
import com.google.gson.Gson;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketExtension;
import com.neovisionaries.ws.client.WebSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BinanceOrderBookCache implements OrderBookCache {

    public static final String BEAN_NAME_ORDERBOOK_CACHE_BINANCE = "orderbookcache-binance";

    private static final Logger LOGGER = LoggerFactory.getLogger(BinanceOrderBookCache.class);
    private static final String BIDS = "BIDS";
    private static final String ASKS = "ASKS";
    private static final int TIMEOUT = 5000;

    private long lastUpdateId = -1;
    private WebSocket ws;
    private Gson gson = new Gson();
    private final Map<String, NavigableMap<BigDecimal, BigDecimal>> depthCache = new ConcurrentHashMap<>();
    private BinanceOrderBookCache.BinanceWsCallback callback;
    private RestTemplate restTemplate = new RestTemplateBuilder().setConnectTimeout(Duration.ofMillis(1000L)).setReadTimeout(Duration.ofMillis(5000L)).build();

    @Value("${trading.symbol}") private String TRADING_SYMBOL;

    @Autowired private Repository binanceRepository;

    @PostConstruct
    public void init() {
        this.callback = new BinanceOrderBookCache.BinanceWsCallback(this);

        this.subscribeToOrderBookAndTrades();
    }

    private void subscribeToOrderBookAndTrades() {

        initializeDepthCache();

        String url = "wss://stream.binance.com:9443/stream?streams=" + TRADING_SYMBOL.toLowerCase() + "@depth20/" + TRADING_SYMBOL.toLowerCase() + "@trade";
        LOGGER.info(url);

        try {
            this.ws = new WebSocketFactory()
                    .setConnectionTimeout(TIMEOUT)
                    .createSocket(url)
                    .addExtension(WebSocketExtension.PERMESSAGE_DEFLATE)
                    .connect();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.ws.addListener(new BinanceWebSocketAdapter(this.callback));
    }

    public void onTradeEvent(Map<String, Object> trade) {
        if(binanceRepository != null) {
            ((BinanceRepository)binanceRepository).onTradeEvent(trade);
        }
    }

    protected void updateOrderBook(long timestamp, List<List<String>> bidData, List<List<String>> askData) {
        if(this.lastUpdateId >= timestamp)
            return;

        NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>(Comparator.reverseOrder());
        askData.forEach(entry -> {
            BigDecimal price = new BigDecimal(entry.get(0));
            BigDecimal amount = new BigDecimal(entry.get(1));

            asks.put(price, amount);
        });
        this.depthCache.put(ASKS, asks);

        NavigableMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Comparator.reverseOrder());
        bidData.forEach(entry -> {
            BigDecimal price = new BigDecimal(entry.get(0));
            BigDecimal amount = new BigDecimal(entry.get(1));

            bids.put(price, amount);
        });
        this.depthCache.put(BIDS, bids);

        this.lastUpdateId = timestamp;
    }

    public void printBestAskBidMidprice() {
        LOGGER.info("--------------------------------------------");
        LOGGER.info("BestAsk: " + getBestAsk());
        LOGGER.info("BestBid: " + getBestBid());
        LOGGER.info("MidPrice: " + getMidPrice());
        LOGGER.info("--------------------------------------------");
    }

    private void initializeDepthCache() {
        String url = "https://api.binance.com/api/v1/depth?symbol=" + this.TRADING_SYMBOL + "&limit=20";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        String body = response.getBody();
        Map<String, Object> map = this.gson.fromJson(body, Map.class);
        long timestamp = ((Double)map.get("lastUpdateId")).longValue();
        List<List<String>> bidData = (List)map.get("bids");
        List<List<String>> askData = (List)map.get("asks");

        this.lastUpdateId = timestamp;

        NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>(Comparator.reverseOrder());
        askData.forEach(entry -> {
            BigDecimal price = new BigDecimal(entry.get(0));
            BigDecimal amount = new BigDecimal(entry.get(1));

            asks.put(price, amount);
        });
        this.depthCache.put(ASKS, asks);

        NavigableMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Comparator.reverseOrder());
        bidData.forEach(entry -> {
            BigDecimal price = new BigDecimal(entry.get(0));
            BigDecimal amount = new BigDecimal(entry.get(1));

            bids.put(price, amount);
        });
        this.depthCache.put(BIDS, bids);
    }

    @Override
    public double getBestAsk() {
        if(this.depthCache.get(ASKS) != null) {
            return this.depthCache.get(ASKS).lastEntry().getKey().doubleValue();
        } else {
            return 0.0D;
        }

    }

    @Override
    public double getBestBid() {
        if(this.depthCache.get(BIDS) != null) {
            return this.depthCache.get(BIDS).firstEntry().getKey().doubleValue();
        } else {
            return 0.0D;
        }
    }

    @Override
    public double getMidPrice() {
        return (getBestAsk() + getBestBid())/2;
    }

    //----------------------------------------------------------------------
    protected final class BinanceWsCallback {

        private BinanceOrderBookCache binanceOrderBookCache;

        protected BinanceWsCallback(BinanceOrderBookCache orderBookCache) {
            this.binanceOrderBookCache = orderBookCache;
        }

        public void onOrderBookUpdate(long timestamp, List<List<String>> bids, List<List<String>> asks) {
            updateOrderBook(timestamp, bids, asks);
        }

        public void onTradeEvent(Map<String, Object> trade) {
            binanceOrderBookCache.onTradeEvent(trade);
        }

        public void onPingFrame() {
            ws.sendPong("I am live");
        }

        public void onFailure(Throwable t) {
            LOGGER.error("WS connection failed. Reconnecting. cause:", t);
            try {
                ws.sendClose();
            } catch (Exception e) {
                LOGGER.error("Exception when closing WS connection...", e);
            }
            LOGGER.info("Restarting connection...");
            initializeDepthCache();
            subscribeToOrderBookAndTrades();
        }
    }
}
