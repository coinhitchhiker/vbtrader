package com.coinhitchhiker.vbtrader.common.strategy.vb;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.strategy.AbstractTradingEngine;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.coinhitchhiker.vbtrader.common.Util.getClosestMin;
import static org.joda.time.DateTimeZone.UTC;

public class VBLongTradingEngine extends AbstractTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(VBLongTradingEngine.class);
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");

    private List<TradingWindow> tradingWindows = new ArrayList<>();
    private TradingWindow currentTradingWindow = null;
    private boolean refreshingTradingWindows = false;

    private final int TRADING_WINDOW_LOOK_BEHIND;
    private final int TRADING_WINDOW_SIZE;
    private final double PRICE_MA_WEIGHT;
    private final double VOLUME_MA_WEIGHT;

    public VBLongTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                               int TRADING_WINDOW_LOOK_BEHIND, int TRADING_WINDOW_SIZE, double PRICE_MA_WEIGHT, double VOLUME_MA_WEIGHT,
                               String SYMBOL, String QUOTE_CURRENCY, double LIMIT_ORDER_PREMIUM, String EXCHANGE, double FEE_RATE,
                               boolean TRADING_ENABLED, boolean TRAILING_STOP_ENABLED, double TS_TRIGGER_PCT, double TS_PCT) {

        super(repository, exchange, orderBookCache, "LONG", SYMBOL, QUOTE_CURRENCY, LIMIT_ORDER_PREMIUM, EXCHANGE,
                FEE_RATE, TRADING_ENABLED, TRAILING_STOP_ENABLED, TS_TRIGGER_PCT, TS_PCT);

        this.TRADING_WINDOW_LOOK_BEHIND = TRADING_WINDOW_LOOK_BEHIND;
        this.TRADING_WINDOW_SIZE = TRADING_WINDOW_SIZE;
        this.PRICE_MA_WEIGHT = PRICE_MA_WEIGHT;
        this.VOLUME_MA_WEIGHT = VOLUME_MA_WEIGHT;

    }

    public void init(long curTimestamp) {
        refreshTradingWindows(curTimestamp);
    }

    @Override
    public TradeResult trade(double curPrice, long curTimestamp) {
        if(this.currentTradingWindow == null) {
            throw new RuntimeException("currentTradingWindow is null. call init() first");
        }

        double buySignalStrength = buySignalStrength(curPrice, curTimestamp);

        if(buySignalStrength > 0) {
            placeBuyOrder(curPrice, buySignalStrength);

            LOGGER.info("tradingWindow endTime {} curTime {} h {} l {}"
                    , new DateTime(currentTradingWindow.getEndTimeStamp(), UTC)
                    , new DateTime(curTimestamp, UTC)
                    , currentTradingWindow.getHighPrice()
                    , currentTradingWindow.getLowPrice());

            return null;
        }

        if(curTimestamp > currentTradingWindow.getEndTimeStamp()) {
            TradeResult tradeResult = null;
            if(sellSignal(curPrice, curTimestamp)) {
                tradeResult = placeSellOrder(curTimestamp);
                super.clearOutOrders();
            }
            this.refreshTradingWindows(curTimestamp);
            return tradeResult;
        }

        // TRAILING STOP
        if(TRAILING_STOP_ENABLED && placedBuyOrder != null && super.trailingStopPrice > curPrice) {
            LOGGERBUYSELL.info("trailingStopPrice {} > curPrice {}", super.trailingStopPrice, curPrice);
            LOGGERBUYSELL.info("---------------LONG TRAILING STOP HIT------------------------");
            TradeResult tradeResult = placeSellOrder(curTimestamp);
            super.clearOutOrders();
            return tradeResult;
        }

        // if a buy order was placed in this trading window and no trailing stop price has been touched
        // we do nothing until this trading window is over
        if(placedBuyOrder != null) {
            LOGGER.info("-----------------------PLACED ORDER PRESENT---------------------");
            LOGGER.info(currentTradingWindow.toString());
            return null;
        }

        LOGGER.info("tradingWindow endTime {} curTime {} h {} l {}"
            , new DateTime(currentTradingWindow.getEndTimeStamp(), UTC)
            , new DateTime(curTimestamp, UTC)
            , currentTradingWindow.getHighPrice()
            , currentTradingWindow.getLowPrice());

        return null;
    }

    @Override
    public double buySignalStrength(double curPrice, long curTimestamp) {
        if(currentTradingWindow == null) {
            LOGGER.debug("curTradingWindow is null");
            return 0;
        }

        if(placedBuyOrder != null) {
            return 0;
        }

        List<TradingWindow> lookbehindTradingWindows = this.getLastNTradingWindow(TRADING_WINDOW_LOOK_BEHIND+1);
        if(lookbehindTradingWindows.size() < TRADING_WINDOW_LOOK_BEHIND+1) {
            LOGGER.debug("lookbehindTradingWindows.size() {} < TRADING_WINDOW_LOOK_BEHIND {}", lookbehindTradingWindows.size(), TRADING_WINDOW_LOOK_BEHIND);
            return 0;
        }

        double k = VolatilityBreakout.getKValue(lookbehindTradingWindows);
        boolean priceBreakout = curPrice > currentTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange();

        if(!priceBreakout) {
            LOGGER.info("[---------------------NO BUY SIGNAL DETECTED----------------------------]");
            LOGGER.info("curPrice {} < {} (openPrice {} + k {} * prevRange {})",
                    curPrice ,
                    currentTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange() ,
                    currentTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
            return 0;
        }

//        double volume = curTradingWindow.getVolume();
//        double volumeMAScore = VolatilityBreakoutRules.getVolumeMAScore_conservative(lookbehindTradingWindows, volume, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
        double priceMAScore = VolatilityBreakout.getPriceMAScore(lookbehindTradingWindows, curPrice, 3, TRADING_WINDOW_LOOK_BEHIND);
        double volumeMAScore = VolatilityBreakout.getVolumeMAScore_aggressive(lookbehindTradingWindows, currentTradingWindow, 3, TRADING_WINDOW_LOOK_BEHIND, TRADING_WINDOW_SIZE, curTimestamp);
        double weightedMAScore = (PRICE_MA_WEIGHT*priceMAScore + VOLUME_MA_WEIGHT*volumeMAScore) / (PRICE_MA_WEIGHT + VOLUME_MA_WEIGHT);

        if(weightedMAScore > 0) {
            LOGGER.info("[---------------------BUY SIGNAL DETECTED----------------------------]");
            LOGGER.info("curPrice {} > {} (openPrice {} + k {} * prevRange {})",
                    curPrice,
                    currentTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange(),
                    currentTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
            return weightedMAScore;
        } else {
            LOGGER.info("[-----------------BUY SIGNAL DETECTED BUT COST IS 0------------------------]");
            LOGGER.info("curPrice {} > {} (openPrice {} + k {} * prevRange {})",
                    curPrice ,
                    currentTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange() ,
                    currentTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
            LOGGER.info("tradingWindow endTime {}", new DateTime(currentTradingWindow.getEndTimeStamp(), UTC));
            return 0;
        }
    }

    @Override
    public boolean sellSignal(double curPrice, long curTimestamp) {
        return placedBuyOrder != null && curTimestamp > currentTradingWindow.getEndTimeStamp();
    }

    @EventListener
    public void onTradeEvent(TradeEvent e) {
        super.updateTrailingStopPrice(e.getPrice());
    }

    private List<TradingWindow> convertCandleListToTradingWindows(List<Candle> candles, int tradingWindowSizeInMin) {
        List<TradingWindow> result = new ArrayList<>();
        List<Candle> tempList = new ArrayList<>();
        for(int i = 1; i <= candles.size(); i++) {
            tempList.add(candles.get(i-1));
            if(i % tradingWindowSizeInMin == 0) {
                TradingWindow tw = TradingWindow.of(tempList);
                tw.setTS_TRIGGER_PCT(TS_TRIGGER_PCT);
                tw.setTS_PCT(TS_PCT);

                result.add(tw);
                tempList = new ArrayList<>();
            }
        }

        // residual candles
        if(tempList.size() > 0) {
            TradingWindow tw = TradingWindow.of(tempList);
            tw.setTS_TRIGGER_PCT(TS_TRIGGER_PCT);
            tw.setTS_PCT(this.TS_PCT);
            result.add(tw);
        }

        return result;
    }

    private TradingWindow constructCurrentTradingWindow(long timestamp) {

        TradingWindow result =  new TradingWindow(SYMBOL, timestamp, timestamp + TRADING_WINDOW_SIZE * 60 * 1000, orderBookCache.getMidPrice());
        result.setTS_TRIGGER_PCT(TS_TRIGGER_PCT);
        result.setTS_PCT(TS_PCT);

        List<Candle> candles = repository.getCandles(SYMBOL, timestamp, timestamp + TRADING_WINDOW_SIZE * 60 * 1000);
        if(candles.size() > 0) {
            TradingWindow twFromBinanceData = TradingWindow.of(candles);
            result.setHighPrice(twFromBinanceData.getHighPrice());
            result.setOpenPrice(twFromBinanceData.getOpenPrice());
            result.setLowPrice(twFromBinanceData.getLowPrice());
        } else {
            // wait for 10s to ensure receiving orderbook data
            try {Thread.sleep(10_000L); } catch(Exception e) {}
            double price = orderBookCache.getMidPrice();
            if(price == 0.0) {
                throw new RuntimeException("orderbook mid price is 0.0!!!!!");
            }
            result.setOpenPrice(price);
            result.setHighPrice(price);
            result.setLowPrice(price);
        }
        return result;
    }

    private void refreshTradingWindows(long curTimestamp) {
        this.refreshingTradingWindows = true;
        LOGGER.debug("refreshingTradingWindows is set to TRUE");

        DateTime closestMin = getClosestMin(new DateTime(curTimestamp, UTC));

        this.currentTradingWindow = constructCurrentTradingWindow(closestMin.getMillis());
        LOGGER.debug("Refreshed curTW {}", this.currentTradingWindow.toString());

        this.tradingWindows = new ArrayList<>();
        long closestMinMillis = closestMin.getMillis();

        long windowEnd = closestMinMillis - 1000;
        long windowStart = windowEnd - TRADING_WINDOW_SIZE * 60 * 1000;

        for(int i = 0; i <= TRADING_WINDOW_LOOK_BEHIND; i++) {
            List<Candle> candles = repository.getCandles(SYMBOL, windowStart, windowEnd);
            if(candles == null || candles.size() == 0) {
                LOGGER.warn("No candle data was found from repository");
            } else {
                TradingWindow tw = TradingWindow.of(candles);
                LOGGER.debug("{}/{} {}", i, TRADING_WINDOW_LOOK_BEHIND, tw.toString());
                this.tradingWindows.add(tw);
            }

            windowEnd -= TRADING_WINDOW_SIZE * 60 * 1000;
            windowStart -= TRADING_WINDOW_SIZE * 60 * 1000;
            try {Thread.sleep(300L);} catch(Exception e) {}
        }
        this.refreshingTradingWindows = false;

        LOGGER.debug("-----------CURRENT TRADING WINDOW REFRESHED-----------------------");
        LOGGER.debug("curTimestamp {}", new DateTime(curTimestamp, UTC));
        LOGGER.debug("{}", this.currentTradingWindow);
        LOGGER.debug("refreshingTradingWindows is set to FALSE");
    }

    private List<TradingWindow> getLastNTradingWindow(int n) {
        return this.tradingWindows.stream().limit(n).collect(Collectors.toList());
    }

}
