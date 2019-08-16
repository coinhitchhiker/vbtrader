package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.common.*;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceExchange;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceOrderBookCache;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceRepository;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import javax.annotation.Resource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(locations = {"classpath:test.properties"})
@ContextConfiguration(classes = {BinanceTestConfig.class,})
public class ITBinance extends BaseIT {

    @Autowired private Repository repository;
    @Autowired private Exchange exchange;

    @Test
    public void testGetLastNTradingWindow() {
        List<TradingWindow> list = this.repository.getLastNTradingWindow(5, 0);
        assertThat(list.size()).isEqualTo(5);
    }

    @Test
    public void testListen() {
        try {Thread.sleep(10000L); } catch(Exception e) {}
    }
}
