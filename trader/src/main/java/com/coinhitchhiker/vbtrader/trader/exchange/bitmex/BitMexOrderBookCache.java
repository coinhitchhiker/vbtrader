package com.coinhitchhiker.vbtrader.trader.exchange.bitmex;

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

public class BitMexOrderBookCache implements OrderBookCache {

    public static final String BEAN_NAME_ORDERBOOK_CACHE_BITMEX = "orderbookcache-bitmex";

    private static final Logger LOGGER = LoggerFactory.getLogger(BitMexOrderBookCache.class);
    private static final String BIDS = "BIDS";
    private static final String ASKS = "ASKS";
    private static final int TIMEOUT = 5000;

    private long lastUpdateId = -1;
    private WebSocket ws;
    private Gson gson = new Gson();
    private final Map<String, NavigableMap<BigDecimal, BigDecimal>> depthCache = new ConcurrentHashMap<>();
    private BitMexWsCallBack callback;
    private RestTemplate restTemplate = new RestTemplateBuilder().setConnectTimeout(Duration.ofMillis(1000L)).setReadTimeout(Duration.ofMillis(5000L)).build();

    @Value("${trading.symbol}") private String TRADING_SYMBOL;

    @Autowired private Repository bitmexRepository;

    @PostConstruct
    public void init() {
        this.callback = new BitMexWsCallBack(this);

        this.subscribeToTrades();
    }

    private void subscribeToTrades() {

        String url = "wss://testnet.bitmex.com/realtime?subscribe=trade:" + TRADING_SYMBOL.toUpperCase();
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

    //----------------------------------------------------------------------
    protected final class BitMexWsCallBack {

        private BitMexOrderBookCache bitmexOrderBookCache;

        protected BitMexWsCallBack(BitMexOrderBookCache orderBookCache) {
            this.bitmexOrderBookCache = orderBookCache;
        }

        public void onTradeEvent(List<Map<String, Object>> trade) {
            bitmexOrderBookCache.onTradeEvent(trade);
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
            subscribeToTrades();
        }
    }
}
