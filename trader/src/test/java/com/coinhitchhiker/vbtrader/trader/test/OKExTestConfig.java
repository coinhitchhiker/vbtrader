package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.common.model.Exchange;
import com.coinhitchhiker.vbtrader.common.model.OrderBookCache;
import com.coinhitchhiker.vbtrader.common.model.Repository;
import com.coinhitchhiker.vbtrader.common.model.TradingMode;
import com.coinhitchhiker.vbtrader.trader.exchange.okex.OKExExchange;
import com.coinhitchhiker.vbtrader.trader.exchange.okex.OKExOrderBookCache;
import com.coinhitchhiker.vbtrader.trader.exchange.okex.OKExRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OKExTestConfig {

    @Bean
    public Exchange exchange() {
        return new OKExExchange(TradingMode.SHORT);
    }

    @Bean
    public Repository repository() {
        return new OKExRepository();
    }

    @Bean
    public OrderBookCache orderBookCache() {
        return new OKExOrderBookCache();
    }
}
