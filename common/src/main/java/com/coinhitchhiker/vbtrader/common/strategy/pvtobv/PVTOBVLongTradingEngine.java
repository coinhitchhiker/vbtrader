package com.coinhitchhiker.vbtrader.common.strategy.pvtobv;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.model.event.TradeEvent;
import com.coinhitchhiker.vbtrader.common.strategy.AbstractTradingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;

public class PVTOBVLongTradingEngine extends AbstractTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(PVTOBVLongTradingEngine.class);
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");

    private final int MIN_CANDLE_LOOK_BEHIND;
    private final double PVTOBV_DROP_THRESHOLD;
    private final double PRICE_DROP_THRESHOLD;
    private final double STOP_LOSS_PCT;

    public PVTOBVLongTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                                   String SYMBOL, String QUOTE_CURRENCY, ExchangeEnum EXCHANGE, double FEE_RATE, boolean TRADING_ENABLED,
                                   boolean TRAILING_STOP_ENABLED, double TS_TRIGGER_PCT, double TS_PCT, double LIMIT_PRICE_PREMIUM,
                                   int MIN_CANDLE_LOOK_BEHIND, double PVTOBV_DROP_THRESHOLD, double PRICE_DROP_THRESHOLD, double STOP_LOSS_PCT, boolean VERBOSE) {

        super(repository, exchange, orderBookCache, TradingMode.LONG, SYMBOL, QUOTE_CURRENCY, LIMIT_PRICE_PREMIUM, EXCHANGE,
                FEE_RATE, TRADING_ENABLED, TRAILING_STOP_ENABLED, TS_TRIGGER_PCT, TS_PCT, true, 0.1, VERBOSE);

        this.MIN_CANDLE_LOOK_BEHIND = MIN_CANDLE_LOOK_BEHIND;
        this.PVTOBV_DROP_THRESHOLD = PVTOBV_DROP_THRESHOLD;
        this.PRICE_DROP_THRESHOLD = PRICE_DROP_THRESHOLD;
        this.STOP_LOSS_PCT = STOP_LOSS_PCT;

    }

    @Override
    public TradeResult trade(double curPrice, long curTimestamp, double curVol) {

        TradeResult tradeResult = null;
        double buySignalStrength = buySignalStrength(curPrice, curTimestamp);
        if(buySignalStrength > 0) {
            this.placeBuyOrder(curPrice, buySignalStrength);
            return null;
        }

        if(stopLossHit(curPrice)) {
            tradeResult = placeSellOrder(curPrice, 1.0D);
            LOGGERBUYSELL.info("-------------------STOP LOSS HIT-------------------");
            return tradeResult;
        }

        if(trailingStopHit(curPrice)) {
            double prevTSPrice = super.trailingStopPrice;
            tradeResult = placeSellOrder(curPrice, 1.0D);
            LOGGERBUYSELL.info("trailingStopPrice {} > curPrice {}", prevTSPrice, curPrice);
            LOGGERBUYSELL.info("---------------LONG TRAILING STOP HIT------------------------");
            return tradeResult;
        }

        // if a buy order was placed and no trailing stop price has been touched
        // we do nothing until price hits either stop loss or trailing stop
        if(placedBuyOrder != null) {
            LOGGER.info("-----------------------PLACED ORDER PRESENT---------------------");
            return null;
        }

        return tradeResult;
    }

    @EventListener
    public void onTradeEvent(TradeEvent e) {
        this.updateTrailingStopPrice(e.getPrice(), e.getTradeTime());
    }

    @Override
    public double buySignalStrength(double curPrice, long curTimestamp) {
        if(this.placedBuyOrder != null) {
            return 0;
        }

        List<Candle> pastNCandles = new ArrayList<>();

        for(int i = 0; i < MIN_CANDLE_LOOK_BEHIND; i++) {
            // we must look for complete past candles.. so we go back by 60 seconds to ensure candles are complete.
            long timestamp = curTimestamp - (i+1)*60_000L;
            Candle candle = repository.getCurrentCandle(timestamp);
            if(candle == null) return 0;
            pastNCandles.add(repository.getCurrentCandle(timestamp));
        }

        if(pastNCandles.size() < MIN_CANDLE_LOOK_BEHIND) return 0;

        Candle latestCandle = pastNCandles.get(0);
        Candle oldestCandle = pastNCandles.get(pastNCandles.size()-1);

        boolean buy = (latestCandle.getPvt() - oldestCandle.getPvt()) < PVTOBV_DROP_THRESHOLD &&
                (latestCandle.getObv() - oldestCandle.getObv()) < PVTOBV_DROP_THRESHOLD &&
                (curPrice - oldestCandle.getClosePrice()) / oldestCandle.getClosePrice() * 100 < PRICE_DROP_THRESHOLD;

        return buy ? 1 : 0;
    }
}
