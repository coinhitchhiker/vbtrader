package com.coinhitchhiker.vbtrader.trader.exchange.binance;

import com.google.gson.Gson;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class BinanceWebSocketAdapter extends WebSocketAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinanceWebSocketAdapter.class);
    private final BinanceOrderBookCache.BinanceWsCallback callback;
    private final Gson gson = new Gson();

    public BinanceWebSocketAdapter(BinanceOrderBookCache.BinanceWsCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception
    {
        Map<String, Object> map = gson.fromJson(text, Map.class);
        String stream = (String)map.get("stream");

        Map<String, Object> data = (Map)map.get("data");
        if(stream.contains("@depth")) {
            long timestamp = ((Double)data.get("lastUpdateId")).longValue();
            List<List<String>> bids = (List)data.get("bids");
            List<List<String>> asks = (List)data.get("asks");
            this.callback.onOrderBookUpdate(timestamp, bids, asks);
        } else if(stream.contains("@trade")){
            this.callback.onTradeEvent(data);
        }
    }

    @Override
    public void onPingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        callback.onPingFrame();
    }

    @Override
    public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
        callback.onFailure(cause);
    }
}
