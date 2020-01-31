package com.coinhitchhiker.vbtrader.common.strategy.m5scalping.longside;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.model.event.TradeResultEvent;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import static org.joda.time.DateTimeZone.UTC;

public class StateTP1REALIZED implements State {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateTP1REALIZED.class);

    private final M5ScalpingLongEngine engine;
    private final ApplicationEventPublisher publisher;
    private final double remainigAmount;

    private final double FEE_RATE = 0.045;
    private final double THRESHOLD_PRICE = 3;   // 3 dollar?
    private final double breakEvenPrice;
    private double trailingStopPrice;

    public StateTP1REALIZED(M5ScalpingLongEngine engine, ApplicationEventPublisher publisher, double remainingAmount, double breakEvenPrice) {
        this.engine = engine;
        this.publisher = publisher;
        this.remainigAmount = remainingAmount;
        this.breakEvenPrice = breakEvenPrice;
        this.trailingStopPrice = breakEvenPrice;
    }

    @Override
    public State nextState(double curPrice, long curTimestamp, double curVol) {

        Exchange exchange = engine.getExchange();
        ExchangeEnum exchangeEnum = exchange.getExchangeEnum();
        String symbol = engine.getSymbol();

        if(curPrice <= breakEvenPrice) {
            OrderInfo breakEvenOrder = exchange.placeOrder(new OrderInfo(exchangeEnum, symbol, OrderSide.SELL, OrderType.MARKET, 0, remainigAmount));
            LOGGER.info("[BREAK EVEN HIT]");
            LOGGER.info("[BREAK EVEN HIT] {}", breakEvenOrder);
            LOGGER.info("[BREAK EVEN HIT]");
            double fee = breakEvenOrder.getPriceExecuted() * breakEvenOrder.getAmountExecuted() * (FEE_RATE / 100) * 2;
            TradeResultEvent e = new TradeResultEvent(exchangeEnum, symbol, null, -fee, 0, breakEvenPrice, breakEvenPrice, breakEvenOrder.getAmountExecuted(), fee, curTimestamp);
            publisher.publishEvent(e);
            return new StateINIT(engine, publisher);
        } else if(curPrice <= trailingStopPrice) {
            OrderInfo tsOrder = exchange.placeOrder(new OrderInfo(exchangeEnum, symbol, OrderSide.SELL, OrderType.MARKET, 0, remainigAmount));
            LOGGER.info("[TRAILING STOP HIT]");
            LOGGER.info("[TRAILING STOP HIT] {}", tsOrder);
            LOGGER.info("[TRAILING STOP HIT]");
            double profit = (tsOrder.getPriceExecuted() - breakEvenPrice) * remainigAmount;
            double fee = tsOrder.getPriceExecuted() * tsOrder.getAmountExecuted() * (FEE_RATE / 100) * 2;
            TradeResultEvent e = new TradeResultEvent(exchangeEnum, symbol, null, profit-fee, profit, tsOrder.getPriceExecuted(), breakEvenPrice, remainigAmount, fee, curTimestamp);
            publisher.publishEvent(e);
            return new StateINIT(engine, publisher);
        } else {
            Chart m5chart = engine.getM5Chart();
            Candle prev1 = m5chart.getCandleReverse(1);
            Candle prev2 = m5chart.getCandleReverse(2);
            Candle prev3 = m5chart.getCandleReverse(3);

            double newTrailingStopPrice = least(prev1.getOpenPrice(), prev1.getClosePrice());
            if(newTrailingStopPrice > least(prev2.getOpenPrice(), prev2.getClosePrice())) newTrailingStopPrice = least(prev2.getOpenPrice(), prev2.getClosePrice());
            if(newTrailingStopPrice > least(prev3.getOpenPrice(), prev3.getClosePrice())) newTrailingStopPrice = least(prev3.getOpenPrice(), prev3.getClosePrice());

            if(trailingStopPrice < newTrailingStopPrice -  THRESHOLD_PRICE) {
                LOGGER.info("[NEW TRAILING STOP PRICE] curTimestamp {} {}->{}", new DateTime(curTimestamp, UTC), breakEvenPrice, newTrailingStopPrice - THRESHOLD_PRICE);
                trailingStopPrice = newTrailingStopPrice - THRESHOLD_PRICE;
            }

        }

//        if(curPrice >= tp2Price) {
//            OrderInfo tp2Order = exchange.placeOrder(new OrderInfo(exchangeEnum, symbol, OrderSide.SELL, OrderType.MARKET, 0, remainigAmount));
//            LOGGER.info("[TP2 HIT] {}", tp2Order);
//            double profit = (tp2Order.getPriceExecuted() - breakEvenPrice) * remainigAmount;
//            double fee = tp2Order.getPriceExecuted() * remainigAmount * (FEE_RATE/100) +
//                    breakEvenPrice * remainigAmount * (FEE_RATE/100);
//            TradeResultEvent e = new TradeResultEvent(exchangeEnum, symbol, null, profit-fee, profit, tp2Order.getPriceExecuted(), breakEvenPrice, remainigAmount, fee, curTimestamp);
//            publisher.publishEvent(e);
//            return new StateINIT(engine, publisher);
//        }

        return this;
    }

    private double least(double d1, double d2) {
        return d1 > d2 ? d1 : d2;
    }

    @Override
    public StateEnum getStateEnum() {
        return StateEnum.TP1REALIZED;
    }
}
