package com.coinhitchhiker.vbtrader.common.trade;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.strategy.PVTOBV;
import com.coinhitchhiker.vbtrader.common.strategy.Strategy;
import com.coinhitchhiker.vbtrader.common.strategy.VolatilityBreakout;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.joda.time.DateTimeZone.UTC;

public class ShortTradingEngine implements TradingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(LongTradingEngine.class);

    private final Repository repository;
    private final Exchange exchange;
    private final OrderBookCache orderBookCache;

    private final int TRADING_WINDOW_LOOK_BEHIND;
    private final String SYMBOL;
    private final String QUOTE_CURRENCY;
    private final double LIMIT_ORDER_PREMIUM;
    private final String EXCHANGE;
    private final double FEE_RATE;
    private final boolean TRADING_ENABLED;
    private final boolean TRAILING_STOP_ENABLED;

    private DateTime lastClosestMin = DateTime.now();

    private Strategy strategy;
    private PVTOBV pvtobv;

    public ShortTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                             int TRADING_WINDOW_LOOK_BEHIND, String SYMBOL, String QUOTE_CURRENCY, double LIMIT_ORDER_PREMIUM,
                             String EXCHANGE, double FEE_RATE, boolean TRADING_ENABLED, boolean TRAILING_STOP_ENABLED) {
        this.repository = repository;
        this.exchange = exchange;
        this.orderBookCache = orderBookCache;

        this.TRADING_WINDOW_LOOK_BEHIND = TRADING_WINDOW_LOOK_BEHIND;
        this.SYMBOL = SYMBOL;
        this.QUOTE_CURRENCY = QUOTE_CURRENCY;
        this.LIMIT_ORDER_PREMIUM = LIMIT_ORDER_PREMIUM;
        this.EXCHANGE = EXCHANGE;
        this.FEE_RATE = FEE_RATE;
        this.TRADING_ENABLED = TRADING_ENABLED;
        this.TRAILING_STOP_ENABLED = TRAILING_STOP_ENABLED;
    }

    @Override
    public TradeResult run(double curPrice, long curTimestamp) {

        TradingWindow curTradingWindow = repository.getCurrentTradingWindow(curTimestamp);
        if(curTradingWindow == null) {
            LOGGER.debug("curTradingWindow is null");
            return null;
        }

        List<TradingWindow> lookbehindTradingWindows = repository.getLastNTradingWindow(TRADING_WINDOW_LOOK_BEHIND+1, curTimestamp);
        if(lookbehindTradingWindows.size() < TRADING_WINDOW_LOOK_BEHIND+1) {
            LOGGER.debug("lookbehindTradingWindows.size() {} < TRADING_WINDOW_LOOK_BEHIND {}", lookbehindTradingWindows.size(), TRADING_WINDOW_LOOK_BEHIND);
            return null;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("curPrice", curPrice);
        params.put("curTradingWindow", curTradingWindow);
        params.put("lookbehindTradingWindows", lookbehindTradingWindows);
        params.put("curTimestamp", curTimestamp);

        if(curTimestamp > curTradingWindow.getEndTimeStamp()) {
            TradeResult tradeResult = sellAtMarketPrice(curTimestamp);
            repository.refreshTradingWindows();
            return tradeResult;
        }

        if(curPrice == 0.0) {
            LOGGER.warn("curPrice 0.0 was received. Returning...");
            return null;
        }

        double availableBalance = exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade();

        if(curTradingWindow.getSellOrder() != null &&
                (0 < curTradingWindow.getTrailingStopPrice() && curTradingWindow.getTrailingStopPrice() < curPrice)) {
            LOGGER.info("---------------SHORT TRAILING STOP HIT------------------------");
            LOGGER.info("trailingStopPrice {} < curPrice {}", curTradingWindow.getTrailingStopPrice(), curPrice);
            TradeResult tradeResult = sellAtMarketPrice(curTimestamp);
            curTradingWindow.clearOutOrders();
            return tradeResult;
        }

        // if a sell order was placed in this trading window and no trailing stop price was touched
        // we do nothing until this trading window is over
        if(curTradingWindow.getSellOrder() != null) {
            LOGGER.info("-----------------------PLACED ORDER PRESENT---------------------");
            LOGGER.info(curTradingWindow.toString());
            return null;
        }

        double signalStrength = strategy.sellSignalStrength(params);

        if(signalStrength == 0) return null;

        double sellPrice = curPrice * (1 - LIMIT_ORDER_PREMIUM/100.0D);
        double cost = availableBalance * signalStrength;
        double amount = cost / sellPrice;

        try {
            if(!this.TRADING_ENABLED) {
                LOGGER.info("TRADING DISABLED!");
                return null;
            }

            if(true) {
                LOGGER.error("Refactoring in progress....... No actual trading will happen!!");
                return null;
            }

            OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, sellPrice, amount);
            OrderInfo placedSellOrder = exchange.placeOrder(sellOrder);
            LOGGER.info("[PLACED SELL ORDER] {}", placedSellOrder.toString());
            LOGGER.info("[PLACED SELL ORDER] position close at {} ", new DateTime(curTradingWindow.getEndTimeStamp(), UTC));
            double sellFee = placedSellOrder.getAmountExecuted() * placedSellOrder.getPriceExecuted() * FEE_RATE / 100.0D;
            curTradingWindow.setSellOrder(placedSellOrder);
            curTradingWindow.setSellFee(sellFee);
        } catch(Exception e ) {
            LOGGER.error("Placing buy order failed", e);
        }

        LOGGER.info("tradingWindow endTime {} curTime {} h {} l {}"
                , new DateTime(curTradingWindow.getEndTimeStamp(), UTC)
                , new DateTime(curTimestamp, UTC)
                , curTradingWindow.getHighPrice()
                , curTradingWindow.getLowPrice()
        );

        return null;
    }

    private TradeResult sellAtMarketPrice(long curTimeStamp) {
        TradeResult tradeResult = null;
        TradingWindow curTradingWindow = repository.getCurrentTradingWindow(curTimeStamp);

        OrderInfo placedSellOrder = curTradingWindow.getSellOrder();
        if(placedSellOrder == null) return null;

        LOGGER.info("--------------------------SELl POSITION CLOSE. BUY IT!----------------------");
        double bestAsk = orderBookCache.getBestAsk();
        if(bestAsk == 0.0) throw new RuntimeException("bestAsk was 0");

        double buyPrice = bestAsk * (1 + LIMIT_ORDER_PREMIUM/100.0D);
        double buyAmount = placedSellOrder.getAmountExecuted();
        OrderInfo buyOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.BUY, buyPrice, buyAmount);
        OrderInfo placedBuyOrder = null;

        try {
            placedBuyOrder = exchange.placeOrder(buyOrder);
            LOGGER.info("[PLACED BUY ORDER] {}", placedBuyOrder.toString());

            double sellFee = curTradingWindow.getSellFee();
            double buyFee = placedBuyOrder.getAmountExecuted() * placedBuyOrder.getPriceExecuted() * FEE_RATE / 100.0D;
            double profit = (placedSellOrder.getPriceExecuted() - placedBuyOrder.getPriceExecuted()) * buyAmount;
            double netProfit =  profit - (buyFee + sellFee);

            curTradingWindow.setSellFee(sellFee);
            curTradingWindow.setProfit(profit);
            curTradingWindow.setNetProfit(netProfit);
            curTradingWindow.setBuyOrder(placedBuyOrder);

            LOGGER.info("[SHORT] [{}] netProfit={}, profit={}, fee={}, sellPrice={} buyPrice={}, amount={},  ",
                    netProfit >= 0 ? "PROFIT" : "LOSS",
                    netProfit,
                    profit,
                    buyFee + sellFee,
                    placedSellOrder.getPriceExecuted(),
                    placedBuyOrder.getPriceExecuted(),
                    placedBuyOrder.getAmountExecuted());
            LOGGER.info("-----------------------------------------------------------------------------------------");
            repository.logCompleteTradingWindow(curTradingWindow);
            tradeResult = new TradeResult(EXCHANGE, SYMBOL, QUOTE_CURRENCY, netProfit, profit, placedSellOrder.getPriceExecuted(), placedBuyOrder.getPriceExecuted(), placedBuyOrder.getAmountExecuted(), buyFee + sellFee);
        } catch(Exception e) {
            LOGGER.error("Placing buy order error", e);
        }

        return tradeResult;
    }

    @Autowired
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }
}
