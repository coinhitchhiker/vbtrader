package com.coinhitchhiker.vbtrader.common.strategy.vb;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.strategy.AbstractTradingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class VBShortTradingEngine extends AbstractTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(VBLongTradingEngine.class);
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");

    private List<TradingWindow> tradingWindows = new ArrayList<>();
    private TradingWindow currentTradingWindow = null;
    private boolean refreshingTradingWindows = false;
    private double pendingVol = 0;

    private final int TRADING_WINDOW_LOOK_BEHIND;
    private final int TRADING_WINDOW_SIZE;
    private final double PRICE_MA_WEIGHT;
    private final double VOLUME_MA_WEIGHT;

    public VBShortTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                                int TRADING_WINDOW_LOOK_BEHIND, int TRADING_WINDOW_SIZE, double PRICE_MA_WEIGHT, double VOLUME_MA_WEIGHT,
                                String SYMBOL, String QUOTE_CURRENCY, double LIMIT_ORDER_PREMIUM, ExchangeEnum EXCHANGE, double FEE_RATE,
                                boolean TRADING_ENABLED, boolean TRAILING_STOP_ENABLED, double TS_TRIGGER_PCT, double TS_PCT) {

        super(repository, exchange, orderBookCache, TradingMode.SHORT, SYMBOL, QUOTE_CURRENCY, LIMIT_ORDER_PREMIUM, EXCHANGE,
                FEE_RATE, TRADING_ENABLED, TRAILING_STOP_ENABLED, TS_TRIGGER_PCT, TS_PCT, false, 0.5D);

        this.TRADING_WINDOW_LOOK_BEHIND = TRADING_WINDOW_LOOK_BEHIND;
        this.TRADING_WINDOW_SIZE = TRADING_WINDOW_SIZE;
        this.PRICE_MA_WEIGHT = PRICE_MA_WEIGHT;
        this.VOLUME_MA_WEIGHT = VOLUME_MA_WEIGHT;
    }


    @Override
    public TradeResult trade(double curPrice, long curTimestamp) {
        return null;
    }

    @Override
    public double buySignalStrength(double curPrice, long curTimestamp) {
        return 0;
    }

    @Override
    public double sellSignalStrength(double curPrice, long curTimestamp) {
        return 0;
    }

    @Override
    public void onTradeEvent(TradeEvent e) {

    }
}
