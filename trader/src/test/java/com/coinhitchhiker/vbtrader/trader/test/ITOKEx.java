package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.trader.exchange.okex.OKExExchange;
import com.coinhitchhiker.vbtrader.trader.exchange.okex.OKExRepository;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
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

import static com.coinhitchhiker.vbtrader.common.model.OrderType.LIMIT_MAKER;
import static com.coinhitchhiker.vbtrader.common.model.OrderType.MARKET;

@TestPropertySource(locations = {"classpath:test-okex.properties"})
@ContextConfiguration(classes = {OKExTestConfig.class,})
public class ITOKEx extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ITOKEx.class);

    @Autowired Exchange okexExchange;
    @Autowired Repository okexRepository;
    @Autowired OrderBookCache okexOrderBookCache;

    @Value("${trading.quote.currency}") String quoteCurrency;

//    @PostConstruct
//    public void init() {
//        int idx = 0;
//        while(true) {
//            ((OKExOrderBookCache)okexOrderBookCache).printBestAskBidMidprice();
//            try {Thread.sleep(100L);} catch(Exception e) {}
//            idx++;
//            if(idx == 7) break;
//        }
//    }

    @Test
    @Ignore
    public void getCandles() {
        long startTime = new DateTime(2019, 8,1,0,0,0).withZone(DateTimeZone.UTC).getMillis();
        long endTime = new DateTime(2019, 8,1,0,10,0).withZone(DateTimeZone.UTC).getMillis();
        String symbol = okexExchange.getCoinInfoList().get(0).getSymbol();
        List<Candle> candles = okexRepository.getCandles(symbol, startTime, endTime);
        candles.forEach(c -> LOGGER.debug(c.toString()));
    }

    @Test
    @Ignore
    public void getCoinInfoList() {
        List<CoinInfo> coinInfos = okexExchange.getCoinInfoList();
        coinInfos.forEach(c -> LOGGER.debug(c.toString()));
        CoinInfo btcusdt = okexExchange.getCoinInfoBySymbol("BTC-USDT");
        LOGGER.debug(btcusdt.toString());
    }

    @Test
    @Ignore
    public void getBalance() {
        LOGGER.info(okexExchange.getBalance().toString());
    }

    @Test
    @Ignore
    public void getCurrentPrice() {
        double btcusdtPrice = okexExchange.getCurrentPrice("BTC-USDT");
        LOGGER.debug("btcUsdt price {}", btcusdtPrice);
    }

    @Test
    @Ignore
    public void borrowAndRepayBTC() {
        String borrow_id = ((OKExExchange)okexExchange).borrow("BTC-USDT", "BTC", 0.01);
        LOGGER.debug("borrow_id {}", borrow_id);

        String repay_id =  ((OKExExchange)okexExchange).repay("BTC-USDT", "BTC", 0.01, borrow_id);
        LOGGER.debug("repay_id {}", repay_id);
    }

    @Test
    @Ignore
    public void placeOrderShort() {
        OrderInfo s = new OrderInfo(ExchangeEnum.OKEX, "BTC-USDT", OrderSide.SELL, MARKET,10500.0, 0.01);
        okexExchange.placeOrder(s);
        LOGGER.info("SHORT ORDER");
        LOGGER.info(s.toString());

        double priceExecuted = s.getPriceExecuted();
        double amountExecuted = s.getAmountExecuted();
        double longPrice = priceExecuted * (1 + 0.5/100);

        OrderInfo l = new OrderInfo(ExchangeEnum.OKEX, "BTC-USDT", OrderSide.BUY, MARKET, longPrice, amountExecuted);
        okexExchange.placeOrder(l);
        LOGGER.info("LONG ORDER");
        LOGGER.info(l.toString());
    }

    @Test
    @Ignore
    public void placeOrderLong() {
        ((OKExExchange)okexExchange).setMODE(TradingMode.LONG);

        OrderInfo s = new OrderInfo(ExchangeEnum.OKEX, "BTC-USDT", OrderSide.BUY, MARKET, 10800.0, 0.01);
        okexExchange.placeOrder(s);
        LOGGER.info("BUY ORDER");
        LOGGER.info(s.toString());

        double priceExecuted = s.getPriceExecuted();
        double amountExecuted = s.getAmountExecuted();
        double sellPrice = priceExecuted * (1 - 0.5/100);

        OrderInfo l = new OrderInfo(ExchangeEnum.OKEX, "BTC-USDT", OrderSide.SELL, MARKET, sellPrice, amountExecuted);
        okexExchange.placeOrder(l);
        LOGGER.info("SELL ORDER");
        LOGGER.info(l.toString());
    }

}