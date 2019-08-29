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

    @Autowired
    OrderBookCache orderBookCache;

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

    @Override
    public Candle getCurrentCandle(long curTimestamp) {
        return null;
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
