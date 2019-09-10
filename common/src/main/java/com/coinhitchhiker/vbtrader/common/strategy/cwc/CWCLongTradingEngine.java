package com.coinhitchhiker.vbtrader.common.strategy.cwc;

import com.coinhitchhiker.vbtrader.common.Util;
import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.strategy.AbstractTradingEngine;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.coinhitchhiker.vbtrader.common.Util.getClosestMin;
import static org.joda.time.DateTimeZone.UTC;

public class CWCLongTradingEngine extends AbstractTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(CWCLongTradingEngine.class);
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");
    private final static int TRADING_WINDOW_LOOK_BEHIND = 10;
    private final static double SLIPPAGE = 0.01;
    private final static double EXPECTED_PROFIT = 0.05;

    private List<TradingWindow> pastTradingWindows = new ArrayList<>();
    private TradingWindow currentTradingWindow = null;
    private boolean SOLD_IN_CUR_TRADING_WIN = false;

    private final int TRADING_WINDOW_SIZE;
    private final double TAIL_LENGTH;

    public CWCLongTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                                String SYMBOL, String QUOTE_CURRENCY, double LIMIT_ORDER_PREMIUM, ExchangeEnum EXCHANGE,
                                double FEE_RATE, boolean TRADING_ENABLED, boolean TRAILING_STOP_ENABLED, double TS_TRIGGER_PCT,
                                double TS_PCT, boolean STOP_LOSS_ENABLED, double STOP_LOSS_PCT, boolean VERBOSE,
                                double TAIL_LENGTH, int TRADING_WINDOW_SIZE) {

        super(repository, exchange, orderBookCache, TradingMode.LONG, SYMBOL, QUOTE_CURRENCY, LIMIT_ORDER_PREMIUM , EXCHANGE, FEE_RATE, TRADING_ENABLED,
                TRAILING_STOP_ENABLED, TS_TRIGGER_PCT, TS_PCT, STOP_LOSS_ENABLED, STOP_LOSS_PCT, VERBOSE);

        this.TRADING_WINDOW_SIZE = TRADING_WINDOW_SIZE;
        this.TAIL_LENGTH = TAIL_LENGTH;

    }

    @Override
    public TradeResult trade(double curPrice, long curTimestamp) {
        if(this.currentTradingWindow == null) {
            LOGGER.info("currentTradingWindow is null. Initializing it...");
            refreshTradingWindows(curTimestamp);
        }

        if(this.currentTradingWindow.getEndTimeStamp() <= curTimestamp) {
            refreshTradingWindows(curTimestamp);
            SOLD_IN_CUR_TRADING_WIN = false;
        }

        double buySignalStrength = buySignalStrength(curPrice, curTimestamp);

        // We don't want to buy when we did trailing stop or stop loss.
        // Should wait until current trading window ends.
        if(placedBuyOrder == null && buySignalStrength > 0 && !SOLD_IN_CUR_TRADING_WIN) {
            placeBuyOrder(curPrice, buySignalStrength);

            LOGGER.info("tradingWindow endTime {} curTime {} h {} l {}"
                    , new DateTime(currentTradingWindow.getEndTimeStamp(), UTC)
                    , new DateTime(curTimestamp, UTC)
                    , currentTradingWindow.getHighPrice()
                    , currentTradingWindow.getLowPrice());

            return null;
        }

        double sellSignalStrength = sellSignalStrength(curPrice, curTimestamp);

        if(placedBuyOrder != null && sellSignalStrength > 0) {
            TradeResult tradeResult = placeSellOrder(curPrice, sellSignalStrength);
            if(tradeResult != null) {
                LOGGERBUYSELL.info("[BALANCE] {}, {} {}", new DateTime(curTimestamp, UTC), QUOTE_CURRENCY, this.exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade());
                LOGGERBUYSELL.info("---------------CWC SELL SIGNAL HIT------------------------");
            }
            SOLD_IN_CUR_TRADING_WIN = true;
            return tradeResult;
        }

        if(placedBuyOrder != null && trailingStopHit(curPrice)) {
            double prevTSPrice = super.trailingStopPrice;
            TradeResult tradeResult = placeSellOrder(curPrice, 1.0);
            LOGGERBUYSELL.info("trailingStopPrice {} > curPrice {}", prevTSPrice, curPrice);
            LOGGERBUYSELL.info("[BALANCE] {}, {} {}", new DateTime(curTimestamp, UTC), QUOTE_CURRENCY, this.exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade());
            LOGGERBUYSELL.info("---------------CWC LONG TRAILING STOP HIT------------------------");
            // when trailing stop hits we sell it and should wait until this trading window ends.
            // Set the flag to true. It'll be set false when trading window ends.
            SOLD_IN_CUR_TRADING_WIN = true;
            return tradeResult;
        }

        if(placedBuyOrder != null && stopLossHit(curPrice)) {
            double prevSLPrice = super.stopLossPrice;
            TradeResult tradeResult = placeSellOrder(curPrice, 1.0);
            LOGGERBUYSELL.info("stopLossPrice {} > curPrice {}", prevSLPrice, curPrice);
            LOGGERBUYSELL.info("[BALANCE] {}, {} {}", new DateTime(curTimestamp, UTC), QUOTE_CURRENCY, this.exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade());
            LOGGERBUYSELL.info("---------------CWC LONG STOP LOSS HIT------------------------");
            SOLD_IN_CUR_TRADING_WIN = true;
            return tradeResult;
        }

        return null;

    }

    @Override
    public double buySignalStrength(double curPrice, long curTimestamp) {
        if(this.pastTradingWindows.size() < TRADING_WINDOW_LOOK_BEHIND) return 0;

        boolean case1 = checkLastTradingWindowStatus() && checkSecondLastTradingWindowStatus();
        boolean case2 = buyException1();
        boolean case3 = buyException2();

        return case1 || case2 || case3 ? 1 : 0;
    }

