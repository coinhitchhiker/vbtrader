package com.coinhitchhiker.vbtrader.common.strategy.hmatrade;

import com.coinhitchhiker.vbtrader.common.Util;
import com.coinhitchhiker.vbtrader.common.indicator.HullMovingAverage;
import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.model.event.CandleOpenEvent;
import com.coinhitchhiker.vbtrader.common.model.event.TradeEvent;
import com.coinhitchhiker.vbtrader.common.model.event.TradeResultEvent;
import com.coinhitchhiker.vbtrader.common.strategy.AbstractTradingEngine;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HMATradeLongTradingEngine extends AbstractTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(HMATradeLongTradingEngine.class);
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private Chart chart = null;
    private final TimeFrame timeFrame;
    private final int HMA_LENGTH;
    private final int TRADING_WINDOW_LOOKBEHIND;
    private final String HMA9 = "hma9";
    private final double ORDER_AMT = 0.1;
    private Map<String, OrderInfo> placedOrders = new ConcurrentHashMap<>();

    public HMATradeLongTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                                     String SYMBOL, String QUOTE_CURRENCY, ExchangeEnum EXCHANGE, double FEE_RATE,
                                     boolean VERBOSE, TimeFrame timeFrame, int HMA_LENGTH, int TRADING_WINDOW_LOOKBEHIND,
                                     boolean SIMUL, long SIMUL_START
                                     ) {

        super(repository, exchange, orderBookCache, TradingMode.LONG, SYMBOL, QUOTE_CURRENCY, 0, EXCHANGE, FEE_RATE,
                true, false, 0, 0, false, 0, VERBOSE);

        this.timeFrame = timeFrame;
        this.HMA_LENGTH = HMA_LENGTH;
        this.TRADING_WINDOW_LOOKBEHIND = TRADING_WINDOW_LOOKBEHIND;

        if(SIMUL) {
            initChart(SIMUL_START);
        } else {
            initChart(DateTime.now(DateTimeZone.UTC).getMillis());
        }

    }

    private void initChart(long loadBeforeThis) {
        this.chart = Chart.of(timeFrame, SYMBOL);
        this.chart.addIndicator(new HullMovingAverage(HMA9, HMA_LENGTH));

        long closestCandleOpen = Util.getClosestCandleOpen(new DateTime(loadBeforeThis, DateTimeZone.UTC), timeFrame).getMillis();
        int backToNCandles = HMA_LENGTH * 2 + TRADING_WINDOW_LOOKBEHIND * 2;
        long startTime = closestCandleOpen - timeFrame.toSeconds() * 1000 * backToNCandles;
        List<Candle> pastCandles = repository.getCandles(SYMBOL, startTime, loadBeforeThis);

        for(Candle candle : pastCandles) {
            this.chart.onTick(candle.getOpenPrice(), candle.getOpenTime(), 0);
            this.chart.onTick(candle.getHighPrice(), candle.getOpenTime() + 20 * 1000, 0);
            this.chart.onTick(candle.getLowPrice(), candle.getOpenTime() + 40 * 1000, 0);
            this.chart.onTick(candle.getClosePrice(), candle.getCloseTime(), candle.getVolume());
        }
    }

    // this will be called when there's a new candle open
    @Override
    public TradeResult trade(double curPrice, long curTimestamp, double curVol) {



        return null;
    }

    @Override
    public double buySignalStrength(double curPrice, long curTimestamp) {
        return 0;
    }

    @Override
    public double sellSignalStrength(double curPrice, long curTimestamp) {
        return 0;
    }

    @Override
    @EventListener
    public void onTradeEvent(TradeEvent e) {
        if(this.chart != null) {
            Candle newCandle = this.chart.onTick(e.getPrice(), e.getTradeTime(), e.getAmount());
            if(newCandle != null) {
                this.eventPublisher.publishEvent(new CandleOpenEvent(this.timeFrame, newCandle));
            }
        }
    }

    @EventListener
    public void onCandleOpenEvent(CandleOpenEvent e) {
        TimeFrame timeFrame = e.getTimeFrame();

        if(!timeFrame.equals(this.timeFrame)) {
            return;
        }

        HullMovingAverage hma = (HullMovingAverage)this.chart.getIndicatorByName(HMA9);
        double hma0 = hma.getValueReverse(1);
        double hma1 = hma.getValueReverse(2);

//        Open order
//        ABS( (HMA0 - HMA1) * 5 ) / HMA1 > (기대수익 0.1 + 수수료x2 ) 일 경우 다음 연장된 HMA값인 HMA0+(HMA0 - HMA1) 가격으로 주문, 주문별 abs(HMA0 - HMA1) 값과 트랜드 방향을 별도로 기록함
        if(hma1 < hma0  // HMA UP
                && Math.abs(hma0 - hma1) * 5 / hma1 * 100 > 0.1 + FEE_RATE*2) {
            double limitBuyPrice = hma0 + (hma0 - hma1);
            OrderInfo buyOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.BUY, OrderType.LIMIT_MAKER, limitBuyPrice, ORDER_AMT);
            OrderInfo placedBuyOrder = this.exchange.placeOrder(buyOrder);
            String timestamp = String.valueOf(e.getNewCandle().getOpenTime());
            this.placedOrders.put(timestamp, placedBuyOrder);
        }

