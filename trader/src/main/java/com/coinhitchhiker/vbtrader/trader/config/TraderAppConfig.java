package com.coinhitchhiker.vbtrader.trader.config;

import com.coinhitchhiker.vbtrader.common.Exchange;
import com.coinhitchhiker.vbtrader.common.OrderBookCache;
import com.coinhitchhiker.vbtrader.common.Repository;
import com.coinhitchhiker.vbtrader.trader.TradeEngine;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceExchange;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceOrderBookCache;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceRepository;
import com.coinhitchhiker.vbtrader.trader.exchange.bitmex.BitMexExchange;
import com.coinhitchhiker.vbtrader.trader.exchange.bitmex.BitMexOrderBookCache;
import com.coinhitchhiker.vbtrader.trader.exchange.bitmex.BitMexRepository;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.annotation.PostConstruct;

@Configuration
public class TraderAppConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraderAppConfig.class);

    @Value("${trading.exchange}") String exchange;

    @Bean
    public TradeEngine tradeEngine() {
        return new TradeEngine();
    }

    @Bean(name="encryptorBean")
    public StringEncryptor stringEncryptor() {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setAlgorithm("PBEWithMD5AndDES");
        config.setKeyObtentionIterations("1000");
        config.setPassword(System.getenv("JASYPT_ENCRYPTOR_PASSWORD"));
        config.setPoolSize("1");
        encryptor.setConfig(config);
        return encryptor;
    }

    @Bean
    public Exchange exchange() {
        LOGGER.info("Registering Exchange Bean. Configured EXCHANGE: " + exchange);

        if(exchange.equals("BINANCE")) {
            return new BinanceExchange();
        } else if(exchange.equals("BITMEX")) {
            return new BitMexExchange();
        } else  {
            throw new RuntimeException("Unsupported exchange was configured");
        }
    }

    @Bean
    public Repository repository() {
        LOGGER.info("Registering Repository Bean. Configured EXCHANGE: " + exchange);

        if(exchange.equals("BINANCE")) {
            return new BinanceRepository();
        } else if(exchange.equals("BITMEX")) {
            return new BitMexRepository();
        } else  {
            throw new RuntimeException("Unsupported exchange was configured");
        }
    }

    @Bean
    public OrderBookCache orderBookCache() {
        LOGGER.info("Registering OrderBookCache Bean. Configured EXCHANGE: " + exchange);

        if(exchange.equals("BINANCE")) {
            return new BinanceOrderBookCache();
        } else if(exchange.equals("BITMEX")) {
            return new BitMexOrderBookCache();
        } else  {
            throw new RuntimeException("Unsupported exchange was configured");
        }
    }

}
