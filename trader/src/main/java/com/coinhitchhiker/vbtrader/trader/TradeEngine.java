package com.coinhitchhiker.vbtrader.trader;


import com.coinhitchhiker.vbtrader.common.*;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceExchange;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceOrderBookCache;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.BinanceRepository;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    @Value("${trading.quote.currency}") private String QUOTE_CURRENCY;
    @Value("${trading.window.size}") private int TRADING_WINDOW_SIZE;
    @Value("${trading.look.behind}") private int TRADING_WINDOW_LOOK_BEHIND;
    @Value("${trading.price.weight}") private double PRICE_MA_WEIGHT;
    @Value("${trading.volume.weight}") private double VOLUME_MA_WEIGHT;
    @Value("${trading.limit.order.premium}") private double LIMIT_ORDER_PREMIUM;
    @Value("${trading.fee.rate}") private double FEE_RATE;

    @Resource(name = BinanceExchange.BEAN_NAME_EXCHANGE_BINANCE)
    private Exchange exchange;

    @Resource(name = BinanceRepository.BEAN_NAME_REPOSITORY_BINANCE)
    private Repository repository;

    @Resource(name = BinanceOrderBookCache.BEAN_NAME_ORDERBOOK_CACHE_BINANCE)
    private OrderBookCache orderBookCache;

    @Scheduled(fixedDelay = 60_000L)
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
        }

        double curPrice = exchange.getCurrentPrice(SYMBOL);

        if(curTradingWindow.getBuyOrder() != null && curTradingWindow.getTrailingStopPrice() > curPrice) {
            // market sell
            sellAtMarketPrice();
            repository.refreshTradingWindows();
            return;
        }

        if(curTradingWindow.getSellOrder() != null &&
                (0 < curTradingWindow.getTrailingStopPrice() && curTradingWindow.getTrailingStopPrice() < curPrice)) {
            // market sell
            sellAtMarketPrice();
            repository.refreshTradingWindows();
            return;
        }

        // if a buy/sell order was placed in this trading window and no trailing stop price was broken,
        // we do nothing until this trading window is over
        if(curTradingWindow.getBuyOrder() != null || curTradingWindow.getSellOrder() != null) return;

        double k = VolatilityBreakoutRules.getKValue(lookbehindTradingWindows);

        // sell signal!
//            if(curTradingWindow.isSellSignal(curPrice, k, lookbehindTradingWindows.get(0))) {
//                double volume = getCurTradingWindowVol(curTradingWindow.getCandles(), curTimeStamp);
//                double sellPrice = curTradingWindow.getOpenPrice() - k * lookbehindTradingWindows.get(0).getRange();
//
//                double priceMAScore = VolatilityBreakoutRules.getPriceMAScore(lookbehindTradingWindows, curPrice, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
//                double volumeMAScore = VolatilityBreakoutRules.getVolumeMAScore(lookbehindTradingWindows, volume, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
//                double weightedMAScore = (PRICE_MA_WEIGHT*priceMAScore + VOLUME_MA_WEIGHT*volumeMAScore) / (PRICE_MA_WEIGHT + VOLUME_MA_WEIGHT);
//
//                double bettingSize = exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade() * weightedMAScore;
//
//                if(bettingSize > 0) {
//                    LOGGER.info("[---------------------SELL SIGNAL DETECTED----------------------------]");
//                    double amount = bettingSize / sellPrice;
//                    OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, sellPrice, amount);
//                    OrderInfo placedSellOrder = exchange.placeOrder(sellOrder);
//
//                    curTradingWindow.setSellOrder(placedSellOrder);
//                    LOGGER.info("[PLACED SELL ORDER] {}", placedSellOrder.toString());
//                    LOGGER.debug("[PLACED SELL ORDER] liquidation expected at {} ", new DateTime(curTradingWindow.getEndTimeStamp(), UTC));
//                    double sellFee = placedSellOrder.getAmountExecuted() * placedSellOrder.getPriceExecuted() * FEE_RATE / 100.0D;
//                    curTradingWindow.setSellFee(sellFee);
//                }
//            }

        if(curTradingWindow.isBuySignal(curPrice, k, lookbehindTradingWindows.get(0))) {

            double volume = curTradingWindow.getVolume();
            double buyPrice = curPrice * (1 + LIMIT_ORDER_PREMIUM/100.0D);

            double priceMAScore = VolatilityBreakoutRules.getPriceMAScore(lookbehindTradingWindows, curPrice, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
            double volumeMAScore = VolatilityBreakoutRules.getVolumeMAScore_conservative(lookbehindTradingWindows, volume, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
            double weightedMAScore = (PRICE_MA_WEIGHT*priceMAScore + VOLUME_MA_WEIGHT*volumeMAScore) / (PRICE_MA_WEIGHT + VOLUME_MA_WEIGHT);

            double availableBalance = exchange.getBalance().get(QUOTE_CURRENCY).getAvailableForTrade();
            double bettingSize = availableBalance * weightedMAScore;

            if(bettingSize > 0) {
                LOGGER.info("[---------------------BUY SIGNAL DETECTED----------------------------]");
                LOGGER.info("bettingSize {} = availableBalance {} * weightedMAScore {}", bettingSize, availableBalance, weightedMAScore);
                LOGGER.info("curPrice {} > {} (openPrice {} + k {} * prevRange {})",
                        curPrice ,
                        curTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange() ,
                        curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());

                double amount = bettingSize / buyPrice;
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
            }
        } else {
            LOGGER.info("[---------------------NO BUY SIGNAL DETECTED----------------------------]");
            LOGGER.info("curPrice {} < {} (openPrice {} + k {} * prevRange {})",
                    curPrice ,
                    curTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange() ,
                    curTradingWindow.getOpenPrice(), k, lookbehindTradingWindows.get(0).getRange());
        }
    }

    private void sellAtMarketPrice() {
        DateTime now = DateTime.now(UTC);
        long curTimeStamp = now.getMillis();
        TradingWindow curTradingWindow = repository.getCurrentTradingWindow(curTimeStamp);

        if(curTradingWindow.getBuyOrder() != null) {
//            OrderInfo buyOrder = curTradingWindow.getBuyOrder();
            LOGGER.info("-----------------------------SELL IT!!!!!!----------------------");
            OrderInfo placedBuyOrder = curTradingWindow.getBuyOrder();
            double bestBid = orderBookCache.getBestBid();
            double sellPrice = bestBid * (1 - LIMIT_ORDER_PREMIUM/100.0D);
            double sellAmount = placedBuyOrder.getAmountExecuted();
            OrderInfo sellOrder = new OrderInfo("BINANCE", SYMBOL, OrderSide.SELL, sellPrice, sellAmount);
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

        if(curTradingWindow.getSellOrder() != null) {
            LOGGER.info("-----------------------------SELL IT!!!!!!----------------------");
            OrderInfo placedSellOrder = curTradingWindow.getSellOrder();
            double bestAsk = orderBookCache.getBestAsk();
            double buyPrice = bestAsk * (1 + LIMIT_ORDER_PREMIUM/100.0D);
            double buyAmount = placedSellOrder.getAmountExecuted();
            OrderInfo buyOrder = new OrderInfo("BINANCE", SYMBOL, OrderSide.BUY, buyPrice, buyAmount);
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
