package com.coinhitchhiker.vbtrader.trader.exchange.okex;

import com.coinhitchhiker.vbtrader.common.RESTAPIResponseErrorHandler;
import com.coinhitchhiker.vbtrader.common.model.Candle;
import com.coinhitchhiker.vbtrader.common.model.Repository;
import com.coinhitchhiker.vbtrader.common.model.TradingWindow;
import com.coinhitchhiker.vbtrader.trader.db.TraderDAO;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceRepository;
import com.google.gson.Gson;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.joda.time.DateTimeZone.UTC;

public class OKExRepository implements Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(OKExRepository.class);
    private RestTemplate restTemplate = new RestTemplateBuilder().setConnectTimeout(Duration.ofMillis(1000L)).setReadTimeout(Duration.ofMillis(5000L)).build();
    private Gson gson = new Gson();

    @Autowired
    private TraderDAO traderDAO;

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

    @Override
    public List<Candle> getCandles(String symbol, long startTime, long endTime) {

        // symbol string comes from v3 API. It gives symbols in upper cased letters like this -- "BTC-USDT".
        // However v1 kline API expected lower-cased symbol connected with underscore, not hyphen. Conversion needed.

        HttpHeaders headers = new HttpHeaders();
        headers.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0");

        String lowerCasedSymbol = symbol.toLowerCase().replace("-", "_");
        List<Candle> candles = new ArrayList<>();
        int size = (int)(endTime - startTime)/1000/60;
        while(true) {
            String response;
            try {
                final UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("https://www.okex.com/api/v1/kline.do")
                        .queryParam("symbol", lowerCasedSymbol)
                        .queryParam("type", "1min")
                        .queryParam("size", size)
                        .queryParam("since", startTime);
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
                candles.add(Candle.fromOKExCandle(symbol, 60, data));
            }
            if(list.size() < 2000) {
                break;
            }
//            start = new DateTime(candles.get(candles.size()-1).getCloseTime() + 1).toDateTimeISO().toString();
            startTime = candles.get(candles.size()-1).getCloseTime() + 1;
            try {Thread.sleep(300L);} catch(Exception e) {}
        }
        return candles;
    }
}
