package com.coinhitchhiker.vbtrader.common.strategy.hmatrade;

import com.coinhitchhiker.vbtrader.common.Util;
import com.coinhitchhiker.vbtrader.common.indicator.HullMovingAverage;
import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.strategy.AbstractTradingEngine;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanIsNotAFactoryException;

import java.util.List;

public class HMATradeLongTradingEngine extends AbstractTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(HMATradeLongTradingEngine.class);
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");

    private Chart chart = null;
    private final TimeFrame timeFrame;
    private final int HMA_LENGTH;
    private final int TRADING_WINDOW_LOOKBEHIND;

    public HMATradeLongTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                                     String SYMBOL, String QUOTE_CURRENCY, ExchangeEnum EXCHANGE, double FEE_RATE,
                                     boolean VERBOSE, TimeFrame timeFrame, int HMA_LENGTH, int TRADING_WINDOW_LOOKBEHIND,
                                     boolean simul, long SIMUL_START
                                     ) {

        super(repository, exchange, orderBookCache, TradingMode.LONG, SYMBOL, QUOTE_CURRENCY, 0, EXCHANGE, FEE_RATE,
                true, false, 0, 0, false, 0, VERBOSE);

        this.timeFrame = timeFrame;
        this.HMA_LENGTH = HMA_LENGTH;
        this.TRADING_WINDOW_LOOKBEHIND = TRADING_WINDOW_LOOKBEHIND;

        if(simul) {
            initChart(SIMUL_START);
        } else {
            initChart(DateTime.now(DateTimeZone.UTC).getMillis());
        }

    }

    private void initChart(long loadBeforeThis) {
        this.chart = Chart.of(timeFrame, SYMBOL);
        this.chart.addIndicator(new HullMovingAverage("hma5", HMA_LENGTH));

        long closestCandleOpen = Util.getClosestCandleOpen(new DateTime(loadBeforeThis, DateTimeZone.UTC), timeFrame).getMillis();
        int backToNCandles = HMA_LENGTH * 2 + TRADING_WINDOW_LOOKBEHIND * 2;
        long startTime = closestCandleOpen - timeFrame.toSeconds() * 1000 * backToNCandles;
        List<Candle> pastCandles = repository.getCandles(SYMBOL, startTime, loadBeforeThis);

        for(Candle candle : pastCandles) {
            this.chart.onTick(candle.getOpenPrice(), candle.getOpenTime(), 0);
            this.chart.onTick(candle.getHighPrice(), candle.getOpenTime() + 20 * 1000, 0);
            this.chart.onTick(candle.getLowPrice(), candle.getOpenTime() + 40 * 1000, 0);
            this.chart.onTick(candle.getClosePrice(), candle.getCloseTime(), candle.getVolume());
        }
    }

    @Override
    public TradeResult trade(double curPrice, long curTimestamp, double curVol) {
        this.chart.onTick(curPrice, curTimestamp, curVol);

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
        if(this.chart != null) {
            this.chart.onTick(e.getPrice(), e.getTradeTime(), e.getAmount());
        }
    }
}
