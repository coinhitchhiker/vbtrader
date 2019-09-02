package com.coinhitchhiker.vbtrader.trader.config;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.strategy.vb.VBLongTradingEngine;
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
import org.springframework.context.annotation.Configuration;

@Configuration
public class TraderAppConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraderAppConfig.class);

    @Value("${trading.enabled}") boolean TRADING_ENABLED;
    @Value("${trading.exchange}") String EXCHANGE;
    @Value("${trading.mode}") private String MODE;
    @Value("${trading.symbol}") private String SYMBOL;
    @Value("${trading.quote.currency}") private String QUOTE_CURRENCY;
    @Value("${trading.limit.order.premium}") private double LIMIT_ORDER_PREMIUM;
    @Value("${trading.fee.rate}") private double FEE_RATE;

    @Value("${trading.ts.enabled}") private boolean TRAILING_STOP_ENABLED;
    @Value("${trading.ts.trigger.pct}") private double TS_TRIGGER_PCT;
    @Value("${trading.ts.pct}") private double TS_PCT;


    @Value("${trading.strategy}") private String STRATEGY;
    @Value("${trading.vb.window.size}") private int TRADING_WINDOW_SIZE;
    @Value("${trading.vb.look.behind}") private int TRADING_WINDOW_LOOK_BEHIND;
    @Value("${trading.vb.price.weight}") private double PRICE_MA_WEIGHT;
    @Value("${trading.vb.volume.weight}") private double VOLUME_MA_WEIGHT;

    @Bean
    public TradingEngine tradeEngine() {
        if(MODE.equals("LONG") && STRATEGY.equals("VB")) {
            return new VBLongTradingEngine(repository(),
                    exchange(),
                    orderBookCache(),
                    TRADING_WINDOW_LOOK_BEHIND,
                    TRADING_WINDOW_SIZE,
                    PRICE_MA_WEIGHT,
                    VOLUME_MA_WEIGHT,
                    SYMBOL,
                    QUOTE_CURRENCY,
                    LIMIT_ORDER_PREMIUM,
                    ExchangeEnum.valueOf(EXCHANGE),
                    FEE_RATE,
                    TRADING_ENABLED,
                    TRAILING_STOP_ENABLED,
                    TS_TRIGGER_PCT,
                    TS_PCT,
                    true);
        } else {
            throw new UnsupportedOperationException("Not yet supported");
        }
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
        LOGGER.info("Registering " + EXCHANGE + " exchange bean");

        if(EXCHANGE.equals("BINANCE")) {
            return new BinanceExchange();
        } else if(EXCHANGE.equals("BITMEX")) {
            return new BitMexExchange();
        } else  {
            throw new RuntimeException("Unsupported exchange was configured");
        }
    }

    @Bean
    public Repository repository() {
        LOGGER.info("Registering " + EXCHANGE + " repository bean");

        if(EXCHANGE.equals("BINANCE")) {
            return new BinanceRepository();
        } else if(EXCHANGE.equals("BITMEX")) {
            return new BitMexRepository();
        } else  {
            throw new RuntimeException("Unsupported exchange was configured");
        }
    }

    @Bean
    public OrderBookCache orderBookCache() {
        LOGGER.info("Registering " + EXCHANGE + " orderBookCache bean");

        if(EXCHANGE.equals("BINANCE")) {
            return new BinanceOrderBookCache();
        } else if(EXCHANGE.equals("BITMEX")) {
            return new BitMexOrderBookCache();
        } else  {
            throw new RuntimeException("Unsupported exchange was configured");
        }
    }
}
