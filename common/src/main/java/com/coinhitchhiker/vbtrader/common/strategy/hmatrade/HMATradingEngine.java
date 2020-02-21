package com.coinhitchhiker.vbtrader.common.strategy.hmatrade;

import com.coinhitchhiker.vbtrader.common.Util;
import com.coinhitchhiker.vbtrader.common.indicator.HullMovingAverage;
import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.model.event.CandleOpenEvent;
import com.coinhitchhiker.vbtrader.common.model.event.TradeEvent;
import com.coinhitchhiker.vbtrader.common.model.event.TradeResultEvent;
import com.coinhitchhiker.vbtrader.common.strategy.AbstractTradingEngine;

import org.joda.time.DateTime;
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

import static org.joda.time.DateTimeZone.UTC;

public class HMATradingEngine extends AbstractTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(HMATradingEngine.class);
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");
    private static final Logger LOGEQUITYBALANCE = LoggerFactory.getLogger("EQUITYBALANCELOGGER");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private Chart chart = null;
    private final TimeFrame timeFrame;
    private final int HMA_LENGTH;
    private final int TRADING_WINDOW_LOOKBEHIND;
    private final String HMA_INDI_NAME = "hma9";

    //external config params

    @Value("${hma.trading.engine.order.amount.type}") String ORDER_AMT_TYPE;
    @Value("${hma.trading.engine.order.amount}") double ORDER_AMT;
    @Value("${hma.trading.engine.order.amount.increment.multiplying}") double ORDER_AMT_INC_MULTI;
    @Value("${hma.trading.engine.expected.profit}") double EXPECTED_PROFIT;
    @Value("${hma.trading.engine.scale.trade.order.interval}") double SCALE_TRD_ORD_INRERVAL;
    @Value("${hma.trading.engine.scale.trade.order.martingale.number}") int SCALE_TRD_ORD_MARTINGALE_NUM;
    @Value("${hma.trading.engine.scale.trade.order.amount.increment.interval}") double SCALE_TRD_ORD_AMT_INC_INTERVAL;
    @Value("${hma.trading.engine.max.order.limit}") double MAX_ORDER_LMT;
    @Value("${trading.slippage}") private double SLIPPAGE;
    @Value("${hma.trading.engine.drawdown.allowance}") private double DRAWDOWNALLOWANCE;

    private final TradingMode tradeMode;
    private Map<String, OrderInfo> placedOrders = new LinkedHashMap<>();
    private int triggerTurningPoint = 0;
    private int orderScaleTradeCnt = 0;
    private double orderAmtIncRateApplied = 0;
    private int triggerTurningPointMartigaleNumCnt = 0;
    private int lastMartigaleNumUsed = 0;
    private double lastHMATurningPoint = 0;

    public HMATradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                            String SYMBOL, String QUOTE_CURRENCY, ExchangeEnum EXCHANGE, double FEE_RATE,
                            boolean VERBOSE, TimeFrame timeFrame, int HMA_LENGTH, int TRADING_WINDOW_LOOKBEHIND,
                            boolean SIMUL, long SIMUL_START, TradingMode TRADE_MODE
                                     ) {

        super(repository, exchange, orderBookCache, TRADE_MODE, SYMBOL, QUOTE_CURRENCY, 0, EXCHANGE, FEE_RATE,
                true, false, 3 + FEE_RATE*2, 0.4 * (0.1 + FEE_RATE*2), false, 0, VERBOSE);

        this.tradeMode = TRADE_MODE;
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

            if(placedOrder.getOrderSide().equals(OrderSide.BUY)) {
                if (this.placedBuyOrder.getExternalOrderId().equals(placedOrder.getExternalOrderId())) {
                    this.placedBuyOrder = syncOrder;
                }
            }else{
                if (this.placedSellOrder.getExternalOrderId().equals(placedOrder.getExternalOrderId())) {
                    this.placedSellOrder = syncOrder;
                }
            }
        }
    }

    private boolean isRiskDrawDown(){

        double orderAmountTotal = 0D;
        double totalCost = 0D;

        for (Map.Entry<String, OrderInfo> entry : this.placedOrders.entrySet()) {

            OrderInfo placedOrder = this.exchange.getOrder(entry.getValue());
            if (placedOrder.getOrderStatus().equals(OrderStatus.COMPLETE)) {
                orderAmountTotal = orderAmountTotal + placedOrder.getAmount();
                totalCost = totalCost + placedOrder.getAmount()*placedOrder.getPriceExecuted();
            }

        }

        double lastBalance = this.exchange.getBalance().get("USDT").getAvailableForTrade();
        double currentDrawdown = tradeMode.equals(TradingMode.LONG) ? totalCost - orderAmountTotal*this.chart.getValueReverse(0).getOpenPrice() : orderAmountTotal*this.chart.getValueReverse(0).getOpenPrice() - totalCost;
        double equity = this.exchange.getBalance().get("USDT").getAvailableForTrade() - (currentDrawdown);

        LOGEQUITYBALANCE.info("{}\t{}\t{}", new DateTime(this.chart.getValueReverse(0).getOpenTime(), UTC), lastBalance, equity );

        boolean drawDownRisk = (100 - equity/lastBalance*100) > DRAWDOWNALLOWANCE;

        return drawDownRisk;

    }

    private void closeAllOrders() {

        List<String> removedKeys = new ArrayList<>();

        double expectedProfit = 0D;
        double profitGained = 0D;

        //calculate profit
        for (Map.Entry<String, OrderInfo> entry : this.placedOrders.entrySet()) {

            OrderInfo placedOrder = this.exchange.getOrder(entry.getValue());
            if (placedOrder.getOrderStatus().equals(OrderStatus.COMPLETE)) {

                double closingOrderPrice = this.chart.getValueReverse(0).getOpenPrice();
                closingOrderPrice = tradeMode.equals(TradingMode.LONG) ? closingOrderPrice*(1-SLIPPAGE) : closingOrderPrice*(1+SLIPPAGE);
                double tradeRawProfit = tradeMode.equals(TradingMode.LONG) ? closingOrderPrice - placedOrder.getPriceExecuted(): placedOrder.getPriceExecuted() - closingOrderPrice;
                profitGained = profitGained + (tradeRawProfit) * placedOrder.getAmount();
                double fee = closingOrderPrice * placedOrder.getAmount() * FEE_RATE / 100 + placedOrder.getPriceExecuted() * placedOrder.getAmount() * FEE_RATE / 100;

                profitGained = profitGained - fee;
                expectedProfit = expectedProfit + placedOrder.getAmount() * placedOrder.getPriceExecuted() * EXPECTED_PROFIT / 100;
            }

        }

        if(profitGained == 0){
            return;
        }

        if(profitGained < expectedProfit ){
            return;
        }

        for (Map.Entry<String, OrderInfo> entry : this.placedOrders.entrySet()) {
            OrderInfo placedOrder = this.exchange.getOrder(entry.getValue());
            if (placedOrder.getOrderStatus().equals(OrderStatus.COMPLETE)) {

                OrderSide orderSide = tradeMode.equals(TradingMode.LONG) ? OrderSide.SELL : OrderSide.BUY;
                double currentOpenPrice = this.chart.getValueReverse(0).getOpenPrice();
                OrderInfo preparedOrderForClosing = new OrderInfo(EXCHANGE, SYMBOL, orderSide, OrderType.MARKET, currentOpenPrice, placedOrder.getAmount());
                OrderInfo placedOrderForClosing = this.exchange.placeOrder(preparedOrderForClosing);

                double profit = tradeMode.equals(TradingMode.LONG) ? placedOrderForClosing.getPriceExecuted()*placedOrderForClosing.getAmount() - placedOrder.getPriceExecuted()*placedOrder.getAmount(): placedOrder.getPriceExecuted()*placedOrder.getAmount() - placedOrderForClosing.getPriceExecuted()*placedOrderForClosing.getAmount();
                double fee = placedOrderForClosing.getPriceExecuted() * placedOrderForClosing.getAmount() * FEE_RATE / 100 + placedOrder.getPriceExecuted() * placedOrder.getAmount() * FEE_RATE / 100;

                TradeResultEvent event = new TradeResultEvent(EXCHANGE, SYMBOL, QUOTE_CURRENCY, profit - fee, profit, placedOrderForClosing.getPriceExecuted(), placedOrder.getPriceExecuted(), placedOrder.getAmountExecuted(), fee, placedOrderForClosing.getExecTimestamp());

                String orderOpenSideStr = tradeMode.equals(TradingMode.LONG) ? "SELL" : "BUY";
                String orderCloseSideStr = tradeMode.equals(TradingMode.LONG) ? "BUY" : "SELL";
                LOGGERBUYSELL.info("[{}]\t{} price\t{}\t{} price\t{}\torder amt\t{}\tnet profit\t{}", new DateTime(placedOrderForClosing.getExecTimestamp(), UTC),orderOpenSideStr, placedOrderForClosing.getPriceExecuted(), orderCloseSideStr, placedOrder.getPriceExecuted(), placedOrder.getAmountExecuted(), profit - fee);
                this.eventPublisher.publishEvent(event);
            }
            removedKeys.add(entry.getKey());
        }

        removedKeys.forEach(key -> this.placedOrders.remove(key));

        //reset vars
        this.triggerTurningPoint = 0;
        this.orderScaleTradeCnt  = 0;
        this.orderAmtIncRateApplied = 0;
        triggerTurningPointMartigaleNumCnt = 0;
        lastMartigaleNumUsed = 0;

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

        boolean hamOrderConditionConfirmed = this.tradeMode.equals(TradingMode.LONG) ? (hma0 - hma1 > 0 && hma1 - hma2 < 0 ? true : false) : (hma0 - hma1 < 0 && hma1 - hma2 > 0  ? true : false);
        boolean hamOrderClosingConditionConfirmed = this.tradeMode.equals(TradingMode.LONG) ? (hma0 - hma1 < 0 && hma1 - hma2 > 0  ? true : false) : (hma0 - hma1 > 0 && hma1 - hma2 < 0 ? true : false);

        double candleClosePrice = this.chart.getValueReverse(1).getClosePrice();

        boolean scaleTradingConfirmed = false;

        if(hamOrderConditionConfirmed){

            if(isRiskDrawDown()){
                return;
            }

            this.triggerTurningPoint++;
            this.triggerTurningPointMartigaleNumCnt++;
            this.lastHMATurningPoint = hma1;

            if(SCALE_TRD_ORD_MARTINGALE_NUM > 0) {

                if(this.triggerTurningPointMartigaleNumCnt == 1 && this.lastMartigaleNumUsed == 0){
                    scaleTradingConfirmed = true;
                    this.lastMartigaleNumUsed++;
                    this.triggerTurningPointMartigaleNumCnt = 0;
                }else if(this.triggerTurningPointMartigaleNumCnt == this.lastMartigaleNumUsed*SCALE_TRD_ORD_MARTINGALE_NUM){
                    scaleTradingConfirmed = true;
                    this.triggerTurningPointMartigaleNumCnt = 0;
                    this.lastMartigaleNumUsed = this.lastMartigaleNumUsed*SCALE_TRD_ORD_MARTINGALE_NUM;
                }

            }else if((this.triggerTurningPoint - 1) % this.SCALE_TRD_ORD_INRERVAL == 0){
                scaleTradingConfirmed = true;
            }

        }

//        Open order
        if(hamOrderConditionConfirmed && (this.triggerTurningPoint == 1 || scaleTradingConfirmed)) {

            if(this.placedOrders.size() >= MAX_ORDER_LMT) {
                LOGGER.info("Max orders {} reached", MAX_ORDER_LMT);
                return;
            }

            if(orderAmtIncRateApplied == 0 || this.SCALE_TRD_ORD_AMT_INC_INTERVAL == 0 || this.lastHMATurningPoint < hma1){
                orderAmtIncRateApplied = getDefaultAmount();
            }else{

                if(this.orderScaleTradeCnt > 0 && (this.orderScaleTradeCnt % this.SCALE_TRD_ORD_AMT_INC_INTERVAL == 0)){
                    orderAmtIncRateApplied = orderAmtIncRateApplied*ORDER_AMT_INC_MULTI;
                }

            }

            double limitOrderPrice = candleClosePrice;
            OrderSide orderSide = tradeMode.equals(TradingMode.LONG) ? OrderSide.BUY : OrderSide.SELL;
            OrderInfo order = new OrderInfo(EXCHANGE, SYMBOL, orderSide, OrderType.MARKET, limitOrderPrice, orderAmtIncRateApplied);

            OrderInfo placedOrder = this.exchange.placeOrder(order);

            this.orderScaleTradeCnt ++;

            this.placedOrders.put(String.valueOf(timestamp), placedOrder);
            this.trailingStopPrice = 0;     // init trailing stop price

            if(tradeMode.equals(TradingMode.LONG)) {
                this.placedBuyOrder = placedOrder;       // set last buyOrder to track trailing stop
            }else{
                this.placedSellOrder = placedOrder;
            }

            LOGGERBUYSELL.info("[{}] [PLACED {} ORDER] {}", new DateTime(timestamp, UTC), orderSide.name(), orderSide.equals(OrderSide.BUY) ? this.placedBuyOrder.toString() : this.placedSellOrder.toString());
            LOGGER.info("---------------------------------------------------------------");
            LOGGER.info("ORDER {} SIGNAL DETECTED AT {}", orderSide.name(), new DateTime(e.getNewCandle().getOpenTime(), UTC));
            LOGGER.info("---------------------------------------------------------------");
            return;

        }

//close order
        if(hamOrderClosingConditionConfirmed && this.placedOrders.size() > 0){
            LOGGER.info("---------------------------------------------------------------");
            LOGGER.info("ORDER CLOSING SIGNAL DETECTED AT {}", new DateTime(e.getNewCandle().getOpenTime(), UTC));
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

    private double getDefaultAmount(){

        if(ORDER_AMT_TYPE.equals("FIAT")){

            return ORDER_AMT/this.chart.getValueReverse(0).getOpenPrice();

        }else if(ORDER_AMT_TYPE.equals("COIN")){
            return ORDER_AMT;
        }else{
            return ORDER_AMT;
        }

    }

}
