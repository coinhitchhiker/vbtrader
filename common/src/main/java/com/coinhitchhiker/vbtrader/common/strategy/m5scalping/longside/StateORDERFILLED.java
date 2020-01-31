package com.coinhitchhiker.vbtrader.common.strategy.m5scalping.longside;


import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.model.event.TradeResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

public class StateORDERFILLED implements State {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateORDERFILLED.class);

    private final M5ScalpingLongEngine engine;
    private final ApplicationEventPublisher publisher;
    private final OrderInfo filledBuyOrder;
    private final double stopLossPrice;
    private final double tp1Price;
    private final double tp2Price;
    private final double FEE_RATE = 0.045;

    public StateORDERFILLED(M5ScalpingLongEngine engine, ApplicationEventPublisher publisher, OrderInfo filledBuyOrder, double stopLossPrice, double tp1Price, double tp2Price) {
        this.engine = engine;
        this.publisher = publisher;
        this.filledBuyOrder = filledBuyOrder;
        this.stopLossPrice = stopLossPrice;
        this.tp1Price = tp1Price;
        this.tp2Price = tp2Price;
    }

    @Override
    public State nextState(double curPrice, long curTimestamp, double curVol) {
        Exchange exchange = engine.getExchange();
        ExchangeEnum exchangeEnum = exchange.getExchangeEnum();
        String symbol = engine.getSymbol();

        if(stopLossHit(curPrice)) {
            OrderInfo stopLossOrder = exchange.placeOrder(new OrderInfo(exchangeEnum, symbol, OrderSide.SELL, OrderType.MARKET, 0, filledBuyOrder.getAmountExecuted()));
            LOGGER.info("[STOP LOSS HIT]");
            LOGGER.info("[STOP LOSS HIT] {}", stopLossOrder);
            LOGGER.info("[STOP LOSS HIT]");

            double profit = (stopLossOrder.getPriceExecuted() - filledBuyOrder.getPriceExecuted()) * filledBuyOrder.getAmountExecuted();
            double fee = stopLossOrder.getPriceExecuted() * filledBuyOrder.getAmountExecuted() * (FEE_RATE/100) +
                        filledBuyOrder.getPriceExecuted() * filledBuyOrder.getAmountExecuted() * (FEE_RATE/100);

            TradeResultEvent e = new TradeResultEvent(exchangeEnum, symbol, null, profit-fee, profit, stopLossOrder.getPriceExecuted(), filledBuyOrder.getPriceExecuted(), filledBuyOrder.getAmountExecuted(), fee, curTimestamp);
            publisher.publishEvent(e);
            return new StateINIT(engine, publisher);
        }

        if(tp1Hit(curPrice)) {
            OrderInfo tp1Order = exchange.placeOrder(new OrderInfo(exchangeEnum, symbol, OrderSide.SELL, OrderType.MARKET, 0, filledBuyOrder.getAmountExecuted()/2));
            LOGGER.info("[TP1 ORDER]");
            LOGGER.info("[TP1 ORDER] {}", tp1Order);
            LOGGER.info("[TP1 ORDER]");
            double remainingAmount = filledBuyOrder.getAmountExecuted() - tp1Order.getAmountExecuted();
            double breakEvenPrice = filledBuyOrder.getPriceExecuted();
            double profit = (tp1Order.getPriceExecuted() - filledBuyOrder.getPriceExecuted()) * (filledBuyOrder.getAmountExecuted()/2);
            double fee = tp1Order.getPriceExecuted() * filledBuyOrder.getAmountExecuted()/2 * (FEE_RATE/100) +
                    filledBuyOrder.getPriceExecuted() * filledBuyOrder.getAmountExecuted()/2 * (FEE_RATE/100);

            TradeResultEvent e = new TradeResultEvent(exchangeEnum, symbol, null, profit-fee, profit, tp1Order.getPriceExecuted(), filledBuyOrder.getPriceExecuted(), filledBuyOrder.getAmountExecuted()/2, fee, curTimestamp);
            publisher.publishEvent(e);
            return new StateTP1REALIZED(engine, publisher, remainingAmount, breakEvenPrice);
        }

        return this;
    }

    private boolean tp1Hit(double curPrice) {
        return curPrice >= tp1Price;
    }

    private boolean stopLossHit(double curPrice) {
        return curPrice <= stopLossPrice;
    }

    @Override
    public StateEnum getStateEnum() {
        return StateEnum.ORDERFILLED;
    }
}
