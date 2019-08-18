package com.coinhitchhiker.vbtrader.trader;

import com.coinhitchhiker.vbtrader.common.*;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

import static org.joda.time.DateTimeZone.UTC;

public class ShortTradingEngine implements TradeEngine {

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
            LOGGER.warn("curPrice 0.0 was received. Returning...");
            return;
        }

        double availableBalance = exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade();

        if(curTradingWindow.getSellOrder() != null &&
                (0 < curTradingWindow.getTrailingStopPrice() && curTradingWindow.getTrailingStopPrice() < curPrice)) {
            LOGGER.info("---------------SHORT TRAILING STOP HIT------------------------");
            LOGGER.info("trailingStopPrice {} < curPrice {}", curTradingWindow.getTrailingStopPrice(), curPrice);
            sellAtMarketPrice();
            curTradingWindow.clearOutOrders();
            return;
        }

        // if a sell order was placed in this trading window and no trailing stop price was touched
        // we do nothing until this trading window is over
        if(curTradingWindow.getSellOrder() != null) {
            LOGGER.info("-----------------------PLACED ORDER PRESENT---------------------");
            LOGGER.info(curTradingWindow.toString());
            return;
        }

        double k = VolatilityBreakoutRules.getKValue(lookbehindTradingWindows);

        // sell signal!
        if(curTradingWindow.isSellSignal(curPrice, k, lookbehindTradingWindows.get(0))) {
            double volume = curTradingWindow.getVolume();
            double sellPrice = curPrice * (1 - LIMIT_ORDER_PREMIUM/100.0D);

            double priceMAScore = VolatilityBreakoutRules.getPriceMAScore(lookbehindTradingWindows, curPrice, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
            double volumeMAScore = VolatilityBreakoutRules.getVolumeMAScore_conservative(lookbehindTradingWindows, volume, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
            double weightedMAScore = (PRICE_MA_WEIGHT*priceMAScore + VOLUME_MA_WEIGHT*volumeMAScore) / (PRICE_MA_WEIGHT + VOLUME_MA_WEIGHT);


            double cost = availableBalance * weightedMAScore;

            if(cost > 0) {
                LOGGER.info("[---------------------SELL SIGNAL DETECTED----------------------------]");
                LOGGER.info("cost {} = availableBalance {} * weightedMAScore {}", cost, availableBalance, weightedMAScore);
                LOGGER.info("curPrice {} < {} (openPrice {} - k {} * prevRange {})",
                        curPrice ,
                        curTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange() ,
                        curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());

                double amount = cost / sellPrice;
                OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, sellPrice, amount);
                OrderInfo placedSellOrder = exchange.placeOrder(sellOrder);

                curTradingWindow.setSellOrder(placedSellOrder);
                double sellFee = placedSellOrder.getAmountExecuted() * placedSellOrder.getPriceExecuted() * FEE_RATE / 100.0D;
                curTradingWindow.setSellFee(sellFee);
                LOGGER.info("[PLACED SELL ORDER] {}", placedSellOrder.toString());
                LOGGER.info("[PLACED SELL ORDER] position close at {} ", new DateTime(curTradingWindow.getEndTimeStamp(), UTC));
            } else {
                LOGGER.info("[-----------------SELL SIGNAL DETECTED BUT COST IS 0------------------------]");
                LOGGER.info("cost {} = availableBalance {} * weightedMAScore {}", cost, availableBalance, weightedMAScore);
                LOGGER.info("curPrice {} < {} (openPrice {} - k {} * prevRange {})",
                        curPrice ,
                        curTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange() ,
                        curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
            }
        } else {
            LOGGER.info("[---------------------NO SELL SIGNAL DETECTED----------------------------]");
            LOGGER.info("curPrice {} > {} (openPrice {} - k {} * prevRange {})",
                    curPrice ,
                    curTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange() ,
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

        OrderInfo placedSellOrder = curTradingWindow.getSellOrder();
        if(placedSellOrder == null) return;

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
        } catch(Exception e) {
            LOGGER.error("Placing buy order error", e);
        }
    }
}
