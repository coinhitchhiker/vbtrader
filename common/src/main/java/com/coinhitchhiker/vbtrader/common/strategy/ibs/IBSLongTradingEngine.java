package com.coinhitchhiker.vbtrader.common.strategy.ibs;

import com.coinhitchhiker.vbtrader.common.Util;
import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.model.event.TradeEvent;
import com.coinhitchhiker.vbtrader.common.strategy.AbstractTradingEngine;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

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
    private final double IBS_UPPER_THRESHOLD;

    public IBSLongTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                                String SYMBOL, String QUOTE_CURRENCY, double LIMIT_ORDER_PREMIUM, ExchangeEnum EXCHANGE,
                                double FEE_RATE, boolean TRADING_ENABLED, boolean TRAILING_STOP_ENABLED, double TS_TRIGGER_PCT,
                                double TS_PCT, boolean STOP_LOSS_ENABLED, double STOP_LOSS_PCT, int IBS_WINDOW_SIZE,
                                double IBS_LOWER_THRESHOLD, double IBS_UPPER_THRESHOLD, boolean VERBOSE) {

        super(repository, exchange, orderBookCache, TradingMode.LONG, SYMBOL, QUOTE_CURRENCY, LIMIT_ORDER_PREMIUM, EXCHANGE,
                FEE_RATE, TRADING_ENABLED, TRAILING_STOP_ENABLED, TS_TRIGGER_PCT, TS_PCT, STOP_LOSS_ENABLED, STOP_LOSS_PCT, VERBOSE);

        this.IBS_WINDOW_SIZE = IBS_WINDOW_SIZE;
        this.IBS_LOWER_THRESHOLD = IBS_LOWER_THRESHOLD;
        this.IBS_UPPER_THRESHOLD = IBS_UPPER_THRESHOLD;

        LOGGER.info("IBS_WINDOW_SIZE {} IBS_LOWER_THRESHOLD {} IBS_UPPER_THRESHOLD {}", IBS_WINDOW_SIZE, IBS_LOWER_THRESHOLD, IBS_UPPER_THRESHOLD);

    }

    @Override
    public TradeResult trade(double curPrice, long curTimestamp, double curVol) {
        if(this.prevTradingWindow == null) {
            LOGGER.info("prevTradingWindow is null. Initializing it...");
            if(!refreshTradingWindows(curTimestamp)) {
                return null;
            }
        }

        if(this.currentTradingWindow.getEndTimeStamp() < curTimestamp) {
            refreshTradingWindows(curTimestamp);
        }

        // We don't want to buy when we did trailing stop or stop loss.
        // Should wait until current trading window ends.
        if(placedBuyOrder == null) {
            double buySignalStrength = buySignalStrength(curPrice, curTimestamp);
            if(buySignalStrength > 0 && !TS_SL_CUT) {
                placeBuyOrder(curPrice, buySignalStrength);

                LOGGER.info("tradingWindow endTime {} curTime {} h {} l {}"
                        , new DateTime(currentTradingWindow.getEndTimeStamp(), UTC)
                        , new DateTime(curTimestamp, UTC)
                        , currentTradingWindow.getHighPrice()
                        , currentTradingWindow.getLowPrice());

                return null;
            }
        }

        if(placedBuyOrder != null &&
           placedBuyOrder.getExecTimestamp() < this.currentTradingWindow.getStartTimeStamp()) {     // ensures we're on a new trading window
            double sellSignalStrength = sellSignalStrength(curPrice, curTimestamp);
            if(sellSignalStrength > 0) {
                TradeResult tradeResult = placeSellOrder(curPrice, sellSignalStrength);
                if(tradeResult != null) {
                    LOGGERBUYSELL.info("[BALANCE] {}, {} {}", new DateTime(curTimestamp, UTC), QUOTE_CURRENCY, this.exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade());
                    LOGGERBUYSELL.info("---------------TRADING WINDOW END HIT------------------------");
                }
                return tradeResult;
            }
        }

        if(placedBuyOrder != null &&
           placedBuyOrder.getExecTimestamp() < this.currentTradingWindow.getStartTimeStamp() &&
           trailingStopHit(curPrice)) {
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

        return null;
    }

    @Override
    public double buySignalStrength(double curPrice, long curTimestamp) {

        double ibs = (this.prevTradingWindow.getClosePrice() - this.prevTradingWindow.getLowPrice()) / (this.prevTradingWindow.getHighPrice() - this.prevTradingWindow.getLowPrice());
        double signalStrength =  ibs < IBS_LOWER_THRESHOLD ? 1 : 0;
        if(VERBOSE) {
            if(signalStrength > 0) {
                LOGGER.info("--------------------------BUY SIGNAL DETECTED----------------------");
            } else {
                LOGGER.info("--------------------------NO BUY SIGNAL DETECTED----------------------");
            }
            LOGGER.info("curPrice {} curTimestamp {} tradingWindowEnd {} IBS {} (close {} - low {}) / (high {} - low {})"
                    , curPrice
                    , new DateTime(curTimestamp, UTC)
                    , new DateTime(this.currentTradingWindow.getEndTimeStamp(), UTC)
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

        double ibs = (this.prevTradingWindow.getClosePrice() - this.prevTradingWindow.getLowPrice()) / (this.prevTradingWindow.getHighPrice() - this.prevTradingWindow.getLowPrice());
        double signalStrength =  ibs > IBS_UPPER_THRESHOLD ? 1 : 0;
        if(VERBOSE) {
            if(signalStrength > 0) {
                LOGGER.info("--------------------------SELL SIGNAL DETECTED----------------------");
            } else {
                LOGGER.info("--------------------------NO SELL SIGNAL DETECTED----------------------");
            }
            LOGGER.info("curPrice {} curTimestamp {} tradingWindowEnd {} IBS {} (close {} - low {}) / (high {} - low {})"
                    , curPrice
                    , new DateTime(curTimestamp, UTC)
                    , new DateTime(this.currentTradingWindow.getEndTimeStamp(), UTC)
                    , ibs
                    , this.prevTradingWindow.getClosePrice()
                    , this.prevTradingWindow.getLowPrice()
                    , this.prevTradingWindow.getHighPrice()
                    , this.prevTradingWindow.getLowPrice());
        }
        return signalStrength;
    }

    private boolean refreshTradingWindows(long curTimestamp) {
        LOGGER.debug("refreshingTradingWindows is set to TRUE");

        DateTime closestMin = Util.getClosestMin(new DateTime(curTimestamp, UTC));

        this.currentTradingWindow = Util.constructCurrentTradingWindow(SYMBOL, IBS_WINDOW_SIZE, orderBookCache.getMidPrice(), closestMin.getMillis(), repository);

        LOGGER.debug("Refreshed curTW {}", this.currentTradingWindow.toString());

        List<TradingWindow> pastTradingWindows = Util.constructPastTradingWindows(curTimestamp, IBS_WINDOW_SIZE, 1, SYMBOL, repository);

        if(pastTradingWindows.size() > 0) {
            this.prevTradingWindow = pastTradingWindows.get(0);

            LOGGER.debug("-----------CURRENT TRADING WINDOW REFRESHED-----------------------");
            LOGGER.debug("curTimestamp {}", new DateTime(curTimestamp, UTC));
            LOGGER.debug("{}", this.currentTradingWindow);
            LOGGER.debug("refreshingTradingWindows is set to FALSE");

            return true;
        } else {

            LOGGER.debug("-----------CURRENT TRADING WINDOW NOT REFRESHED-----------------------");
            LOGGER.debug("curTimestamp {}", new DateTime(curTimestamp, UTC));
            LOGGER.debug("{}", this.currentTradingWindow);
            LOGGER.debug("refreshingTradingWindows is set to FALSE");

            return false;
        }
    }

    @Override
    @EventListener
    public void onTradeEvent(TradeEvent e) {
        super.updateTrailingStopPrice(e.getPrice(), e.getTradeTime());
    }

    public void printStrategyParams() {
        LOGGERBUYSELL.info("IBS_LOWER_THRESHOLD {}", this.IBS_LOWER_THRESHOLD);
        LOGGERBUYSELL.info("IBS_UPPER_THRESHOLD {}", this.IBS_UPPER_THRESHOLD);
        LOGGERBUYSELL.info("STOP_LOSS_PCT {}", this.STOP_LOSS_PCT);
        LOGGERBUYSELL.info("TS_TRIGGER_PCT {}", this.TS_TRIGGER_PCT);
        LOGGERBUYSELL.info("TS_PCT {}", this.TS_PCT);
    }
}
