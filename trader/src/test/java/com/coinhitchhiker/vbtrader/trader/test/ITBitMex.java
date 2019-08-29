package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.trader.exchange.bitmex.BitMexOrderBookCache;
import com.coinhitchhiker.vbtrader.trader.exchange.bitmex.BitMexRepository;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@TestPropertySource(locations = {"classpath:test-bitmex.properties"})
@ContextConfiguration(classes = {BitMexTestConfig.class,})
public class ITBitMex extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ITBitMex.class);

    @Autowired
    Exchange bitmexExchange;
    @Autowired Repository bitMexRepository;
    @Autowired OrderBookCache bitmexOrderBookCache;

    @Value("${trading.quote.currency}") String quoteCurrency;

    @PostConstruct
    public void init() {
        int idx = 0;
        while(true) {
            ((BitMexOrderBookCache)bitmexOrderBookCache).printBestAskBidMidprice();
            try {Thread.sleep(100L);} catch(Exception e) {}
            idx++;
            if(idx == 7) break;
        }
    }

    @Test
    @Ignore
    public void getCandles() {
        List<Candle> candles = ((BitMexRepository)bitMexRepository).getCandles("XBTUSD", 1565818700000L, 1565905100000L);
        candles.forEach(c -> LOGGER.info(c.toString()));
    }

    @Test
    @Ignore
    public void getBalance() {
        LOGGER.info(bitmexExchange.getBalance().toString());
    }

    @Test
    @Ignore
    public void getCurrentPrice() {
        // wait for some secs for the orderbookcache receives WS stream
        double xbtPrice;
        while(true) {
            xbtPrice = bitmexExchange.getCurrentPrice(null);
            if(xbtPrice != 0.0) break;
            try {Thread.sleep(1000L);} catch(Exception e) {}
        }

        LOGGER.info("XBTUSD price {}", xbtPrice);
        Map<String, Balance> bal = bitmexExchange.getBalance();
        double xbtAmount = bal.get(quoteCurrency).getAvailableForTrade();
        double bestBid = bitmexOrderBookCache.getBestBid();
        double bestAsk = bitmexOrderBookCache.getBestAsk();
        int shortPrice = (int)(bestBid * (1-0.05/100));
        int longPrice = (int)(bestAsk * (1+0.05/100));
        int shortSize = (int)(xbtAmount / shortPrice);
        int longSize = (int)(xbtAmount / longPrice);

        LOGGER.info("XBT amount {}", xbtAmount);
        LOGGER.info("BestBid {}", bestBid);
        LOGGER.info("BestAsk {}", bestAsk);
        LOGGER.info("shortPrice {}", shortPrice);
        LOGGER.info("shortSize {}", shortSize);
        LOGGER.info("longPrice {}", longPrice);
        LOGGER.info("longSize {}", longSize);
    }

    @Test
    @Ignore
    public void placeOrder() {
        OrderInfo s = new OrderInfo("BITMEX", "XBTUSD", OrderSide.SELL, 9800.5, 10);
        bitmexExchange.placeOrder(s);
        LOGGER.info("SHORT ORDER");
        LOGGER.info(s.toString());

        double priceExecuted = s.getPriceExecuted();
        double amountExecuted = s.getAmountExecuted();
        double longPrice = priceExecuted * (1 + 0.5/100);

        OrderInfo l = new OrderInfo("BITMEX", "XBTUSD", OrderSide.BUY, longPrice, amountExecuted);
        bitmexExchange.placeOrder(l);
        LOGGER.info("LONG ORDER");
        LOGGER.info(l.toString());
    }

}
