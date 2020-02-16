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
import org.springframework.beans.factory.annotation.Value;
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

    //external config params

    @Value("${hma.trading.engine.order.amount}") double ORDER_AMT;
    @Value("${hma.trading.engine.order.amount.increment.multiplying}") double ORDER_AMT_INC_MULTI;
    @Value("${hma.trading.engine.expected.profit}") double EXPECTED_PROFIT;
    @Value("${hma.trading.engine.scale.trade.order.interval}") double SCALE_TRD_ORD_INRERVAL;
    @Value("${hma.trading.engine.scale.trade.order.amount.increment.interval}") double SCALE_TRD_ORD_AMT_INC_INTERVAL;
    @Value("${hma.trading.engine.max.order.limit}") double MAX_ORDER_LMT;

    private Map<String, OrderInfo> placedOrders = new LinkedHashMap<>();
    private int longTriggerPointCnt = 0;
    private int orderScaleTradeCnt = 0;
    private double orderAmtIncRateApplied = 0;

    public HMATradeLongTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                                     String SYMBOL, String QUOTE_CURRENCY, ExchangeEnum EXCHANGE, double FEE_RATE,
                                     boolean VERBOSE, TimeFrame timeFrame, int HMA_LENGTH, int TRADING_WINDOW_LOOKBEHIND,
                                     boolean SIMUL, long SIMUL_START
                                     ) {

        super(repository, exchange, orderBookCache, TradingMode.LONG, SYMBOL, QUOTE_CURRENCY, 0, EXCHANGE, FEE_RATE,
                true, false, 3 + FEE_RATE*2, 0.4 * (0.1 + FEE_RATE*2), false, 0, VERBOSE);

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

        double expectedProfit = 0D;
        double profitGained = 0D;

        //calculate profit
        for (Map.Entry<String, OrderInfo> entry : this.placedOrders.entrySet()) {

            OrderInfo placedBuyOrder = this.exchange.getOrder(entry.getValue());
            if (placedBuyOrder.getOrderStatus().equals(OrderStatus.COMPLETE)) {

                OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, OrderType.MARKET, 0, placedBuyOrder.getAmount());
                OrderInfo placedSellOrder = this.exchange.placeOrder(sellOrder);

                profitGained = profitGained + (placedSellOrder.getPriceExecuted() - placedBuyOrder.getPriceExecuted()) * placedBuyOrder.getAmount();
                double fee = placedSellOrder.getPriceExecuted() * placedSellOrder.getAmount() * FEE_RATE / 100 + placedBuyOrder.getPriceExecuted() * placedBuyOrder.getAmount() * FEE_RATE / 100;

                profitGained = profitGained - fee;

                expectedProfit = expectedProfit + placedBuyOrder.getAmount() * placedBuyOrder.getPriceExecuted() * EXPECTED_PROFIT / 100;
            }

        }

        if(profitGained == 0){
            return;
        }

        if(profitGained < expectedProfit ){
            return;
        }

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
            }
            removedKeys.add(entry.getKey());
        }

        removedKeys.forEach(key -> this.placedOrders.remove(key));

        //reset vars
        this.longTriggerPointCnt = 0;
        this.orderScaleTradeCnt  = 0;
        this.orderAmtIncRateApplied = 0;

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
            if(TRAILING_STOP_ENABLED) {
                this.updateTrailingStopPrice(e.getPrice(), e.getTradeTime());
            }

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

        boolean hamLongConditionConfirmed = hma0 - hma1 > 0 && hma1 - hma2 < 0 ? true : false;
        boolean hamShortConditionConfirmed = hma0 - hma1 < 0 && hma1 - hma2 > 0  ? true : false;

//        double candleOpenPrice = this.chart.getValueReverse(1).getOpenPrice();
//        double candleClosePrice = this.chart.getValueReverse(1).getClosePrice();
//        double candleHighPrice = this.chart.getValueReverse(1).getHighPrice();
//        double candleLowPrice = this.chart.getValueReverse(1).getLowPrice();

        if(hamLongConditionConfirmed){
            this.longTriggerPointCnt++;
        }

//        Open order
        if(hamLongConditionConfirmed && (this.longTriggerPointCnt == 1 || (this.longTriggerPointCnt - 1) % this.SCALE_TRD_ORD_INRERVAL == 0)) {

            if(this.placedOrders.size() >= MAX_ORDER_LMT) {
                LOGGER.info("Max orders {} reached", MAX_ORDER_LMT);
                return;
            }

            if(orderAmtIncRateApplied == 0 || this.SCALE_TRD_ORD_AMT_INC_INTERVAL == 0){
                orderAmtIncRateApplied = ORDER_AMT;
            }else{

                if(this.orderScaleTradeCnt > 0 && (this.orderScaleTradeCnt % this.SCALE_TRD_ORD_AMT_INC_INTERVAL == 0)){
                    orderAmtIncRateApplied = orderAmtIncRateApplied*ORDER_AMT_INC_MULTI;
                }

            }

            double limitBuyPrice = this.chart.getValueReverse(1).getClosePrice();
            OrderInfo buyOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.BUY, OrderType.MARKET, limitBuyPrice, orderAmtIncRateApplied);
            OrderInfo placedBuyOrder = this.exchange.placeOrder(buyOrder);

            this.orderScaleTradeCnt ++;

            this.placedOrders.put(String.valueOf(timestamp), placedBuyOrder);
            this.trailingStopPrice = 0;     // init trailing stop price
            this.placedBuyOrder = placedBuyOrder;       // set last buyOrder to track trailing stop
            LOGGERBUYSELL.info("[{}] [PLACED BUYORDER] {}", new DateTime(timestamp, UTC), placedBuyOrder.toString());
            LOGGER.info("---------------------------------------------------------------");
            LOGGER.info("BUY SIGNAL DETECTED AT {}", new DateTime(e.getNewCandle().getOpenTime(), UTC));
            LOGGER.info("---------------------------------------------------------------");
            return;

        }

//close order
        if(hamShortConditionConfirmed && this.placedOrders.size() > 0){
            LOGGER.info("---------------------------------------------------------------");
            LOGGER.info("SELL SIGNAL DETECTED AT {}", new DateTime(e.getNewCandle().getOpenTime(), UTC));
            LOGGER.info("---------------------------------------------------------------");
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