//        2. open 된 것중 새로운 HMA값이 완성될때마다
//        새로 완성된 HMA - 이전 HMA 에 대한 트랜드와 반대인 주문들(역방향 형성시 기존 주문들 close)
        boolean hmaGoingDown = hma1 > hma0;
        List<String> removedKeys = new ArrayList<>();
        if(hmaGoingDown) {
            for(Map.Entry<String, OrderInfo> entry : this.placedOrders.entrySet()) {
                OrderInfo placedBuyOrder = this.exchange.getOrder(entry.getValue());
                if(placedBuyOrder.getOrderStatus().equals(OrderStatus.COMPLETE)) {
                    OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, OrderType.MARKET, 0, ORDER_AMT);
                    OrderInfo placedSellOrder = this.exchange.placeOrder(sellOrder);
                    double profit = (placedSellOrder.getPriceExecuted() - placedBuyOrder.getPriceExecuted()) * ORDER_AMT;
                    double fee = placedSellOrder.getPriceExecuted() * ORDER_AMT * FEE_RATE / 100 + placedBuyOrder.getPriceExecuted() * ORDER_AMT * FEE_RATE / 100;
                    TradeResultEvent event = new TradeResultEvent(EXCHANGE, SYMBOL, QUOTE_CURRENCY, profit - fee, profit, placedSellOrder.getPriceExecuted(), placedBuyOrder.getPriceExecuted(), ORDER_AMT, fee);
                    LOGGERBUYSELL.info("sell price {} buy price {} order amt {} profit {}", placedSellOrder.getPriceExecuted() , placedBuyOrder.getPriceExecuted() , ORDER_AMT , profit);
                    removedKeys.add(entry.getKey());
                    this.eventPublisher.publishEvent(event);
                } else if(placedBuyOrder.getOrderStatus().equals(OrderStatus.PENDING)) {
                    this.exchange.cancelOrder(placedBuyOrder);
                    removedKeys.add(entry.getKey());
                }
            }
            removedKeys.forEach(key -> this.placedOrders.remove(key));
        } else {
//        같은 방향 트랜드일 경우,  abs(새로운 완성된 HMA - 이전 HMA)*0.8 값보다 작은 주문들(상승폭이 감소될경우 기존 주문들에 대해서는 수익 실현 또는 손절)
            for(Map.Entry<String, OrderInfo> entry : this.placedOrders.entrySet()) {
                OrderInfo placedBuyOrder = entry.getValue();
                if(!placedBuyOrder.getOrderStatus().equals(OrderStatus.COMPLETE)) continue;
                double curPrice = e.getNewCandle().getOpenPrice();
                if(Math.abs(hma0 - hma1) * 0.8 > curPrice - placedBuyOrder.getPriceExecuted()) {
                    OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, OrderType.MARKET, 0, ORDER_AMT);
                    OrderInfo placedSellOrder = this.exchange.placeOrder(sellOrder);
                    double profit = (placedSellOrder.getPriceExecuted() - placedBuyOrder.getPriceExecuted()) * ORDER_AMT;
                    double fee = placedSellOrder.getPriceExecuted() * ORDER_AMT * FEE_RATE / 100 + placedBuyOrder.getPriceExecuted() * ORDER_AMT * FEE_RATE / 100;
                    TradeResultEvent event = new TradeResultEvent(EXCHANGE, SYMBOL, QUOTE_CURRENCY, profit - fee, profit, placedSellOrder.getPriceExecuted(), placedBuyOrder.getPriceExecuted(), ORDER_AMT, fee);
                    LOGGERBUYSELL.info("sell price {} buy price {} order amt {} profit {}", placedSellOrder.getPriceExecuted() , placedBuyOrder.getPriceExecuted() , ORDER_AMT , profit);
                    removedKeys.add(entry.getKey());
                    this.eventPublisher.publishEvent(event);
                }
            }
            removedKeys.forEach(key -> this.placedOrders.remove(key));
        }

    }

    public StrategyEnum getStrategy() {
        return StrategyEnum.HMA_TRADE;
    }

    public void printStrategyParams() {
        LOGGERBUYSELL.info("timeFrame {}", this.timeFrame);
        LOGGERBUYSELL.info("HMA_LENGTH {}", this.HMA_LENGTH);
        LOGGERBUYSELL.info("TRADING_WINDOW_LOOK_BEHIND {}", this.TRADING_WINDOW_LOOKBEHIND);
    }

}
