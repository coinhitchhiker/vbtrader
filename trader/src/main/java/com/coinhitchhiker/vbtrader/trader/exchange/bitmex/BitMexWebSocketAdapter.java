package com.coinhitchhiker.vbtrader.trader.exchange.bitmex;

import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceOrderBookCache;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceWebSocketAdapter;
import com.google.gson.Gson;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class BitMexWebSocketAdapter extends WebSocketAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BitMexWebSocketAdapter.class);
    private final BitMexOrderBookCache.BitMexWsCallBack callback;
    private final Gson gson = new Gson();

    public BitMexWebSocketAdapter(BitMexOrderBookCache.BitMexWsCallBack callback) {
        this.callback = callback;
    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception
    {
        Map<String, Object> map = gson.fromJson(text, Map.class);
        String table = (String)map.get("table");
        String action = (String)map.get("action");
        List<Map<String, Object>> data = (List)map.get("data");

        if(data == null) return;
        if(!table.equals("trade") || !action.equals("insert")) return;

        this.callback.onTradeEvent(data);
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
