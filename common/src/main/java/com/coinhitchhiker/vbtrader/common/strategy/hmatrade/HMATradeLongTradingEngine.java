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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.joda.time.DateTimeZone.UTC;

public class HMATradeLongTradingEngine extends AbstractTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(HMATradeLongTradingEngine.class);
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private Chart chart = null;
    private final TimeFrame timeFrame;
    private final int HMA_LENGTH;
    private final int TRADING_WINDOW_LOOKBEHIND;
    private final String HMA_INDI_NAME = "hma9";
    private final double ORDER_AMT = 0.1;
    private final int MAX_ORDER_CNT = 20;
    private Map<String, OrderInfo> placedOrders = new LinkedHashMap<>();

    public HMATradeLongTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                                     String SYMBOL, String QUOTE_CURRENCY, ExchangeEnum EXCHANGE, double FEE_RATE,
                                     boolean VERBOSE, TimeFrame timeFrame, int HMA_LENGTH, int TRADING_WINDOW_LOOKBEHIND,
                                     boolean SIMUL, long SIMUL_START
                                     ) {

        super(repository, exchange, orderBookCache, TradingMode.LONG, SYMBOL, QUOTE_CURRENCY, 0, EXCHANGE, FEE_RATE,
                true, true, 0.1 + FEE_RATE*2, 0.2 * (0.1 + FEE_RATE*2), false, 0, VERBOSE);

        this.timeFrame = timeFrame;
        this.HMA_LENGTH = HMA_LENGTH;
        this.TRADING_WINDOW_LOOKBEHIND = TRADING_WINDOW_LOOKBEHIND;

        if(SIMUL) {
            initChart(SIMUL_START);
        } else {
            initChart(DateTime.now(UTC).getMillis());
        }

    }

    private void initChart(long loadBeforeThis) {
        this.chart = Chart.of(timeFrame, SYMBOL);
        this.chart.addIndicator(new HullMovingAverage(HMA_INDI_NAME, HMA_LENGTH));

        long closestCandleOpen = Util.getClosestCandleOpen(new DateTime(loadBeforeThis, UTC), timeFrame).getMillis();
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

    @Override
    public TradeResult trade(double curPrice, long curTimestamp, double curVol) {
        syncPlacedOrderStatus();
//        Close order

        if(trailingStopHit(curPrice)) {
            double prevTSPrice = super.trailingStopPrice;
            LOGGERBUYSELL.info("trailingStopPrice {} > curPrice {}", prevTSPrice, curPrice);
            LOGGERBUYSELL.info("---------------LONG TRAILING STOP HIT------------------------");
            this.closeAllOrders();
            return null;
        }

        return null;
    }

    private void syncPlacedOrderStatus() {
        for(Map.Entry<String, OrderInfo> entry : this.placedOrders.entrySet()) {
            OrderInfo placedOrder = entry.getValue();
            if(placedOrder.getOrderStatus().equals(OrderStatus.COMPLETE)) continue;

            OrderInfo syncOrder = this.exchange.getOrder(placedOrder);
            placedOrder.setOrderStatus(syncOrder.getOrderStatus());
            placedOrder.setAmountExecuted(syncOrder.getAmountExecuted());
            placedOrder.setPriceExecuted(syncOrder.getPriceExecuted());
            placedOrder.setExecTimestamp(syncOrder.getExecTimestamp());

            if(this.placedBuyOrder.getExternalOrderId().equals(placedOrder.getExternalOrderId())) {
                this.placedBuyOrder = syncOrder;
            }
        }
    }

    private void closeAllOrders() {
        List<String> removedKeys = new ArrayList<>();

        for (Map.Entry<String, OrderInfo> entry : this.placedOrders.entrySet()) {
            OrderInfo placedBuyOrder = this.exchange.getOrder(entry.getValue());
            if (placedBuyOrder.getOrderStatus().equals(OrderStatus.COMPLETE)) {
                OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, OrderType.MARKET, 0, ORDER_AMT);
                OrderInfo placedSellOrder = this.exchange.placeOrder(sellOrder);
                double profit = (placedSellOrder.getPriceExecuted() - placedBuyOrder.getPriceExecuted()) * ORDER_AMT;
                double fee = placedSellOrder.getPriceExecuted() * ORDER_AMT * FEE_RATE / 100 + placedBuyOrder.getPriceExecuted() * ORDER_AMT * FEE_RATE / 100;
                TradeResultEvent event = new TradeResultEvent(EXCHANGE, SYMBOL, QUOTE_CURRENCY, profit - fee, profit, placedSellOrder.getPriceExecuted(), placedBuyOrder.getPriceExecuted(), ORDER_AMT, fee, placedSellOrder.getExecTimestamp());
                LOGGERBUYSELL.info("[{}] sell price {} buy price {} order amt {} net profit {}", new DateTime(placedSellOrder.getExecTimestamp(), UTC), placedSellOrder.getPriceExecuted(), placedBuyOrder.getPriceExecuted(), ORDER_AMT, profit - fee);
                this.eventPublisher.publishEvent(event);
            } else if (placedBuyOrder.getOrderStatus().equals(OrderStatus.PENDING)) {
                this.exchange.cancelOrder(placedBuyOrder);
                LOGGERBUYSELL.info("[{}] [CANCEL PENDING ORDER] {}", new DateTime(placedBuyOrder.getExecTimestamp(), UTC), placedBuyOrder.toString());
            } else if(placedBuyOrder.getOrderStatus().equals(OrderStatus.PARTIALLY_FILLED)) {
                double amountExecuted = placedBuyOrder.getAmountExecuted();
                OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, OrderType.MARKET, 0, amountExecuted);
                OrderInfo placedSellOrder = this.exchange.placeOrder(sellOrder);
                double profit = (placedSellOrder.getPriceExecuted() - placedBuyOrder.getPriceExecuted()) * amountExecuted;
                double fee = placedSellOrder.getPriceExecuted() * amountExecuted * FEE_RATE / 100 + placedBuyOrder.getPriceExecuted() * amountExecuted * FEE_RATE / 100;
                TradeResultEvent event = new TradeResultEvent(EXCHANGE, SYMBOL, QUOTE_CURRENCY, profit - fee, profit, placedSellOrder.getPriceExecuted(), placedBuyOrder.getPriceExecuted(), amountExecuted, fee, placedSellOrder.getExecTimestamp());
                LOGGERBUYSELL.info("[{}] sell price {} buy price {} order amt {} net profit {}", new DateTime(placedSellOrder.getExecTimestamp(), UTC), placedSellOrder.getPriceExecuted(), placedBuyOrder.getPriceExecuted(), amountExecuted, profit - fee);
                this.exchange.cancelOrder(placedBuyOrder);
                this.eventPublisher.publishEvent(event);
            }
            removedKeys.add(entry.getKey());
        }

        removedKeys.forEach(key -> this.placedOrders.remove(key));

        this.clearOutOrders();
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
            this.updateTrailingStopPrice(e.getPrice(), e.getTradeTime());
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

        HullMovingAverage hma = (HullMovingAverage)this.chart.getIndicatorByName(HMA_INDI_NAME);
        double hma0 = hma.getValueReverse(1);
        double hma1 = hma.getValueReverse(2);
        double hma2 = hma.getValueReverse(3);
        long timestamp = e.getNewCandle().getOpenTime();


        boolean hmaGoingUp = hma1 < hma0;
