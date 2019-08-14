package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.common.*;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceExchange;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceRepository;
import org.junit.Ignore;
import org.junit.Test;

import javax.annotation.Resource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore
public class ITBinance extends BaseIT {

    @Resource(name = BinanceRepository.BEAN_NAME_REPOSITORY_BINANCE)
    private Repository repository;

    @Resource(name = BinanceExchange.BEAN_NAME_EXCHANGE_BINANCE)
    private Exchange exchange;

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
