package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.common.Exchange;
import com.coinhitchhiker.vbtrader.common.OrderBookCache;
import com.coinhitchhiker.vbtrader.common.Repository;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceExchange;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceOrderBookCache;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceRepository;
import com.coinhitchhiker.vbtrader.trader.exchange.bitmex.BitMexExchange;
import com.coinhitchhiker.vbtrader.trader.exchange.bitmex.BitMexOrderBookCache;
import com.coinhitchhiker.vbtrader.trader.exchange.bitmex.BitMexRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

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
