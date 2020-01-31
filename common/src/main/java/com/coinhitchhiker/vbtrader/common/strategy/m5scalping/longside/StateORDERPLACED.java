package com.coinhitchhiker.vbtrader.common.strategy.m5scalping.longside;

import com.coinhitchhiker.vbtrader.common.indicator.EMA;
import com.coinhitchhiker.vbtrader.common.model.*;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.List;

import static org.joda.time.DateTimeZone.UTC;

public class StateORDERPLACED implements State {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateORDERPLACED.class);

    private final Candle triggerM5Bar;
    private final List<Candle> prev5Candles;
    private final double THRESHOLD_PRICE = 3;       // 5 dollars?
    private final double ORDER_AMT = 0.1;
    private final M5ScalpingLongEngine engine;
    private final ApplicationEventPublisher publisher;

    public static final String M5_21EMA = "m5_21ema";
    public static final String M5_13EMA = "m5_13ema";
    public static final String M5_8EMA = "m5_8ema";

    private double stopLossPrice;
    private double tp1Price;
    private double tp2Price;
    private OrderInfo placedLimitBuy;

    public StateORDERPLACED(M5ScalpingLongEngine engine, ApplicationEventPublisher publisher) {
        Chart m5chart = engine.getM5Chart();

        this.engine = engine;
        this.publisher = publisher;
        this.placedLimitBuy = placedLimitBuy;
        this.triggerM5Bar = m5chart.getCandleReverse(1);
        this.prev5Candles = Arrays.asList(m5chart.getCandleReverse(2),
                        m5chart.getCandleReverse(3),
                        m5chart.getCandleReverse(4),
                        m5chart.getCandleReverse(5),
                        m5chart.getCandleReverse(6));
    }

    @Override
    public State nextState(double curPrice, long curTimestamp, double curVol) {
        if(placedLimitBuy == null) {
            // order failed in enter() for some reason. go back to StatINIT
            return new StateINIT(engine, publisher);
        } else {
            OrderInfo queriedOrder = this.engine.getExchange().getOrder(this.placedLimitBuy);
            if(queriedOrder.getOrderStatus().equals(OrderStatus.COMPLETE)) {
                return new StateORDERFILLED(this.engine, publisher, queriedOrder, stopLossPrice, tp1Price, tp2Price);
            } else {
                if(!checkM5condition()) {
                    // cancel pending order
                    OrderInfo cancelledOrder = engine.getExchange().cancelOrder(placedLimitBuy);
                    LOGGER.info("[CANCELLED ORDER]");
                    LOGGER.info("[CANCELLED ORDER] {}", cancelledOrder);
                    LOGGER.info("[CANCELLED ORDER]");
                    return new StateINIT(engine, publisher);
                }
                return this;
            }
        }
    }

    private boolean checkM5condition() {

        Chart m5chart = this.engine.getM5Chart();
        EMA ema21 = (EMA)m5chart.getIndicatorByName(M5_21EMA);
        double m5close = m5chart.getCandleReverse(1).getClosePrice();
        double ema = ema21.getValueReverse(1);

        if(m5close < ema) {
            LOGGER.info("[M5 TRADE SETUP INVALIDATED]");
            LOGGER.info("[M5 TRADE SETUP INVALIDATED] m5 close {} < 21ema {} curTimestamp {}", m5close, ema, new DateTime(m5chart.getCandleReverse(0).getOpenTime(), UTC));
            LOGGER.info("[M5 TRADE SETUP INVALIDATED]");
            return false;
        }

        return true;
    }

    public void enter(Exchange exchange, String symbol) {
        placeOrder();
    }

    void placeOrder() {
        Exchange exchange = this.engine.getExchange();
        String symbol = this.engine.getSymbol();

        double highestPrice = 0;
        for(Candle candle : prev5Candles) {
            if(highestPrice < candle.getHighPrice()) highestPrice = candle.getHighPrice();
        }

//        double limitBuyPrice = highestPrice + THRESHOLD_PRICE;
        double limitBuyPrice = triggerM5Bar.getHighPrice();
        double stopLossPrice = triggerM5Bar.getLowPrice() - THRESHOLD_PRICE;
        double tp1Price = limitBuyPrice + (limitBuyPrice - stopLossPrice);
        double tp2Price = tp1Price + (limitBuyPrice - stopLossPrice);

        OrderInfo limitBuy = new OrderInfo(exchange.getExchangeEnum(),symbol, OrderSide.BUY, OrderType.STOP_LIMIT, limitBuyPrice, ORDER_AMT);
        try {
            OrderInfo placedLimitBuy = exchange.placeOrder(limitBuy);
            LOGGER.info("[PLACED STOP LIMIT BUY]");
            LOGGER.info("[PLACED STOP LIMIT BUY] {}", placedLimitBuy);
            LOGGER.info("[PLACED STOP LIMIT BUY]");
            this.stopLossPrice = stopLossPrice;
            this.tp1Price = tp1Price;
            this.tp2Price = tp2Price;
            this.placedLimitBuy = placedLimitBuy;
        } catch(Exception e) {
            LOGGER.error("[ORDER ERROR] ", e);
        }
    }

    @Override
    public StateEnum getStateEnum() {
        return StateEnum.ORDERPLACED;
    }
}
