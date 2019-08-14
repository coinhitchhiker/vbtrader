package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.trader.TradeEngine;
import com.coinhitchhiker.vbtrader.trader.config.EncryptorHelper;
import com.coinhitchhiker.vbtrader.trader.config.PropertyMapHandler;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceExchange;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceOrderBookCache;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceRepository;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;

@Configuration
@Import(value = {BinanceExchange.class, BinanceRepository.class, BinanceOrderBookCache.class, EncryptorHelper.class,})
public class TraderITConfig {

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
    @Primary
    public PropertyMapHandler propertyMapHandler() {
        return new PropertyMapHandler();
    }

    @Bean
    @Primary
    public ApplicationEventPublisher applicationEventPublisher() {
        return Mockito.mock(ApplicationEventPublisher.class);
    }
}
