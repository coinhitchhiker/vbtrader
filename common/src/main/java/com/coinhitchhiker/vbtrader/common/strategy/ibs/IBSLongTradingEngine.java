package com.coinhitchhiker.vbtrader.common.strategy.ibs;

import com.coinhitchhiker.vbtrader.common.Util;
import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.strategy.AbstractTradingEngine;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.joda.time.DateTimeZone.UTC;

public class IBSLongTradingEngine extends AbstractTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(IBSLongTradingEngine.class);
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");

    private TradingWindow prevTradingWindow = null;
    private TradingWindow currentTradingWindow = null;
    private boolean TS_SL_CUT = false;

    private final int IBS_WINDOW_SIZE;
    private final double IBS_LOWER_THRESHOLD;

    public IBSLongTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                                String SYMBOL, String QUOTE_CURRENCY, double LIMIT_ORDER_PREMIUM, ExchangeEnum EXCHANGE, double FEE_RATE,
                                boolean TRADING_ENABLED, boolean TRAILING_STOP_ENABLED, double TS_TRIGGER_PCT, double TS_PCT, double STOP_LOSS_PCT,
                                int IBS_WINDOW_SIZE, double IBS_LOWER_THRESHOLD, boolean VERBOSE) {

        super(repository, exchange, orderBookCache, TradingMode.LONG, SYMBOL, QUOTE_CURRENCY, LIMIT_ORDER_PREMIUM, EXCHANGE,
                FEE_RATE, TRADING_ENABLED, TRAILING_STOP_ENABLED, TS_TRIGGER_PCT, TS_PCT, true, STOP_LOSS_PCT, VERBOSE);

        this.IBS_WINDOW_SIZE = IBS_WINDOW_SIZE;
        this.IBS_LOWER_THRESHOLD = IBS_LOWER_THRESHOLD;
    }

    @Override
    public TradeResult trade(double curPrice, long curTimestamp) {
        if(this.currentTradingWindow == null) {
            LOGGER.info("currentTradingWindow is null. Initializing it...");
            refreshTradingWindows(curTimestamp);
            return null;
        }

        double buySignalStrength = buySignalStrength(curPrice, curTimestamp);

        // We don't want to buy when we did trailing stop or stop loss.
        // Should wait until current trading window ends.
        if(placedBuyOrder == null && buySignalStrength > 0 && !TS_SL_CUT) {
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
                LOGGERBUYSELL.info("---------------TRADING WINDOW END HIT------------------------");
            }
            // IBS algo sells when it hits the end of trading window. That's why we refresh the trading window here
            // no matter what.
            refreshTradingWindows(curTimestamp);
            TS_SL_CUT = false;
            return tradeResult;
        }

        if(placedBuyOrder != null && trailingStopHit(curPrice)) {
            double prevTSPrice = super.trailingStopPrice;
            TradeResult tradeResult = placeSellOrder(curPrice, 1.0);
            LOGGERBUYSELL.info("trailingStopPrice {} > curPrice {}", prevTSPrice, curPrice);
            LOGGERBUYSELL.info("[BALANCE] {}, {} {}", new DateTime(curTimestamp, UTC), QUOTE_CURRENCY, this.exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade());
            LOGGERBUYSELL.info("---------------IBS LONG TRAILING STOP HIT------------------------");
            // when trailing stop hits we sell it and should wait until this trading window ends.
            // Set the flag to true. It'll be set false when trading window ends.
            TS_SL_CUT = true;
            return tradeResult;
        }

        if(placedBuyOrder != null && stopLossHit(curPrice)) {
            double prevSLPrice = super.stopLossPrice;
            TradeResult tradeResult = placeSellOrder(curPrice, 1.0);
            LOGGERBUYSELL.info("stopLossPrice {} > curPrice {}", prevSLPrice, curPrice);
            LOGGERBUYSELL.info("[BALANCE] {}, {} {}", new DateTime(curTimestamp, UTC), QUOTE_CURRENCY, this.exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade());
            LOGGERBUYSELL.info("---------------IBS LONG STOP LOSS HIT------------------------");
            TS_SL_CUT = true;
            return tradeResult;
        }

        if(this.currentTradingWindow.getEndTimeStamp() < curTimestamp) {
            refreshTradingWindows(curTimestamp);
            TS_SL_CUT = false;
        }

        return null;

    }

    @Override
    public double buySignalStrength(double curPrice, long curTimestamp) {
        if(this.prevTradingWindow == null) return 0;

        double ibs = (this.prevTradingWindow.getClosePrice() - this.prevTradingWindow.getLowPrice()) / (this.prevTradingWindow.getHighPrice() - this.prevTradingWindow.getLowPrice());
        double signalStrength =  ibs <= IBS_LOWER_THRESHOLD ? 1 : 0;
        if(VERBOSE) {
            if(signalStrength > 0) {
                LOGGER.info("--------------------------BUY SIGNAL DETECTED----------------------");
            } else {
                LOGGER.info("--------------------------NO BUY SIGNAL DETECTED----------------------");
            }
            LOGGER.info("curPrice {} curTimestamp {} IBS {} (close {} - low {}) / (high {} - low {})"
                    , curPrice
                    , new DateTime(curTimestamp, UTC)
                    , ibs
                    , this.prevTradingWindow.getClosePrice()
                    , this.prevTradingWindow.getLowPrice()
                    , this.prevTradingWindow.getHighPrice()
                    , this.prevTradingWindow.getLowPrice());
        }
        return signalStrength;
    }

    @Override
    public double sellSignalStrength(double curPrice, long curTimestamp) {
        if(this.prevTradingWindow == null) return 0;

        return curTimestamp > this.currentTradingWindow.getEndTimeStamp() ? 1 : 0;
    }

    private void refreshTradingWindows(long curTimestamp) {
        LOGGER.debug("refreshingTradingWindows is set to TRUE");

        DateTime closestMin = Util.getClosestMin(new DateTime(curTimestamp, UTC));

        this.currentTradingWindow = Util.constructCurrentTradingWindow(SYMBOL, IBS_WINDOW_SIZE, orderBookCache.getMidPrice(), closestMin.getMillis(), repository);
        LOGGER.debug("Refreshed curTW {}", this.currentTradingWindow.toString());

        List<TradingWindow> pastTradingWindows = Util.constructPastTradingWindows(curTimestamp, IBS_WINDOW_SIZE, 1, SYMBOL, repository);

        if(pastTradingWindows.size() > 0) {
            this.prevTradingWindow = pastTradingWindows.get(0);
        }

        LOGGER.debug("-----------CURRENT TRADING WINDOW REFRESHED-----------------------");
        LOGGER.debug("curTimestamp {}", new DateTime(curTimestamp, UTC));
        LOGGER.debug("{}", this.currentTradingWindow);
        LOGGER.debug("refreshingTradingWindows is set to FALSE");
    }

    @Override
    public void onTradeEvent(TradeEvent e) {
        super.updateTrailingStopPrice(e.getPrice());
    }
}