//        Open order
//        ABS( (HMA0 - HMA1) * 5 ) / HMA1 > (기대수익 0.1 + 수수료x2 ) 일 경우 다음 연장된 HMA값인 HMA0+(HMA0 - HMA1) 가격으로 주문,
        // TODO: 5배가 아니라 최적값을 찾아야 함
        if(hmaGoingUp) {
            if (Math.abs(hma0 - hma1) * 5 / hma1 * 100 > 0.1 + FEE_RATE * 2) {

                if(this.placedOrders.size() >= MAX_ORDER_CNT) {
                    LOGGER.info("Max orders {} reached", MAX_ORDER_CNT);
                    return;
                }

                double limitBuyPrice = hma0 + (hma0 - hma1);
                OrderInfo buyOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.BUY, OrderType.LIMIT, limitBuyPrice, ORDER_AMT);
                OrderInfo placedBuyOrder = this.exchange.placeOrder(buyOrder);
                this.placedOrders.put(String.valueOf(timestamp), placedBuyOrder);
                this.trailingStopPrice = 0;     // init trailing stop price
                this.placedBuyOrder = placedBuyOrder;       // set last buyOrder to track trailing stop
                LOGGERBUYSELL.info("[{}] [PLACED BUYORDER] {}", new DateTime(timestamp, UTC), placedBuyOrder.toString());
                return;
            }
        }

        // TODO: 0.2를 적정값 찾기.... ㅠㅠㅠㅠ
        // 직전 두개 HMA 상승폭이 하나 더 전의 HMA상승폭의 20%보다 작으면 기존 주문들 모두 청산 (하락 또는 상승폭 급격 둔화)
        if(hma0 - hma1 < 0.2 * (hma1 - hma2) && this.placedOrders.size() > 0) {
            LOGGERBUYSELL.info("[{}] [CLOSING ORDERS] hma0 - hma1 {} < 0.2 * (hma1 - hma2) {} ", new DateTime(timestamp, UTC), hma0-hma1, 0.2*(hma1-hma2));
            this.closeAllOrders();
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
