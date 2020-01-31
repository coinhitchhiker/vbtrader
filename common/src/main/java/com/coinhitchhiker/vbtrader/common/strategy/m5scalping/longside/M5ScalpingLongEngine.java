package com.coinhitchhiker.vbtrader.common.strategy.m5scalping.longside;

import com.coinhitchhiker.vbtrader.common.Util;
import com.coinhitchhiker.vbtrader.common.indicator.EMA;
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
}
