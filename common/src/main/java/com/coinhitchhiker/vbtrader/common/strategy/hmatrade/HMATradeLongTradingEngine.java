package com.coinhitchhiker.vbtrader.common.strategy.hmatrade;

import com.coinhitchhiker.vbtrader.common.Util;
import com.coinhitchhiker.vbtrader.common.indicator.HullMovingAverage;
import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.strategy.AbstractTradingEngine;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class HMATradeLongTradingEngine extends AbstractTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(HMATradeLongTradingEngine.class);
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");

    private Chart chart = null;

    public HMATradeLongTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                                     TradingMode MODE, String SYMBOL, String QUOTE_CURRENCY, double LIMIT_ORDER_PREMIUM,
                                     ExchangeEnum EXCHANGE, double FEE_RATE, boolean TRADING_ENABLED, boolean TRAILING_STOP_ENABLED,
                                     double TS_TRIGGER_PCT, double TS_PCT, boolean STOP_LOSS_ENABLED, double STOP_LOSS_PCT, boolean VERBOSE,
                                     TimeFrame timeFrame, int HMA_LENGTH, int TRADING_WINDOW_LOOKBEHIND
                                     ) {

        super(repository, exchange, orderBookCache, MODE, SYMBOL, QUOTE_CURRENCY, LIMIT_ORDER_PREMIUM, EXCHANGE, FEE_RATE,
                TRADING_ENABLED, TRAILING_STOP_ENABLED, TS_TRIGGER_PCT, TS_PCT, STOP_LOSS_ENABLED, STOP_LOSS_PCT, VERBOSE);

        initChart(timeFrame, HMA_LENGTH, TRADING_WINDOW_LOOKBEHIND);

    }

    private void initChart(TimeFrame timeFrame, int HMA_LENGTH, int TRADING_WINDOW_LOOKBEHIND) {
        this.chart = Chart.of(timeFrame, SYMBOL);
        this.chart.addIndicator(new HullMovingAverage("hma5", HMA_LENGTH));

        long closestCandleOpen = Util.getClosestCandleOpen(DateTime.now(DateTimeZone.UTC), timeFrame).getMillis();
        int backToNCandles = HMA_LENGTH * 2 + TRADING_WINDOW_LOOKBEHIND * 2;
        long startTime = closestCandleOpen - timeFrame.toSeconds() * 1000 * backToNCandles;
        List<Candle> pastCandles = repository.getCandles(SYMBOL, startTime, DateTime.now(DateTimeZone.UTC).getMillis());

        for(Candle candle : pastCandles) {
            this.chart.onTick(candle.getOpenPrice(), candle.getOpenTime(), 0);
            this.chart.onTick(candle.getHighPrice(), candle.getOpenTime() + 20 * 1000, 0);
            this.chart.onTick(candle.getLowPrice(), candle.getOpenTime() + 40 * 1000, 0);
            this.chart.onTick(candle.getClosePrice(), candle.getCloseTime(), candle.getVolume());
        }
    }

    @Override
    public TradeResult trade(double curPrice, long curTimestamp) {
        this.chart.onTick(curPrice, curTimestamp, 0);

        HullMovingAverage hma = (HullMovingAverage)this.chart.getIndicatorByName("hma5");
        double val = hma.getValueReverse(1);

        //TODO keep working.... 

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
        this.chart.onTick(e.getPrice(), e.getTradeTime(), e.getAmount());
    }
}
