package com.coinhitchhiker.vbtrader.common.strategy.m5scalping;

import com.coinhitchhiker.vbtrader.common.Util;
import com.coinhitchhiker.vbtrader.common.indicator.EMA;
import com.coinhitchhiker.vbtrader.common.indicator.HullMovingAverage;
import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.model.event.CandleOpenEvent;
import com.coinhitchhiker.vbtrader.common.model.event.TradeEvent;
import com.coinhitchhiker.vbtrader.common.strategy.AbstractTradingEngine;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.joda.time.DateTimeZone.UTC;

public class M5ScalpingLongEngine  extends AbstractTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(M5ScalpingLongEngine.class);
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private Chart chart = null;
    private final TimeFrame TIME_FRAME;
    private final int EMA_LENGTH = 9;
    private final int TRADING_WINDOW_LOOKBEHIND;
    private final String INDI_NAME = "ema9";
    private final double ORDER_AMT = 0.1;
    private final int MAX_ORDER_CNT = 20;
    private Map<String, OrderInfo> placedOrders = new LinkedHashMap<>();

    public M5ScalpingLongEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                                     String SYMBOL, String QUOTE_CURRENCY, ExchangeEnum EXCHANGE, double FEE_RATE,
                                     boolean VERBOSE, TimeFrame TIME_FRAME, int TRADING_WINDOW_LOOKBEHIND,
                                     boolean SIMUL, long SIMUL_START
    ) {

        super(repository, exchange, orderBookCache, TradingMode.LONG, SYMBOL, QUOTE_CURRENCY, 0,
                EXCHANGE, FEE_RATE, true, true, 0.1 + FEE_RATE*2,
                0.2 * (0.1 + FEE_RATE*2), false, 0, VERBOSE);

        this.TIME_FRAME = TIME_FRAME;
        this.TRADING_WINDOW_LOOKBEHIND = TRADING_WINDOW_LOOKBEHIND;

        if(SIMUL) {
            initChart(SIMUL_START);
        } else {
            initChart(DateTime.now(UTC).getMillis());
        }

    }

    private void initChart(long loadBeforeThis) {
        this.chart = Chart.of(TIME_FRAME, SYMBOL);
        this.chart.addIndicator(new EMA(INDI_NAME, EMA_LENGTH));

        long closestCandleOpen = Util.getClosestCandleOpen(new DateTime(loadBeforeThis, UTC), TIME_FRAME).getMillis();
        int backToNCandles = EMA_LENGTH * 2 + TRADING_WINDOW_LOOKBEHIND * 2;
        long startTime = closestCandleOpen - TIME_FRAME.toSeconds() * 1000 * backToNCandles;
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
    @EventListener
    public void onTradeEvent(TradeEvent e) {
        if(this.chart != null) {
            this.updateTrailingStopPrice(e.getPrice(), e.getTradeTime());
            Candle newCandle = this.chart.onTick(e.getPrice(), e.getTradeTime(), e.getAmount());
            if(newCandle != null) {
                this.eventPublisher.publishEvent(new CandleOpenEvent(this.TIME_FRAME, newCandle));
            }
        }
    }

    @EventListener
    public void onCandleOpenEvent(CandleOpenEvent e) {

        TimeFrame timeFrame = e.getTimeFrame();

        if (!timeFrame.equals(this.TIME_FRAME)) {
            return;
        }

        EMA ema9 = (EMA)this.chart.getIndicatorByName(INDI_NAME);
        double ema_1 = ema9.getValueReverse(1);
        double ema_2 = ema9.getValueReverse(2);

    }

    @Override
    public StrategyEnum getStrategy() {
        return null;
    }

    @Override
    public void printStrategyParams() {

    }
}
