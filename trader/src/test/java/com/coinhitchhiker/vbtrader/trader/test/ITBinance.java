package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.common.*;
import com.coinhitchhiker.vbtrader.trader.db.TraderDAO;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.Binance;
import com.google.gson.Gson;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ITBinance extends BaseIT {

    @Resource(name = Binance.BEAN_NAME_BINANCE)
    private Repository repository;

    @Resource(name = Binance.BEAN_NAME_BINANCE)
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
