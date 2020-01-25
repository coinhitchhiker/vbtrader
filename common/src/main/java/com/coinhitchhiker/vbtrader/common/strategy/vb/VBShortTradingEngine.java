package com.coinhitchhiker.vbtrader.common.strategy.vb;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.strategy.AbstractTradingEngine;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.coinhitchhiker.vbtrader.common.Util.getClosestMin;
import static org.joda.time.DateTimeZone.UTC;

public class VBShortTradingEngine extends AbstractTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(VBShortTradingEngine.class);
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
                                boolean TRADING_ENABLED, boolean TRAILING_STOP_ENABLED, double TS_TRIGGER_PCT, double TS_PCT, boolean VERBOSE) {

        super(repository, exchange, orderBookCache, TradingMode.SHORT, SYMBOL, QUOTE_CURRENCY, LIMIT_ORDER_PREMIUM, EXCHANGE,
                FEE_RATE, TRADING_ENABLED, TRAILING_STOP_ENABLED, TS_TRIGGER_PCT, TS_PCT, false, 0.5D, VERBOSE);

        this.TRADING_WINDOW_LOOK_BEHIND = TRADING_WINDOW_LOOK_BEHIND;
        this.TRADING_WINDOW_SIZE = TRADING_WINDOW_SIZE;
        this.PRICE_MA_WEIGHT = PRICE_MA_WEIGHT;
        this.VOLUME_MA_WEIGHT = VOLUME_MA_WEIGHT;
    }


    @Override
    public TradeResult trade(double curPrice, long curTimestamp, double curVol) {
        if(this.currentTradingWindow == null) {
            LOGGER.info("currentTradingWindow is null. Initializing it...");
            refreshTradingWindows(curTimestamp);
        }

        double sellSignalStrength = sellSignalStrength(curPrice, curTimestamp);

        if(sellSignalStrength > 0) {
            placeSellOrder(curPrice, sellSignalStrength);

            LOGGER.info("tradingWindow endTime {} curTime {} h {} l {}"
                    , new DateTime(currentTradingWindow.getEndTimeStamp(), UTC)
                    , new DateTime(curTimestamp, UTC)
                    , currentTradingWindow.getHighPrice()
                    , currentTradingWindow.getLowPrice());

            return null;
        }

        double buySignalStrength = buySignalStrength(curPrice, curTimestamp);

        if(buySignalStrength > 0) {
            TradeResult tradeResult = placeBuyOrder(curPrice, buySignalStrength);
            this.refreshTradingWindows(curTimestamp);
            if(tradeResult != null) LOGGERBUYSELL.info("---------------TRADING WINDOW END HIT------------------------");
            return tradeResult;
        }

        if(trailingStopHit(curPrice)) {
            double prevTSPrice = super.trailingStopPrice;
            TradeResult tradeResult = placeBuyOrder(curPrice, 1.0);
            this.refreshTradingWindows(curTimestamp);
            LOGGERBUYSELL.info("trailingStopPrice {} < curPrice {}", prevTSPrice, curPrice);
            LOGGERBUYSELL.info("---------------SHORT TRAILING STOP HIT------------------------");
            return tradeResult;
        }

        if(stopLossHit(curPrice)) {
            double prevSLPrice = super.stopLossPrice;
            TradeResult tradeResult = placeBuyOrder(curPrice, 1.0);
            LOGGERBUYSELL.info("stopLossPrice {} < curPrice {}", prevSLPrice, curPrice);
            LOGGERBUYSELL.info("---------------LONG STOP LOSS HIT------------------------");
            this.refreshTradingWindows(curTimestamp);
            return tradeResult;
        }

        // if a buy order was placed in this trading window and no trailing stop price has been touched
        // we do nothing until this trading window is over
        if(placedSellOrder != null) {
            if(VERBOSE) {
                LOGGER.info(currentTradingWindow.toString());
                LOGGER.info("-----------------------PLACED ORDER PRESENT---------------------");
            }
            return null;
        }

        if(VERBOSE) {
            LOGGER.info("tradingWindow endTime {} curTime {} h {} l {}"
                    , new DateTime(currentTradingWindow.getEndTimeStamp(), UTC)
                    , new DateTime(curTimestamp, UTC)
                    , currentTradingWindow.getHighPrice()
                    , currentTradingWindow.getLowPrice());
        }

        return null;
    }

    @Override
    public double buySignalStrength(double curPrice, long curTimestamp) {
        return curTimestamp > this.currentTradingWindow.getEndTimeStamp() ? 1 : 0;
    }

    @Override
    public double sellSignalStrength(double curPrice, long curTimestamp) {
        if(currentTradingWindow == null) {
            LOGGER.debug("curTradingWindow is null");
            return 0;
        }

        if(placedSellOrder != null) {
            return 0;
        }

        List<TradingWindow> lookbehindTradingWindows = VolatilityBreakout.getLastNTradingWindow(this.tradingWindows, TRADING_WINDOW_LOOK_BEHIND+1);
        if(lookbehindTradingWindows.size() < TRADING_WINDOW_LOOK_BEHIND+1) {
            LOGGER.debug("lookbehindTradingWindows.size() {} < TRADING_WINDOW_LOOK_BEHIND {}", lookbehindTradingWindows.size(), TRADING_WINDOW_LOOK_BEHIND);
            return 0;
        }

        double k = VolatilityBreakout.getKValue(lookbehindTradingWindows);
        boolean priceBreakout = curPrice < currentTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange();

        if(!priceBreakout) {
            if(VERBOSE) {
                LOGGER.info("[---------------------NO SELL SIGNAL DETECTED----------------------------]");
                LOGGER.info("curPrice {} > {} (openPrice {} + k {} * prevRange {})",
                        curPrice ,
                        currentTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange() ,
                        currentTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
            }
            return 0;
        }

        double priceMAScore = VolatilityBreakout.getPriceMAScore(lookbehindTradingWindows, curPrice, 3, TRADING_WINDOW_LOOK_BEHIND);
//        double volume = currentTradingWindow.getVolume();
//        double volumeMAScore = VolatilityBreakout.getVolumeMAScore_conservative(lookbehindTradingWindows, volume, 3, TRADING_WINDOW_LOOK_BEHIND);
        double volumeMAScore = VolatilityBreakout.getVolumeMAScore_aggressive(lookbehindTradingWindows, currentTradingWindow, 3, TRADING_WINDOW_LOOK_BEHIND, TRADING_WINDOW_SIZE, curTimestamp);
        double weightedMAScore = (PRICE_MA_WEIGHT*priceMAScore + VOLUME_MA_WEIGHT*volumeMAScore) / (PRICE_MA_WEIGHT + VOLUME_MA_WEIGHT);

        if(VERBOSE) {
            if(weightedMAScore > 0.0) {
                LOGGER.info("[---------------------SELL SIGNAL DETECTED----------------------------]");
            } else {
                LOGGER.info("[-----------------SELL SIGNAL DETECTED BUT COST IS 0------------------------]");
            }
            LOGGER.info("priceMAScore {} volumeMAScore {} weightedMAScore {}", priceMAScore, volumeMAScore, weightedMAScore);
            LOGGER.info("curPrice {} < {} (openPrice {} + k {} * prevRange {})",
                    curPrice,
                    currentTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange(),
                    currentTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
            LOGGER.info("tradingWindow endTime {}", new DateTime(currentTradingWindow.getEndTimeStamp(), UTC));
        }
        return weightedMAScore;
    }

    @Override
    public void onTradeEvent(TradeEvent e) {
        if(this.refreshingTradingWindows) {
            pendingVol += e.getAmount();
        } else {
            if(this.currentTradingWindow != null) {
                this.updateTrailingStopPrice(e.getPrice());
                double curVol = this.currentTradingWindow.getVolume();
                this.currentTradingWindow.setVolume(curVol + pendingVol + e.getAmount());
                pendingVol = 0;
            }
        }
    }

    private void refreshTradingWindows(long curTimestamp) {
        this.refreshingTradingWindows = true;
        LOGGER.debug("refreshingTradingWindows is set to TRUE");

        DateTime closestMin = getClosestMin(new DateTime(curTimestamp, UTC));

        this.currentTradingWindow = VolatilityBreakout.constructCurrentTradingWindow(SYMBOL, TRADING_WINDOW_SIZE, orderBookCache.getMidPrice(), closestMin.getMillis(), repository);
        LOGGER.debug("Refreshed curTW {}", this.currentTradingWindow.toString());

        this.tradingWindows = VolatilityBreakout.constructPastTradingWindows(curTimestamp, TRADING_WINDOW_SIZE, TRADING_WINDOW_LOOK_BEHIND, SYMBOL, repository);
        this.refreshingTradingWindows = false;

        LOGGER.debug("-----------CURRENT TRADING WINDOW REFRESHED-----------------------");
        LOGGER.debug("curTimestamp {}", new DateTime(curTimestamp, UTC));
        LOGGER.debug("{}", this.currentTradingWindow);
        LOGGER.debug("refreshingTradingWindows is set to FALSE");
    }
}
