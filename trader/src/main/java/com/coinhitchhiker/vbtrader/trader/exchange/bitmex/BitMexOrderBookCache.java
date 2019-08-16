package com.coinhitchhiker.vbtrader.trader.exchange.bitmex;

import com.coinhitchhiker.vbtrader.common.OrderBookCache;
import com.coinhitchhiker.vbtrader.common.Repository;
import com.google.gson.Gson;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketExtension;
import com.neovisionaries.ws.client.WebSocketFactory;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.tags.EditorAwareTag;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BitMexOrderBookCache implements OrderBookCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(BitMexOrderBookCache.class);
    private static final String BIDS = "BIDS";
    private static final String ASKS = "ASKS";
    private static final int TIMEOUT = 5000;

    private long lastUpdateId = -1;
    private WebSocket ws;
    private Gson gson = new Gson();
    private final Map<String, NavigableMap<BigDecimal, BigDecimal>> depthCache = new ConcurrentHashMap<>();
    private BitMexWsCallBack callback;

    @Value("${trading.symbol}") private String TRADING_SYMBOL;

    @Autowired private Repository bitmexRepository;

    @PostConstruct
    public void init() {
        this.callback = new BitMexWsCallBack(this);

        this.subscribeToTradesAndOrderBook();
    }

    private void subscribeToTradesAndOrderBook() {

        String url = "wss://www.bitmex.com/realtime?subscribe=trade:" + TRADING_SYMBOL.toUpperCase() + ",orderBook10:" + TRADING_SYMBOL.toUpperCase();
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

        this.ws.addListener(new BitMexWebSocketAdapter(this.callback));
    }

    public void onTradeEvent(List<Map<String, Object>> trades) {
        if(bitmexRepository != null) {
            ((BitMexRepository)bitmexRepository).onTradeEvent(trades);
        }
    }

    public void onOrderBookUpdate(List<Map<String, Object>> orderbook) {
        List<List<Double>> bids = (List)orderbook.get(0).get("bids");
        List<List<Double>> asks = (List)orderbook.get(0).get("asks");
        long timestamp = new DateTime((String)orderbook.get(0).get("timestamp"), DateTimeZone.UTC).getMillis();

        if(timestamp < this.lastUpdateId) {
            return;
        }

        NavigableMap<BigDecimal, BigDecimal> bidsMap = new TreeMap<>(Comparator.reverseOrder());
        bids.forEach(b -> {
            double price = b.get(0);
            double size = b.get(1);

            bidsMap.put(new BigDecimal(price), new BigDecimal(size));
        });
        this.depthCache.put(BIDS, bidsMap);

        NavigableMap<BigDecimal, BigDecimal> asksMap = new TreeMap<>(Comparator.reverseOrder());
        asks.forEach(a -> {
            double price = a.get(0);
            double size = a.get(1);

            asksMap.put(new BigDecimal(price), new BigDecimal(size));
        });
        this.depthCache.put(ASKS, asksMap);

        this.lastUpdateId = timestamp;
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

    public void printBestAskBidMidprice() {
        LOGGER.info("--------------------------------------------");
        LOGGER.info("BestAsk: " + getBestAsk());
        LOGGER.info("BestBid: " + getBestBid());
        LOGGER.info("MidPrice: " + getMidPrice());
        LOGGER.info("--------------------------------------------");
    }

    //----------------------------------------------------------------------
    protected final class BitMexWsCallBack {

        private BitMexOrderBookCache bitmexOrderBookCache;

        protected BitMexWsCallBack(BitMexOrderBookCache orderBookCache) {
            this.bitmexOrderBookCache = orderBookCache;
        }

        public void onTradeEvent(List<Map<String, Object>> trade) {
            bitmexOrderBookCache.onTradeEvent(trade);
        }

        public void onOrderBookUpdate(List<Map<String, Object>> orderbook) {
            bitmexOrderBookCache.onOrderBookUpdate(orderbook);
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
            subscribeToTradesAndOrderBook();
        }
    }
}
