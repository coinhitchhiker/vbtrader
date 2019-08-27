package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.common.model.Exchange;
import com.coinhitchhiker.vbtrader.common.model.Repository;
import com.coinhitchhiker.vbtrader.common.model.TradingWindow;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(locations = {"classpath:test.properties"})
@ContextConfiguration(classes = {BinanceTestConfig.class,})
public class ITBinance extends BaseIT {

    @Autowired private Repository repository;
    @Autowired private Exchange exchange;

    @Test
    @Ignore
    public void testGetLastNTradingWindow() {
//        List<TradingWindow> list = this.repository.getLastNTradingWindow(5, 0);
//        assertThat(list.size()).isEqualTo(5);
    }

    @Test
    @Ignore
    public void testListen() {
        try {Thread.sleep(10000L); } catch(Exception e) {}
    }
}
