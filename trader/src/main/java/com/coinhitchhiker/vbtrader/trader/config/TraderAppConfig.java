package com.coinhitchhiker.vbtrader.trader.config;

import com.coinhitchhiker.vbtrader.common.*;
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

    @Value("${trading.enabled}") boolean tradingEnabled;
    @Value("${trading.exchange}") String exchange;
    @Value("${trading.mode}") private String MODE;

    @Value("${trading.exchange}") private String EXCHANGE;
    @Value("${trading.symbol}") private String SYMBOL;
    @Value("${trading.quote.currency}") private String QUOTE_CURRENCY;
    @Value("${trading.window.size}") private int TRADING_WINDOW_SIZE;
    @Value("${trading.look.behind}") private int TRADING_WINDOW_LOOK_BEHIND;
    @Value("${trading.price.weight}") private double PRICE_MA_WEIGHT;
    @Value("${trading.volume.weight}") private double VOLUME_MA_WEIGHT;
    @Value("${trading.limit.order.premium}") private double LIMIT_ORDER_PREMIUM;
    @Value("${trading.fee.rate}") private double FEE_RATE;

    @Bean
    public TradingEngine tradeEngine() {
        if(MODE.equals("LONG")) {
            return new LongTradingEngine(repository(), exchange(), orderBookCache(), TRADING_WINDOW_LOOK_BEHIND, SYMBOL, QUOTE_CURRENCY, LIMIT_ORDER_PREMIUM, 3, TRADING_WINDOW_SIZE, PRICE_MA_WEIGHT, VOLUME_MA_WEIGHT, exchange, FEE_RATE, tradingEnabled);
        } else {
            return new ShortTradingEngine(repository(), exchange(), orderBookCache(), TRADING_WINDOW_LOOK_BEHIND, SYMBOL, QUOTE_CURRENCY, LIMIT_ORDER_PREMIUM, 3, TRADING_WINDOW_SIZE, PRICE_MA_WEIGHT, VOLUME_MA_WEIGHT, exchange, FEE_RATE, tradingEnabled);
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
        LOGGER.info("Registering " + exchange + " exchange bean");

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
        LOGGER.info("Registering " + exchange + " repository bean");

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
        LOGGER.info("Registering " + exchange + " orderBookCache bean");

        if(exchange.equals("BINANCE")) {
            return new BinanceOrderBookCache();
        } else if(exchange.equals("BITMEX")) {
            return new BitMexOrderBookCache();
        } else  {
            throw new RuntimeException("Unsupported exchange was configured");
        }
    }
}