//        1. 마지막 봉의 상태
//- 종가가 시가보다 커야 함(양봉)
//- 전체길이의 0.2이상되는 아랫꼬리가 있을 경우
//    a. 윗꼬리가 있는지 체크하고, 있다면 아랫꼬리는 윗꼬리보다 길어야함
//- 아랫꼬리가 있으나 전체길이의 0.2가 되지 않는 경우 또는 없는 경우, 즉 (시가 - 저가) < (고가 - 저가)*0.2 에 대해서
//    a.윗꼬리가 있는지 체크하고, 전체길이의 20%미만이어야 함.
    private boolean checkLastTradingWindowStatus() {
        boolean cond1 = this.pastTradingWindows.get(0).isUpCandle() && this.pastTradingWindows.get(1).isDownCandle();
        boolean cond2 = false;

        TradingWindow lastTW = this.pastTradingWindows.get(0);
        if(lastTW.tailLengthRatio() >= TAIL_LENGTH) {
            cond2 = lastTW.headLengthRatio() > 0 && lastTW.tailLengthRatio() > lastTW.headLengthRatio();
        } else {
            cond2 = lastTW.headLengthRatio() > 0 && lastTW.headLengthRatio() < TAIL_LENGTH;
        }

        return cond1 && cond2;
    }

//2. 마지막봉 직전 봉의 상태
//- 직전음봉의 하락 또는 그 이전의 연속적인 음봉들의 총 하락률은 다음보다 커야 한다.
//    a. 수수료*2(매수와 매도)+슬리피지+기대수익
//    b. 하락률은 하락직전의 양봉의 종가에서 마지막 하락의 음봉의 종가의 차
//
//- 만약 아랫꼬리가 존재한다면,
//    a. 전체길이의 20%이상의 비율일 경우, 윗꼬리가 있다면 윗꼬리보다 보다 길어야함.
//
//- 아랫꼬리가 없거나 또는 아랫꼬리가 전체길이의 20% 미만인 경우, 윗꼬리가 존재한다면,
//    a. 전체길이의 20%미만이어야 함.

    private List<TradingWindow> collectTradingWindowsAfterLastUpCandle(boolean includeLastUpCandle) {
        // collect past tradingWindows until it hits plus candle
        if(this.pastTradingWindows.get(1).isUpCandle()) return new ArrayList<>();

        List<TradingWindow> t = new ArrayList<>();
        for(int i = 1; i < this.pastTradingWindows.size(); i++) {
            TradingWindow p = this.pastTradingWindows.get(i);
            t.add(p);
            if(p.isUpCandle()) {
                if(!includeLastUpCandle) t.remove(p);
                break;
            }
        }
        return t;
    }

    private boolean checkSecondLastTradingWindowStatus() {

        List<TradingWindow> t = collectTradingWindowsAfterLastUpCandle(true);

        if(t.size() == 0) return false;

        double dropRate = (t.get(0).getClosePrice() - t.get(t.size()-1).getOpenPrice()) / t.get(t.size()-1).getOpenPrice() * 100;
        boolean dropRateCond = FEE_RATE * 2 + SLIPPAGE + EXPECTED_PROFIT < Math.abs(dropRate);
        boolean tailCond = false;
        if(t.get(0).tailLengthRatio() >= TAIL_LENGTH) {
            tailCond = t.get(0).headLengthRatio() > 0 && t.get(0).tailLengthRatio() > t.get(0).headLengthRatio();
        } else {
            tailCond = t.get(0).headLengthRatio() < TAIL_LENGTH;
        }

        return dropRateCond && tailCond;
    }

