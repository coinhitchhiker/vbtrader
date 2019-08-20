package com.coinhitchhiker.vbtrader.common.trade;

import com.coinhitchhiker.vbtrader.common.Util;
import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.strategy.PVTOBV;
import com.coinhitchhiker.vbtrader.common.strategy.VolatilityBreakoutRules;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.joda.time.DateTimeZone.UTC;

public class LongTradingEngine implements TradingEngine {

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

    private DateTime lastClosestMin = DateTime.now();

    private VolatilityBreakoutRules vbRules;
    private PVTOBV pvtobv = new PVTOBV();

    public LongTradingEngine(Repository repository, Exchange exchange, OrderBookCache orderBookCache,
                             int TRADING_WINDOW_LOOK_BEHIND, String SYMBOL, String QUOTE_CURRENCY, double LIMIT_ORDER_PREMIUM,
                             String EXCHANGE, double FEE_RATE, boolean TRADING_ENABLED) {
        this.repository = repository;
        this.exchange = exchange;
        this.orderBookCache = orderBookCache;

        this.TRADING_WINDOW_LOOK_BEHIND = TRADING_WINDOW_LOOK_BEHIND;
        this.SYMBOL = SYMBOL;
        this.QUOTE_CURRENCY = QUOTE_CURRENCY;
        this.LIMIT_ORDER_PREMIUM = LIMIT_ORDER_PREMIUM;
        this.EXCHANGE = EXCHANGE;
        this.FEE_RATE = FEE_RATE;
        this.TRADING_ENABLED =  TRADING_ENABLED;
    }

    private void buildTechnicalIndicator(long curTimestamp) {
        DateTime curClosestMin = Util.getClosestMin(new DateTime(curTimestamp, UTC));

        if(lastClosestMin.equals(curClosestMin)) return;

        // now 1min passed... build indicator for the 1min candle
        double pvt = repository.getPVT(curTimestamp);
        double obv = repository.getOBV(curTimestamp);

        pvtobv.addPvtValue(pvt);
        pvtobv.addObvValue(obv);

        DateTime dt = new DateTime(curTimestamp, UTC);
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd H:m");
        String str = fmt.print(dt);

//        System.out.println(str + "," + pvt + "," + pvtobv.pvtDelta() + "," + obv + "," + pvtobv.obvDelta());

        lastClosestMin = curClosestMin;

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

        if(curTimestamp > curTradingWindow.getEndTimeStamp()) {
            TradeResult tradeResult = sellAtMarketPrice(curTimestamp);
            repository.refreshTradingWindows();
            return tradeResult;
        }

        buildTechnicalIndicator(curTimestamp);

        if(curTradingWindow.getBuyOrder() != null &&
                curTradingWindow.getTrailingStopPrice() > curPrice) {
            LOGGER.info("---------------LONG TRAILING STOP HIT------------------------");
            LOGGER.info("trailingStopPrice {} > curPrice {}", curTradingWindow.getTrailingStopPrice(), curPrice);
            TradeResult tradeResult = sellAtMarketPrice(curTimestamp);
            curTradingWindow.clearOutOrders();
            return tradeResult;
        }

        // if a buy order was placed in this trading window and no trailing stop price has been touched
        // we do nothing until this trading window is over
        if(curTradingWindow.getBuyOrder() != null) {
            LOGGER.info("-----------------------PLACED ORDER PRESENT---------------------");
            LOGGER.info(curTradingWindow.toString());
            return null;
        }

        double vbBuySignal = vbRules.buySignalStrength(curPrice, curTradingWindow, lookbehindTradingWindows, curTimestamp);
        double pvtobvBuySignal = pvtobv.buySignalStrength();

        if(vbBuySignal > 0 || pvtobvBuySignal > 0) {
            LOGGER.info("{} vbBuySignal {} pvtobvBuySignal {}", new DateTime(curTimestamp, UTC), vbBuySignal, pvtobvBuySignal);
        }

        //double signalStrenth = vbBuySignal;
        double signalStrength = pvtobvBuySignal;

        if(signalStrength == 0) return null;

        double availableBalance = exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade();
        double buyPrice = curPrice * (1 + LIMIT_ORDER_PREMIUM/100.0D);
        double cost = availableBalance * signalStrength;
        double amount = cost / buyPrice;
        OrderInfo buyOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.BUY, buyPrice, amount);

        try {
            if(!this.TRADING_ENABLED) {
                LOGGER.info("TRADING DISABLED!");
                return null;
            }

            OrderInfo placedBuyOrder = exchange.placeOrder(buyOrder);
            LOGGER.info("[PLACED BUY ORDER] {}", placedBuyOrder.toString());
            LOGGER.info("[PLACED BUY ORDER] liquidation expected at {} ", new DateTime(curTradingWindow.getEndTimeStamp(), UTC));
            double buyFee = placedBuyOrder.getAmountExecuted() * placedBuyOrder.getPriceExecuted() * FEE_RATE / 100.0D;
            curTradingWindow.setBuyOrder(placedBuyOrder);
            curTradingWindow.setBuyFee(buyFee);
        } catch(Exception e) {
            LOGGER.error("Placing buy order failed", e);
        }

        LOGGER.info("tradingWindow endTime {} curTime {} h {} l {}"
            , new DateTime(curTradingWindow.getEndTimeStamp(), UTC)
            , new DateTime(curTimestamp, UTC)
            , curTradingWindow.getHighPrice()
            , curTradingWindow.getLowPrice());

        return null;
    }

    private TradeResult sellAtMarketPrice(long curTimeStamp) {
        TradeResult tradeResult = null;
        TradingWindow curTradingWindow = repository.getCurrentTradingWindow(curTimeStamp);

        if(curTradingWindow.getBuyOrder() != null) {
            LOGGER.info("-----------------------------SELL IT!!!!!!----------------------");
            OrderInfo placedBuyOrder = curTradingWindow.getBuyOrder();
            double bestBid = orderBookCache.getBestBid();
            double sellPrice = bestBid * (1 - LIMIT_ORDER_PREMIUM/100.0D);
            double sellAmount = placedBuyOrder.getAmountExecuted();
            OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, sellPrice, sellAmount);
            OrderInfo placedSellOrder = null;

            try {
                placedSellOrder = exchange.placeOrder(sellOrder);
                LOGGER.info("[PLACED SELL ORDER] {}", placedSellOrder.toString());

                double buyFee = curTradingWindow.getBuyFee();
                double sellFee = placedSellOrder.getAmountExecuted() * placedSellOrder.getPriceExecuted() * FEE_RATE / 100.0D;
                double profit = (placedSellOrder.getPriceExecuted() - placedBuyOrder.getPriceExecuted()) * sellAmount;
                double netProfit =  profit - (buyFee + sellFee);

                curTradingWindow.setSellFee(sellFee);
                curTradingWindow.setProfit(profit);
                curTradingWindow.setNetProfit(netProfit);
                curTradingWindow.setSellOrder(placedSellOrder);

                LOGGER.info("[LONG] [{}] netProfit={}, profit={}, fee={}, sellPrice={} buyPrice={}, amount={},  ",
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
                LOGGER.error("Placing sell order error", e);
            }
        }

        return tradeResult;
    }

    @Autowired
    public void setVBRules(VolatilityBreakoutRules vbRules) {
        this.vbRules = vbRules;
    }
}
