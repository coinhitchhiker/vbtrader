package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.common.model.Exchange;
import com.coinhitchhiker.vbtrader.common.model.OrderBookCache;
import com.coinhitchhiker.vbtrader.common.model.Repository;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceExchange;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceOrderBookCache;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class BinanceTestConfig {
    @Bean
    public Exchange exchange() {
        return new BinanceExchange();
    }

    @Bean
    public Repository repository() {
        return new BinanceRepository();
    }

    @Bean
    public OrderBookCache orderBookCache() {
        return new BinanceOrderBookCache();
    }
}
