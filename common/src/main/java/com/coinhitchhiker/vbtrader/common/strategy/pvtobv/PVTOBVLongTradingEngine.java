package com.coinhitchhiker.vbtrader.common.strategy.pvtobv;

import com.coinhitchhiker.vbtrader.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class PVTOBVLongTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(PVTOBVLongTradingEngine.class);
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");

    private final Repository repository;
    private final Exchange exchange;

    private final int MIN_CANDLE_LOOK_BEHIND;
    private final String SYMBOL;
    private final String QUOTE_CURRENCY;
    private final String EXCHANGE;
    private final double FEE_RATE;
    private final boolean TRADING_ENABLED;
    private final double TS_TRIGGER_PCT;
    private final double TS_PCT;
    private final double PVTOBV_DROP_THRESHOLD;
    private final double PRICE_DROP_THRESHOLD;
    private final double LIMIT_PRICE_PREMIUM;
    private final double STOP_LOSS_PCT;

    private OrderInfo placedBuyOrder = null;
    private double trailingStopPrice = 0.0D;
    private double stopLossPrice = 0.0D;
    private double prevPrice = Double.MAX_VALUE;

    public PVTOBVLongTradingEngine(Repository repository, Exchange exchange, double LIMIT_PRICE_PREMIUM,
                                   int MIN_CANDLE_LOOK_BEHIND, String SYMBOL, String QUOTE_CURRENCY, String EXCHANGE,
                                   double FEE_RATE, boolean TRADING_ENABLED, double TS_TRIGGER_PCT, double TS_PCT,
                                   double PVTOBV_DROP_THRESHOLD, double PRICE_DROP_THRESHOLD, double STOP_LOSS_PCT) {

        this.repository = repository;
        this.exchange = exchange;

        this.MIN_CANDLE_LOOK_BEHIND = MIN_CANDLE_LOOK_BEHIND;
        this.PVTOBV_DROP_THRESHOLD = PVTOBV_DROP_THRESHOLD;
        this.PRICE_DROP_THRESHOLD = PRICE_DROP_THRESHOLD;
        this.LIMIT_PRICE_PREMIUM = LIMIT_PRICE_PREMIUM;
        this.STOP_LOSS_PCT = STOP_LOSS_PCT;

        this.SYMBOL = SYMBOL;
        this.QUOTE_CURRENCY = QUOTE_CURRENCY;
        this.EXCHANGE = EXCHANGE;
        this.FEE_RATE = FEE_RATE;
        this.TRADING_ENABLED =  TRADING_ENABLED;
        this.TS_PCT = TS_PCT;
        this.TS_TRIGGER_PCT = TS_TRIGGER_PCT;
    }

    @Override
    public TradeResult trade(double curPrice, long curTimestamp) {

        if(buySignalStrength(curPrice, curTimestamp) > 0) {
            double availableBalance = exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade();
            double buyPremiumPrice = curPrice * (1+LIMIT_PRICE_PREMIUM/100D);
            double amount = availableBalance / buyPremiumPrice;

            try {
                OrderInfo buyOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.BUY, buyPremiumPrice, amount);
                if(!this.TRADING_ENABLED) {
                    LOGGER.info("-------------------TRADING DISABLED!-------------------");
                    LOGGER.info("[PREPARED BUYORDER] {}", buyOrder.toString());
                    return null;
                }
                OrderInfo placedBuyOrder = exchange.placeOrder(buyOrder);
                this.stopLossPrice = placedBuyOrder.getPriceExecuted() * (1-(STOP_LOSS_PCT/100));
                this.placedBuyOrder = placedBuyOrder;
                LOGGER.info("[PLACED BUY ORDER] {}", placedBuyOrder.toString());
                LOGGERBUYSELL.info("[PLACED BUY ORDER] {}", placedBuyOrder.toString());
            } catch(Exception e) {
                LOGGER.error("Placing buy order failed", e);
            } finally {
                return null;
            }
        }

        if(hitStopLoss(curPrice)) {
            double amount = this.placedBuyOrder.getAmountExecuted();
            double price = this.stopLossPrice*(1-LIMIT_PRICE_PREMIUM/100D);
            OrderInfo stopLossOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, price, amount);
            try {
                OrderInfo placedStopLossOrder = exchange.placeOrder(stopLossOrder);
                TradeResult tradeResult = new TradeResult(EXCHANGE, SYMBOL, QUOTE_CURRENCY, 0,0,0,0,0,0);
                initEngineState();
                LOGGER.info("[STOP LOSS HIT] {}", placedStopLossOrder.toString());
                LOGGERBUYSELL.info("[STOP LOSS HIT] {}", placedStopLossOrder.toString());
                return tradeResult;
            } catch(Exception e) {
                LOGGER.error("Placing stopLossOrder failed", e);
                this.prevPrice = curPrice;
                return null;
            }
        }

        updateTrailingStopPrice(curPrice);

        if(sellSignal(curPrice, curTimestamp)) {
            double sellPremiumPrice = curPrice * (1-LIMIT_PRICE_PREMIUM/100D);
            OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, sellPremiumPrice, placedBuyOrder.getAmountExecuted());
            try {
                OrderInfo placedSellOrder = exchange.placeOrder(sellOrder);
                TradeResult tradeResult = new TradeResult(EXCHANGE, SYMBOL, QUOTE_CURRENCY, 0,0,0,0,0,0);
                initEngineState();
                LOGGER.info("[PLACED SELL ORDER] {}", placedSellOrder.toString());
                LOGGERBUYSELL.info("[PLACED SELL ORDER] {}", placedSellOrder.toString());
                return tradeResult;
            } catch (Exception e) {
                LOGGER.error("Placing sell order failed", e);
                this.prevPrice = curPrice;
                return null;
            }
        }

        this.prevPrice = curPrice;
        return null;
    }

    private void updateTrailingStopPrice(double curPrice) {
        if(this.trailingStopPrice == 0) {
            if(curPrice > placedBuyOrder.getPriceExecuted() * (100.0 + TS_TRIGGER_PCT)/100) {
                LOGGER.info(String.format("[LONG] prevPrice=%.2f, curPrice=%.2f, old TS=%.2f, new TS=%.2f", prevPrice, curPrice, this.trailingStopPrice, curPrice * (100.0 - TS_PCT)/100.0));
                this.trailingStopPrice = curPrice * (100.0 - TS_PCT)/100.0;
            }
        } else {
            if(curPrice > prevPrice) {
                if(this.trailingStopPrice < curPrice * (100.0 - TS_PCT)/100.0) {
                    LOGGER.info(String.format("[LONG] prevPrice=%.2f, curPrice=%.2f, old TS=%.2f, new TS=%.2f", prevPrice, curPrice, this.trailingStopPrice, curPrice * (100.0 - TS_PCT)/100.0));
                    this.trailingStopPrice = curPrice * (100.0 - TS_PCT)/100.0;
                }
            }
        }
    }

    private void initEngineState() {
        this.placedBuyOrder = null;
        this.stopLossPrice = 0;
        this.trailingStopPrice = 0;
        this.prevPrice = Double.MAX_VALUE;
    }

    private boolean hitStopLoss(double curPrice) {
        return this.placedBuyOrder != null && curPrice < this.stopLossPrice;
    }

    @Override
    public boolean sellSignal(double curPrice, long curTimestamp) {
        return this.placedBuyOrder != null && this.trailingStopPrice > 0 && curPrice < this.trailingStopPrice;
    }

    @Override
    public void onTradeEvent(TradeEvent e) {

    }

    @Override
    public double getTrailingStopPrice() {
        return 0;
    }

    @Override
    public double getStopLossPrice() {
        return 0;
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
            pastNCandles.add(repository.getCurrentCandle(timestamp));
        }

        Candle latestCandle = pastNCandles.get(0);
        Candle oldestCandle = pastNCandles.get(pastNCandles.size()-1);

        boolean buy = (latestCandle.getPvt() - oldestCandle.getPvt()) < PVTOBV_DROP_THRESHOLD &&
                (latestCandle.getObv() - oldestCandle.getObv()) < PVTOBV_DROP_THRESHOLD &&
                (curPrice - oldestCandle.getClosePrice()) < PRICE_DROP_THRESHOLD;

        return buy ? 1 : 0;
    }
}
