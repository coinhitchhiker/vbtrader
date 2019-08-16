package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.common.*;
import com.coinhitchhiker.vbtrader.trader.exchange.bitmex.BitMexExchange;
import com.coinhitchhiker.vbtrader.trader.exchange.bitmex.BitMexOrderBookCache;
import com.coinhitchhiker.vbtrader.trader.exchange.bitmex.BitMexRepository;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@TestPropertySource(locations = {"classpath:test-bitmex.properties"})
@ContextConfiguration(classes = {BitMexTestConfig.class,})
public class ITBitMex extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ITBitMex.class);

    @Autowired Exchange bitmexExchange;
    @Autowired Repository bitMexRepository;

    @Value("${trading.quote.currency}") String quoteCurrency;

    @Test
    @Ignore
    public void getCandles() {
        List<Candle> candles = ((BitMexRepository)bitMexRepository).getCandles("XBTUSD", 1565818700000L, 1565905100000L);
        candles.forEach(c -> LOGGER.info(c.toString()));
    }

    @Test
//    @Ignore
    public void getBalance() {
        LOGGER.info(bitmexExchange.getBalance().toString());
    }

    @Test
//    @Ignore
    public void getCurrentPrice() {
        // wait for some secs for the orderbookcache receives WS stream
        double xbtPrice;
        while(true) {
            xbtPrice = bitmexExchange.getCurrentPrice(null);
            if(xbtPrice != 10000) break;
            try {Thread.sleep(5000L); } catch(Exception e) {}
        }

        LOGGER.info("XBTUSD price {}", xbtPrice);
        Map<String, Balance> bal = bitmexExchange.getBalance();
        double xbtAmount = bal.get(quoteCurrency).getAvailableForTrade();
        LOGGER.info("XBT amount {}", xbtAmount);
        double shortSize = xbtPrice * (1.0-0.5/100) * xbtAmount;
        LOGGER.info("ShortSize {}", (int)shortSize);

        double longSize = xbtPrice * (1.0+0.5/100) * xbtAmount;
        LOGGER.info("LongSize {}", (int)longSize);

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
