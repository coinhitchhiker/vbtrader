package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.common.model.Exchange;
import com.coinhitchhiker.vbtrader.common.model.OrderBookCache;
import com.coinhitchhiker.vbtrader.common.model.Repository;
import com.coinhitchhiker.vbtrader.trader.exchange.bitmex.BitMexExchange;
import com.coinhitchhiker.vbtrader.trader.exchange.bitmex.BitMexOrderBookCache;
import com.coinhitchhiker.vbtrader.trader.exchange.bitmex.BitMexRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BitMexTestConfig {
    @Bean
    public Exchange exchange() {
        return new BitMexExchange();
    }

    @Bean
    public Repository repository() {
        return new BitMexRepository();
    }

    @Bean
    public OrderBookCache orderBookCache() {
        return new BitMexOrderBookCache();
    }
}
