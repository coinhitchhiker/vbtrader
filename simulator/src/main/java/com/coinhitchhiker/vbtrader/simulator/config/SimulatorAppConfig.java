package com.coinhitchhiker.vbtrader.simulator.config;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.strategy.hmatrade.HMATradingEngine;
import com.coinhitchhiker.vbtrader.common.strategy.ibs.IBSLongTradingEngine;
import com.coinhitchhiker.vbtrader.common.strategy.vb.VBLongTradingEngine;
import com.coinhitchhiker.vbtrader.simulator.*;
import com.coinhitchhiker.vbtrader.simulator.db.SimulatorDAO;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimulatorAppConfig {

    @Autowired
    private SimulatorDAO simulatorDAO;

    @Value("${trading.enabled}") boolean TRADING_ENABLED;
    @Value("${trading.exchange}") String EXCHANGE;
    @Value("${trading.simul.start}") String SIMUL_START;    // YYYYMMDD
    @Value("${trading.simul.end}") String SIMUL_END;    // YYYYMMDD
    @Value("${trading.quote.currency}") String QUOTE_CURRRENCY;    // USDT? BTC?

    @Value("${trading.mode}") private String MODE;
    @Value("${trading.symbol}") private String SYMBOL;
    @Value("${trading.quote.currency}") private String QUOTE_CURRENCY;
    @Value("${trading.limit.order.premium}") private double LIMIT_ORDER_PREMIUM;
    @Value("${trading.fee.rate}") private double FEE_RATE;
    @Value("${trading.slippage}") private double SLIPPAGE;
    @Value("${trading.chart.time.frame}") private String TIMEFRAME;

    @Value("${trading.ts.enabled}") private boolean TRAILING_STOP_ENABLED;
    @Value("${trading.ts.trigger.pct}") private double TS_TRIGGER_PCT;
    @Value("${trading.ts.pct}") private double TS_PCT;
    @Value("${trading.stoploss.enabled}") private boolean STOP_LOSS_ENABLED;
    @Value("${trading.stoploss.pct}") private double STOP_LOSS_PCT;

    @Value("${trading.strategy}") private String STRATEGY;

    // Volatility Breakout related params
    @Value("${strategy.vb.window.size}") private int TRADING_WINDOW_SIZE;
    @Value("${strategy.vb.look.behind}") private int TRADING_WINDOW_LOOK_BEHIND;
    @Value("${strategy.vb.price.weight}") private double PRICE_MA_WEIGHT;
    @Value("${strategy.vb.volume.weight}") private double VOLUME_MA_WEIGHT;

    // Internal Bar Strength related params
    @Value("${strategy.ibs.window.size}") private int IBS_WINDOW_SIZE;
    @Value("${strategy.ibs.lower.threshold}") private double IBS_LOWER_THRESHOLD;
    @Value("${strategy.ibs.upper.threshold}") private double IBS_UPPER_THRESHOLD;

    // HMATrading Long related params
    @Value("${strategy.hma_trade.hma_length}") private int HMA_LENGTH;
    @Value("${strategy.hma_trade.look.behind}") private int HMA_TRADE_LOOK_BEHIND;

    @Bean
    public TradingEngine tradingEngine() {
        if(MODE.equals("LONG") && STRATEGY.equals("VB")) {
            return new VBLongTradingEngine(repository(), exchange(), orderBookCache(), TRADING_WINDOW_LOOK_BEHIND,
                    TRADING_WINDOW_SIZE, PRICE_MA_WEIGHT, VOLUME_MA_WEIGHT, SYMBOL, QUOTE_CURRENCY,
                    LIMIT_ORDER_PREMIUM, ExchangeEnum.valueOf(EXCHANGE), FEE_RATE, TRADING_ENABLED,
                    TRAILING_STOP_ENABLED, TS_TRIGGER_PCT, TS_PCT, true);
        } else if (MODE.equals("LONG") && STRATEGY.equals("IBS")) {
            return new IBSLongTradingEngine(repository(), exchange(), orderBookCache(), SYMBOL, QUOTE_CURRENCY, LIMIT_ORDER_PREMIUM,
                    ExchangeEnum.valueOf(EXCHANGE), FEE_RATE, TRADING_ENABLED, TRAILING_STOP_ENABLED, TS_TRIGGER_PCT, TS_PCT,
                    STOP_LOSS_ENABLED, STOP_LOSS_PCT, IBS_WINDOW_SIZE, IBS_LOWER_THRESHOLD, IBS_UPPER_THRESHOLD, true);
        } else if(STRATEGY.equals("HMA_TRADE")) {
            return new HMATradingEngine(repository(), exchange(), orderBookCache(), SYMBOL, QUOTE_CURRRENCY,
                    ExchangeEnum.valueOf(EXCHANGE), FEE_RATE, true, TimeFrame.valueOf(TIMEFRAME), HMA_LENGTH,
                    HMA_TRADE_LOOK_BEHIND, true,
                    DateTime.parse(SIMUL_START, DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).getMillis(),
                    TradingMode.valueOf(MODE)
            );
        } else {
            throw new UnsupportedOperationException("Not yet supported");
        }
    }

    @Bean
    public SimulatorExchange exchange() {
        return new SimulatorExchange(SLIPPAGE);
    }

    @Bean
    public SimulatorRepositoryImpl repository() {
        return new SimulatorRepositoryImpl(ExchangeEnum.valueOf(EXCHANGE), SYMBOL
                , DateTime.parse(SIMUL_START, DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).getMillis()
                , DateTime.parse(SIMUL_END, DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).plusDays(1).getMillis()
                , simulatorDAO, false);
    }

    @Bean
    public SimulatorOrderBookCache orderBookCache() {
        return new SimulatorOrderBookCache(EXCHANGE, SYMBOL);
    }

    @Bean
    public Simulator simulator() {
        return new Simulator(simulatorDAO
                , DateTime.parse(SIMUL_START, DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).getMillis()
                , DateTime.parse(SIMUL_END, DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).plusDays(1).getMillis()
                , ExchangeEnum.valueOf(EXCHANGE)
                , SYMBOL
                , TS_TRIGGER_PCT
                , TS_PCT
                , TradingMode.valueOf(MODE)
                ,QUOTE_CURRENCY
       );
    }
}