//    buy case exception  1
//    - 마지막 양봉 직전의 음봉(들)의 전체하락률이 1프로이상인 급락일경우, 마지막 형성되는 양봉의 윗꼬리가 0.2 이상이라 하더라도 0.5를 넘지 않는 다면, 매수
    private boolean buyException1() {
        List<TradingWindow> t = collectTradingWindowsAfterLastUpCandle(false);
        if(t.size() == 0) return false;

        double dropRate = (t.get(0).getClosePrice() - t.get(t.size()-1).getOpenPrice()) / t.get(t.size()-1).getOpenPrice() * 100;
        if(dropRate <= -1.0) {
            return this.pastTradingWindows.get(0).isUpCandle() &&
                    this.pastTradingWindows.get(0).headLengthRatio() < 0.5 &&
                    this.pastTradingWindows.get(0).headLengthRatio() >= TAIL_LENGTH;
        }
        return false;
    }

//buy case exception 2
//- 마지막 양봉 직전의 음봉의 아랫꼬리가 전체하락률이 0.5이상 길며, 마지막 양봉의 종가가 직전 음봉의 고가 보다 높게 마감한 경우 양봉의 윗꼬리가 0.2 이상이라 하더라도 0.5를 넘지 않는 다면, 매수
    private boolean buyException2() {
        List<TradingWindow> t = collectTradingWindowsAfterLastUpCandle(false);
        if(t.size() == 0) return false;

        double drop = t.get(0).getClosePrice() - t.get(t.size()-1).getOpenPrice();

        return Math.abs((t.get(0).getClosePrice() - t.get(0).getOpenPrice())) / drop > 0.5 &&
                this.pastTradingWindows.get(0).getClosePrice() > t.get(0).getHighPrice() &&
                this.pastTradingWindows.get(0).headLengthRatio() >= TAIL_LENGTH &&
                this.pastTradingWindows.get(0).headLengthRatio() < 0.5;
    }

    @Override
    public double sellSignalStrength(double curPrice, long curTimestamp) {
        if(this.pastTradingWindows.size() < TRADING_WINDOW_LOOK_BEHIND) return 0;

        if(this.pastTradingWindows.get(0).isDownCandle()) {
            return 1.0;
        }

        // now last trading windows is up candle.. but....
        if(this.pastTradingWindows.get(0).headLengthRatio() > this.pastTradingWindows.get(0).tailLengthRatio() &&
            this.pastTradingWindows.get(0).headLengthRatio() > TAIL_LENGTH) {
            return 1.0;
        }

        return 0.0;
    }

    @Override
    public void onTradeEvent(TradeEvent e) {
        super.updateTrailingStopPrice(e.getPrice());
    }

    private void refreshTradingWindows(long curTimestamp) {
        LOGGER.debug("refreshingTradingWindows is set to TRUE");

        DateTime closestMin = getClosestMin(new DateTime(curTimestamp, UTC));

        this.currentTradingWindow = Util.constructCurrentTradingWindow(SYMBOL, TRADING_WINDOW_SIZE, orderBookCache.getMidPrice(), closestMin.getMillis(), repository);
        LOGGER.debug("Refreshed curTW {}", this.currentTradingWindow.toString());

        this.pastTradingWindows = Util.constructPastTradingWindows(curTimestamp, TRADING_WINDOW_SIZE, TRADING_WINDOW_LOOK_BEHIND, SYMBOL, repository);

        LOGGER.debug("-----------CURRENT TRADING WINDOW REFRESHED-----------------------");
        LOGGER.debug("curTimestamp {}", new DateTime(curTimestamp, UTC));
        LOGGER.debug("{}", this.currentTradingWindow);
        LOGGER.debug("refreshingTradingWindows is set to FALSE");
    }
}
