package com.coinhitchhiker.vbtrader.trader;


import com.coinhitchhiker.vbtrader.common.*;
import com.coinhitchhiker.vbtrader.trader.db.TraderDAO;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.Binance;
import com.google.gson.Gson;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.List;

import static org.joda.time.DateTimeZone.UTC;

// This class gets initialized in TraderAppConfig as a spring bean
public class TradeEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeEngine.class);
    private static final int MA_MIN = 3;

    @Value("${trading.exchange}") private String EXCHANGE;
    @Value("${trading.symbol}") private String SYMBOL;
    @Value("${trading.window.size}") private int TRADING_WINDOW_SIZE;
    @Value("${trading.look.behind}") private int TRADING_WINDOW_LOOK_BEHIND;
    @Value("${trading.price.weight}") private double PRICE_MA_WEIGHT;
    @Value("${trading.volume.weight}") private double VOLUME_MA_WEIGHT;
    @Value("${trading.betting.size}") private double BETTING_SIZE;
    @Value("${trading.limit.order.premium}") private double LIMIT_ORDER_PREMIUM;
    @Value("${trading.fee.rate}") private double FEE_RATE;

    @Resource(name = Binance.BEAN_NAME_BINANCE)
    private Exchange exchange;

    @Resource(name = Binance.BEAN_NAME_BINANCE)
    private Repository repository;

    @Autowired
    private TraderDAO traderDAO;

    @Scheduled(fixedDelay = 2_000L)
    protected void trade() {

        checkCurTradingWindowStatus();

        long curTimeStamp = DateTime.now(UTC).getMillis();
        TradingWindow curTradingWindow = repository.getCurrentTradingWindow(curTimeStamp);
        if(curTradingWindow == null) {
            LOGGER.debug("curTradingWindow is null");
            return;
        }

        // if a buy order was placed in this trading windows, we do nothing until this trading window is over
        if(curTradingWindow.getBuyOrder() != null) return;

        List<TradingWindow> lookbehindTradingWindows = repository.getLastNTradingWindow(TRADING_WINDOW_LOOK_BEHIND, curTimeStamp);
        if(lookbehindTradingWindows.size() < TRADING_WINDOW_LOOK_BEHIND) {
            LOGGER.debug("lookbehindTradingWindows.size() {} < TRADING_WINDOW_LOOK_BEHIND {}", lookbehindTradingWindows.size(), TRADING_WINDOW_LOOK_BEHIND);
            return;
        }

        double k = VolatilityBreakoutRules.getKValue(lookbehindTradingWindows);
        double curPrice = exchange.getCurrentPrice(SYMBOL);

        if(!curTradingWindow.isBuySignal(curPrice, k, lookbehindTradingWindows.get(0))) {
            LOGGER.debug("----------NO BUY SIGNAL DETECTED-------------");
            LOGGER.debug("curPrice {} <= {} (openPrice {} + k {} * prevRange {})",
                    curPrice ,
                    curTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange() ,
                    curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
            return;
        }

        LOGGER.info("-----------------BUY SIGNAL DETECTED--------------------");
        LOGGER.info("curPrice {} > {} (openPrice {} + k {} * prevRange {})",
                curPrice ,
                curTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange() ,
                curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());

        double volume = curTradingWindow.getVolume();
        double priceMAScore = VolatilityBreakoutRules.getPriceMAScore(lookbehindTradingWindows, curPrice, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
        double volumeMAScore = VolatilityBreakoutRules.getVolumeMAScore(lookbehindTradingWindows, volume, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
        double weightedMAScore = (PRICE_MA_WEIGHT*priceMAScore + VOLUME_MA_WEIGHT*volumeMAScore) / (PRICE_MA_WEIGHT + VOLUME_MA_WEIGHT);

        double buyPrice = curPrice * (1 + LIMIT_ORDER_PREMIUM/100.0D);
        double bettingSize = BETTING_SIZE * weightedMAScore;

        LOGGER.info("bettingSize {} = BETTING_SIZE {} * weightedMAScore {}", bettingSize, BETTING_SIZE, weightedMAScore);
        if(bettingSize <= 0) {
            LOGGER.info("No buy order placement BETTING SIZE 0");
            return;
        }

        double amount = bettingSize / buyPrice;
        OrderInfo buyOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.BUY, buyPrice, amount);
        LOGGER.info(buyOrder.toString());

        try {
            OrderInfo placedBuyOrder = exchange.placeOrder(buyOrder);
            LOGGER.info("[PLACED BUY ORDER] {}", placedBuyOrder.toString());
            LOGGER.info("[PLACED BUY ORDER] window_size {} lookbehind {} price_w {} vol_w {}", TRADING_WINDOW_SIZE, TRADING_WINDOW_LOOK_BEHIND, PRICE_MA_WEIGHT, VOLUME_MA_WEIGHT);
            LOGGER.info("[PLACED BUY ORDER] liquidation expected at {} ", new DateTime(curTradingWindow.getEndTimeStamp(), UTC));
            curTradingWindow.setBuyOrder(placedBuyOrder);
            double buyFee = placedBuyOrder.getAmountExecuted() * placedBuyOrder.getPriceExecuted() * FEE_RATE / 100.0D;
            curTradingWindow.setBuyFee(buyFee);
        } catch(Exception e) {
            LOGGER.error("Placing buy order failed", e);
        }
    }

    private void checkCurTradingWindowStatus() {
        DateTime now = DateTime.now(UTC);
        long curTimeStamp = now.getMillis();
        TradingWindow curTradingWindow = repository.getCurrentTradingWindow(curTimeStamp);

        if(curTimeStamp <= curTradingWindow.getEndTimeStamp()) {
            return;
        }

        if(curTradingWindow.getBuyOrder() != null) {
//            OrderInfo buyOrder = curTradingWindow.getBuyOrder();
            LOGGER.info("-----------------------------SELL IT!!!!!!----------------------");
            OrderInfo placedBuyOrder = curTradingWindow.getBuyOrder();
            LOGGER.info(placedBuyOrder.toString());
            double bestBid = exchange.getBestBid();
            double sellPrice = bestBid * (1 - LIMIT_ORDER_PREMIUM/100.0D);

            // if it's partially filled, sell only filled amount and cancel remaining
            // if it's filled, sell all at best bid + 1 unit price
            double sellAmount = placedBuyOrder.getAmountExecuted();
            OrderInfo sellOrder = new OrderInfo("BINANCE", SYMBOL, OrderSide.SELL, sellPrice, sellAmount);
            OrderInfo placedSellOrder = null;

            try {
                placedSellOrder = exchange.placeOrder(sellOrder);
                LOGGER.info("[PLACED SELL ORDER] {}", FEE_RATE, LIMIT_ORDER_PREMIUM, placedSellOrder.toString());

                double buyFee = curTradingWindow.getBuyFee();
                double sellFee = placedSellOrder.getAmountExecuted() * placedSellOrder.getPriceExecuted() * FEE_RATE / 100.0D;
                double profit = (placedSellOrder.getPriceExecuted() - placedBuyOrder.getPriceExecuted()) * sellAmount;
                double netProfit =  profit - (buyFee + sellFee);

                curTradingWindow.setSellFee(sellFee);
                curTradingWindow.setProfit(profit);
                curTradingWindow.setNetProfit(netProfit);

                LOGGER.info("[{}] netProfit={}, profit={}, fee={}, sellPrice={} buyPrice={}, amount={},  ",
                        netProfit >= 0 ? "PROFIT" : "LOSS",
                        netProfit,
                        profit,
                        buyFee + sellFee,
                        placedSellOrder.getPriceExecuted(),
                        placedBuyOrder.getPriceExecuted(),
                        placedBuyOrder.getAmountExecuted());
                LOGGER.info("-----------------------------------------------------------------------------------------");
                traderDAO.logCompleteTransaction(new Gson().toJson(curTradingWindow));
            } catch(Exception e) {
                LOGGER.error("Placing sell order error", e);
            }
        }

        exchange.refreshTradingWindows();

        LOGGER.info("-----------CURRENT TRADING WINDOW REFRESHED-----------------------");
        LOGGER.info("curTimestamp {}", now);
        LOGGER.info("{}", repository.getCurrentTradingWindow(curTimeStamp));
    }

    @PreDestroy
    public void cancelAllOutstandingOrders() {
        OrderInfo outstandingBuyOrder = repository.getCurrentTradingWindow(DateTime.now(UTC).getMillis()).getBuyOrder();
        if(outstandingBuyOrder.getOrderStatus() != OrderStatus.COMPLETE) {
            LOGGER.info("[CANCEL] {}", outstandingBuyOrder.toString());
            exchange.cancelOrder(outstandingBuyOrder);
        } else {
            LOGGER.info("[OPEN ORDER] {}", outstandingBuyOrder.toString());
        }
    }

}
