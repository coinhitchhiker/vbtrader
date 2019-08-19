package com.coinhitchhiker.vbtrader.trader;


import com.coinhitchhiker.vbtrader.common.*;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import java.util.List;

import static org.joda.time.DateTimeZone.UTC;

// This class gets initialized in TraderAppConfig as a spring bean
public class LongTradingEngine implements TradeEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(LongTradingEngine.class);
    private static final int MA_MIN = 3;

    @Value("${trading.exchange}") private String EXCHANGE;
    @Value("${trading.symbol}") private String SYMBOL;
    @Value("${trading.quote.currency}") private String QUOTE_CURRENCY;
    @Value("${trading.window.size}") private int TRADING_WINDOW_SIZE;
    @Value("${trading.look.behind}") private int TRADING_WINDOW_LOOK_BEHIND;
    @Value("${trading.price.weight}") private double PRICE_MA_WEIGHT;
    @Value("${trading.volume.weight}") private double VOLUME_MA_WEIGHT;
    @Value("${trading.limit.order.premium}") private double LIMIT_ORDER_PREMIUM;
    @Value("${trading.fee.rate}") private double FEE_RATE;

    @Autowired private Exchange exchange;
    @Autowired private Repository repository;
    @Autowired private OrderBookCache orderBookCache;

    @Scheduled(fixedDelay = 10_000L)
    protected void trade() {

        long curTimeStamp = DateTime.now(UTC).getMillis();
        TradingWindow curTradingWindow = repository.getCurrentTradingWindow(curTimeStamp);
        if(curTradingWindow == null) {
            LOGGER.debug("curTradingWindow is null");
            return;
        }

        List<TradingWindow> lookbehindTradingWindows = repository.getLastNTradingWindow(TRADING_WINDOW_LOOK_BEHIND, curTimeStamp);
        if(lookbehindTradingWindows.size() < TRADING_WINDOW_LOOK_BEHIND) {
            LOGGER.debug("lookbehindTradingWindows.size() {} < TRADING_WINDOW_LOOK_BEHIND {}", lookbehindTradingWindows.size(), TRADING_WINDOW_LOOK_BEHIND);
            return;
        }

        if(curTimeStamp > curTradingWindow.getEndTimeStamp()) {
            sellAtMarketPrice();
            repository.refreshTradingWindows();
            return;
        }

        double curPrice = exchange.getCurrentPrice(SYMBOL);
        if(curPrice == 0.0) {
            LOGGER.warn("curPrice 0.0 was received. Returning... will check again in a minute");
            return;
        }

        double availableBalance = exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade();

        if(curTradingWindow.getBuyOrder() != null &&
                curTradingWindow.getTrailingStopPrice() > curPrice) {
            LOGGER.info("---------------LONG TRAILING STOP HIT------------------------");
            LOGGER.info("trailingStopPrice {} > curPrice {}", curTradingWindow.getTrailingStopPrice(), curPrice);
            sellAtMarketPrice();
            curTradingWindow.clearOutOrders();
            return;
        }

        // if a buy order was placed in this trading window and no trailing stop price has been touched
        // we do nothing until this trading window is over
        if(curTradingWindow.getBuyOrder() != null) {
            LOGGER.info("-----------------------PLACED ORDER PRESENT---------------------");
            LOGGER.info(curTradingWindow.toString());
            return;
        }

        double k = VolatilityBreakoutRules.getKValue(lookbehindTradingWindows);

        if(curTradingWindow.isBuySignal(curPrice, k, lookbehindTradingWindows.get(0))) {
            double volume = curTradingWindow.getVolume();
            double buyPrice = curPrice * (1 + LIMIT_ORDER_PREMIUM/100.0D);

            double priceMAScore = VolatilityBreakoutRules.getPriceMAScore(lookbehindTradingWindows, curPrice, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
//            double volumeMAScore = VolatilityBreakoutRules.getVolumeMAScore_conservative(lookbehindTradingWindows, volume, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
            double volumeMAScore = VolatilityBreakoutRules.getVolumeMAScore_aggresive(lookbehindTradingWindows, curTradingWindow, MA_MIN, TRADING_WINDOW_LOOK_BEHIND, TRADING_WINDOW_SIZE, curTimeStamp);
            double weightedMAScore = (PRICE_MA_WEIGHT*priceMAScore + VOLUME_MA_WEIGHT*volumeMAScore) / (PRICE_MA_WEIGHT + VOLUME_MA_WEIGHT);

            double cost = availableBalance * weightedMAScore;

            if(cost > 0) {
                LOGGER.info("[---------------------BUY SIGNAL DETECTED----------------------------]");
                LOGGER.info("cost {} = availableBalance {} * weightedMAScore {}", cost, availableBalance, weightedMAScore);
                LOGGER.info("curPrice {} > {} (openPrice {} + k {} * prevRange {})",
                        curPrice ,
                        curTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange() ,
                        curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());

                double amount = cost / buyPrice;
                OrderInfo buyOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.BUY, buyPrice, amount);

                try {
                    OrderInfo placedBuyOrder = exchange.placeOrder(buyOrder);
                    LOGGER.info("[PLACED BUY ORDER] {}", placedBuyOrder.toString());
                    LOGGER.info("[PLACED BUY ORDER] liquidation expected at {} ", new DateTime(curTradingWindow.getEndTimeStamp(), UTC));
                    double buyFee = placedBuyOrder.getAmountExecuted() * placedBuyOrder.getPriceExecuted() * FEE_RATE / 100.0D;
                    curTradingWindow.setBuyOrder(placedBuyOrder);
                    curTradingWindow.setBuyFee(buyFee);
                } catch(Exception e) {
                    LOGGER.error("Placing buy order failed", e);
                }
            } else {
                LOGGER.info("[-----------------BUY SIGNAL DETECTED BUT COST IS 0------------------------]");
                LOGGER.info("cost {} = availableBalance {} * weightedMAScore {}", cost, availableBalance, weightedMAScore);
                LOGGER.info("curPrice {} > {} (openPrice {} + k {} * prevRange {})",
                        curPrice ,
                        curTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange() ,
                        curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
                LOGGER.info("tradingWindow endTime {}", new DateTime(curTradingWindow.getEndTimeStamp(), UTC));
            }
        } else {
            LOGGER.info("[---------------------NO BUY SIGNAL DETECTED----------------------------]");
            LOGGER.info("curPrice {} < {} (openPrice {} + k {} * prevRange {})",
                    curPrice ,
                    curTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange() ,
                    curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
            LOGGER.info("Available Balance {} {}", QUOTE_CURRENCY, availableBalance);
        }
        LOGGER.info("tradingWindow endTime {} curTime {} h {} l {}"
                , new DateTime(curTradingWindow.getEndTimeStamp(), UTC)
                , new DateTime(curTimeStamp, UTC)
                , curTradingWindow.getHighPrice()
                , curTradingWindow.getLowPrice()
        );
    }

    private void sellAtMarketPrice() {
        DateTime now = DateTime.now(UTC);
        long curTimeStamp = now.getMillis();
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
            } catch(Exception e) {
                LOGGER.error("Placing sell order error", e);
            }
        }
    }
}
