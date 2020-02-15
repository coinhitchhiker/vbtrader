package com.coinhitchhiker.vbtrader.common.strategy.m5scalping.longside;

import com.coinhitchhiker.vbtrader.common.Util;
import com.coinhitchhiker.vbtrader.common.indicator.EMA;
import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.model.event.CandleOpenEvent;
import com.coinhitchhiker.vbtrader.common.model.event.TradeEvent;
import com.coinhitchhiker.vbtrader.common.strategy.AbstractTradingEngine;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.joda.time.DateTimeZone.UTC;

// https://www.youtube.com/watch?v=zhEukjCzXwM
public class M5ScalpingLongEngine  extends AbstractTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(M5ScalpingLongEngine.class);
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");
    public static final String M5_21EMA = "m5_21ema";
    public static final String M5_13EMA = "m5_13ema";
    public static final String M5_8EMA = "m5_8ema";
    public static final String H1_21EMA = "h1_21ema";
    public static final String H1_8EMA = "h1_8ema";

    private final ApplicationEventPublisher eventPublisher;

    private Chart m5Chart = null;
    private Chart h1Chart = null;
    private State curState;

    private final int TRADING_WINDOW_LOOKBEHIND;

    public M5ScalpingLongEngine(ApplicationEventPublisher eventPublisher, Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                                     String SYMBOL, String QUOTE_CURRENCY, ExchangeEnum EXCHANGE, double FEE_RATE,
                                     boolean VERBOSE, int TRADING_WINDOW_LOOKBEHIND, boolean SIMUL, long SIMUL_START
    ) {

        super(repository, exchange, orderBookCache, TradingMode.LONG, SYMBOL, QUOTE_CURRENCY, 0,
                EXCHANGE, FEE_RATE, true, true, 0.1 + FEE_RATE*2,
                0.2 * (0.1 + FEE_RATE*2), false, 0, VERBOSE);

        this.TRADING_WINDOW_LOOKBEHIND = TRADING_WINDOW_LOOKBEHIND;
        this.eventPublisher = eventPublisher;

        if(SIMUL) {
            initChart(SIMUL_START);
        } else {
            initChart(DateTime.now(UTC).getMillis());
        }

        this.curState = new StateINIT(this, eventPublisher);
    }

    private void initChart(long loadBeforeThis) {

        this.m5Chart = Chart.of(TimeFrame.M5, SYMBOL);
        this.m5Chart.addIndicator(new EMA(M5_21EMA, 21));
        this.m5Chart.addIndicator(new EMA(M5_13EMA, 13));
        this.m5Chart.addIndicator(new EMA(M5_8EMA, 8));

        this.h1Chart = Chart.of(TimeFrame.H1, SYMBOL);
        this.h1Chart.addIndicator(new EMA(H1_21EMA, 21));
        this.h1Chart.addIndicator(new EMA(H1_8EMA, 8));

        // M5 is more granular than H1
        long closestCandleOpen = Util.getClosestCandleOpen(new DateTime(loadBeforeThis, UTC), TimeFrame.M5).getMillis();
        // go back to the oldest possible time that this strategy needs
        int backToNCandles = 21 * 5 + TRADING_WINDOW_LOOKBEHIND * 2;

        long startTime = closestCandleOpen - TimeFrame.H1.toSeconds() * 1000 * backToNCandles;
        List<Candle> pastCandles = repository.getCandles(SYMBOL, startTime, loadBeforeThis);

        for(Candle candle : pastCandles) {
            this.m5Chart.onTick(candle.getOpenPrice(), candle.getOpenTime(), 0);
            this.m5Chart.onTick(candle.getHighPrice(), candle.getOpenTime() + 20 * 1000, 0);
            this.m5Chart.onTick(candle.getLowPrice(), candle.getOpenTime() + 40 * 1000, 0);
            this.m5Chart.onTick(candle.getClosePrice(), candle.getCloseTime(), candle.getVolume());

            this.h1Chart.onTick(candle.getOpenPrice(), candle.getOpenTime(), 0);
            this.h1Chart.onTick(candle.getHighPrice(), candle.getOpenTime() + 20 * 1000, 0);
            this.h1Chart.onTick(candle.getLowPrice(), candle.getOpenTime() + 40 * 1000, 0);
            this.h1Chart.onTick(candle.getClosePrice(), candle.getCloseTime(), candle.getVolume());
        }
    }


    @Override
    public TradeResult trade(double curPrice, long curTimestamp, double curVol) {
        State newState = this.curState.nextState(curPrice, curTimestamp, curVol);
        if(!curState.getStateEnum().equals(newState.getStateEnum())) {
            newState.enter(this.exchange, this.SYMBOL);
            this.curState = newState;
        }
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
        if(this.m5Chart != null && this.h1Chart != null) {
            this.updateTrailingStopPrice(e.getPrice(), e.getTradeTime());

            // ORDER MATTERS!!! H1 Chart Confirmation should happen first!!!!
            Candle h1newCandle = this.h1Chart.onTick(e.getPrice(), e.getTradeTime(), e.getAmount());
            if(h1newCandle != null) {
                this.eventPublisher.publishEvent(new CandleOpenEvent(TimeFrame.H1, h1newCandle));
            }

            Candle m5newCandle = this.m5Chart.onTick(e.getPrice(), e.getTradeTime(), e.getAmount());
            if(m5newCandle != null) {
                this.eventPublisher.publishEvent(new CandleOpenEvent(TimeFrame.M5, m5newCandle));
            }
        }
    }

    @EventListener
    public void onCandleOpenEvent(CandleOpenEvent e) {
        double curPrice = e.getNewCandle().getClosePrice();
        long curTimestamp = e.getNewCandle().getOpenTime();
        double curVol = e.getNewCandle().getVolume();

        State newState = this.curState.nextState(curPrice, curTimestamp, curVol);
        if(!curState.getStateEnum().equals(newState.getStateEnum())) {
            newState.enter(this.exchange, this.SYMBOL);
            this.curState = newState;
        }
    }

    @Override
    public StrategyEnum getStrategy() {
        return StrategyEnum.M5SCALPING;
    }

    @Override
    public void printStrategyParams() {
        return;
    }

    public Chart getM5Chart() {
        return m5Chart;
    }

    public Chart getH1Chart() {
        return h1Chart;
    }

    public Exchange getExchange() {
        return this.exchange;
    }

    public String getSymbol() {
        return this.SYMBOL;
    }

    public void setTrailingStopPrice(double price) {
        this.trailingStopPrice = price;
    }

    public void setStopLossPrice(double price) {
        this.stopLossPrice = price;
    }

    public boolean isH1LongTrending() {
        Double ema8_1 = ((EMA)h1Chart.getIndicatorByName(H1_8EMA)).getValueReverse(1);
        Double ema21_1 = ((EMA)h1Chart.getIndicatorByName(H1_21EMA)).getValueReverse(1);
        double prev1_diff = ema8_1 - ema21_1;

        Double ema8_2 = ((EMA)h1Chart.getIndicatorByName(H1_8EMA)).getValueReverse(2);
        Double ema21_2 = ((EMA)h1Chart.getIndicatorByName(H1_21EMA)).getValueReverse(2);
        double prev2_diff = ema8_2 - ema21_2;

        Double ema8_3 = ((EMA)h1Chart.getIndicatorByName(H1_8EMA)).getValueReverse(3);
        Double ema21_3 = ((EMA)h1Chart.getIndicatorByName(H1_21EMA)).getValueReverse(3);
        double prev3_diff = ema8_3 - ema21_3;

        double EMA8_INCREASE_PCT1 = 0.1;
        double EMA8_INCREASE_PCT2 = 0.11;
        if(prev1_diff > prev2_diff && prev2_diff > prev3_diff &&        // 8,21ema 값의 차이가 3연속  증가
                (ema8_1 - ema8_2)/ema8_2*100 > EMA8_INCREASE_PCT1
                && (ema8_2 - ema8_3)/ema8_3*100 > EMA8_INCREASE_PCT2
        ) {
            Candle prev1 = h1Chart.getCandleReverse(1);
            Candle prev2 = h1Chart.getCandleReverse(2);
            Candle prev3 = h1Chart.getCandleReverse(3);

            if(prev1.getOpenPrice() > ema8_1 && prev1.getClosePrice() > ema8_1 &&
                    prev2.getOpenPrice() > ema8_2 && prev2.getClosePrice() > ema8_2 &&
                    prev3.getOpenPrice() > ema8_3 && prev3.getClosePrice() > ema8_3
            ) {
                LOGGER.info("[CheckH1TradeSetup] curTimestamp {}", new DateTime(h1Chart.getCandleReverse(0).getOpenTime(), DateTimeZone.UTC));
                LOGGER.info("[CheckH1TradeSetup] cur Candle {}", h1Chart.getCandleReverse(0));
                LOGGER.info("[CheckH1TradeSetup]");
                return true;
            } else {
                LOGGER.info("[checkH1TradeSetup invalidated]");
                LOGGER.info("[checkH1TradeSetup invalidated]\ncur candle {}", h1Chart.getCandleReverse(0));
                LOGGER.info("[checkH1TradeSetup invalidated]");
            }
        }

        return false;
    }

    public boolean m5PullbackDetected() {

        EMA m5_8ema = (EMA)m5Chart.getIndicatorByName(M5_8EMA);
        EMA m5_13ema = (EMA)m5Chart.getIndicatorByName(M5_13EMA);
        EMA m5_21ema = (EMA)m5Chart.getIndicatorByName(M5_21EMA);

        boolean ema_cond1 = m5_8ema.getValueReverse(1) > m5_13ema.getValueReverse(1)  &&
                m5_13ema.getValueReverse(1) > m5_21ema.getValueReverse(1);

        boolean ema_cond2 = m5_8ema.getValueReverse(2) > m5_13ema.getValueReverse(2)  &&
                m5_13ema.getValueReverse(2) > m5_21ema.getValueReverse(2);

        boolean ema_cond3 = m5_8ema.getValueReverse(3) > m5_13ema.getValueReverse(3)  &&
                m5_13ema.getValueReverse(3) > m5_21ema.getValueReverse(3);

        boolean ema_cond4 = m5_8ema.getValueReverse(4) > m5_13ema.getValueReverse(4)  &&
                m5_13ema.getValueReverse(4) > m5_21ema.getValueReverse(4);

        Candle prev1_candle = m5Chart.getCandleReverse(1);

        boolean pullback_candle = prev1_candle.getOpenPrice() > prev1_candle.getClosePrice() && // negative candle
                m5_8ema.getValueReverse(1)  > prev1_candle.getLowPrice() &&
                prev1_candle.getClosePrice() > m5_21ema.getValueReverse(1);

        return ema_cond1 && ema_cond2 && ema_cond3 && ema_cond4 && pullback_candle;
    }
}
