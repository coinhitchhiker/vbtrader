package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.*;
import com.coinhitchhiker.vbtrader.simulator.db.SimulatorDAO;
import com.google.gson.Gson;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.joda.time.DateTimeZone.UTC;

public class Simulator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Simulator.class);

    private int TRADING_WINDOW_SIZE_IN_MIN;
    private int TRADING_WINDOW_LOOK_BEHIND;
    private double PRICE_MA_WEIGHT;
    private double VOLUME_MA_WEIGHT;
    private long SIMUL_START;
    private long SIMUL_END;
    private String EXCHANGE;
    private String SYMBOL;
    private double TS_TRIGGER_PCT;
    private double TS_PCT;
    private String MODE;

    private Repository repository;
    private Exchange exchange;
    private SimulatorDAO simulatorDAO;

    private final double SLIPPAGE = 0.01/100.0D;
    private static final int MA_MIN = 3;
    private double USD_BALANCE = 10000.0D;
    private double START_USD_BALANCE = 10000.0D;
    private double FEE_RATE = 0.045;

    private int win = 0;
    private int lose = 0;

    private double profit = 0.0D, loss = 0.0D;

    public Simulator(SimulatorDAO simulatorDAO,
                     int TRADING_WINDOW_SIZE_IN_MIN,
                     int TRADING_WINDOW_LOOK_BEHIND,
                     double PRICE_MA_WEIGHT,
                     double VOLUME_MA_WEIGHT,
                     long SIMUL_START,
                     long SIMUL_END,
                     String EXCHANGE,
                     String SYMBOL,
                     double TS_TRIGGER_PCT,
                     double TS_PCT,
                     String mode)  {

        this.simulatorDAO = simulatorDAO;

        this.TRADING_WINDOW_SIZE_IN_MIN = TRADING_WINDOW_SIZE_IN_MIN;
        this.TRADING_WINDOW_LOOK_BEHIND = TRADING_WINDOW_LOOK_BEHIND;
        this.PRICE_MA_WEIGHT = PRICE_MA_WEIGHT;
        this.VOLUME_MA_WEIGHT = VOLUME_MA_WEIGHT;
        this.SIMUL_START = SIMUL_START;
        this.SIMUL_END = SIMUL_END;
        this.EXCHANGE = EXCHANGE;
        this.SYMBOL = SYMBOL;
        this.TS_TRIGGER_PCT = TS_TRIGGER_PCT;
        this.TS_PCT = TS_PCT;
        this.MODE = mode;
    }

    public void init() {
        this.repository = new SimulatorRepositoryImpl(EXCHANGE, SYMBOL, SIMUL_START, SIMUL_END, TRADING_WINDOW_SIZE_IN_MIN, TS_TRIGGER_PCT, TS_PCT);
        this.exchange = new SimulatorExchange(this.repository, SLIPPAGE);
    }

    public void runSimul() throws IOException {

        if(this.repository == null) throw new RuntimeException("call init() first");
        if(this.exchange == null) throw new RuntimeException("call init() first");

        long curTimeStamp = 0;
        for(curTimeStamp = SIMUL_START; curTimeStamp < SIMUL_END; curTimeStamp+=60_000) {  // advance by minute
            // let the simulExchange know current time
            ((SimulatorExchange)this.exchange).setCurrentTimestamp(curTimeStamp);

            TradingWindow curTradingWindow = repository.getCurrentTradingWindow(curTimeStamp);
            if(curTradingWindow == null) break;

            List<TradingWindow> lookbehindTradingWindows = repository.getLastNTradingWindow(TRADING_WINDOW_LOOK_BEHIND+1, curTimeStamp);
            if(lookbehindTradingWindows.size() < TRADING_WINDOW_LOOK_BEHIND+1) continue;

            if(curTimeStamp > curTradingWindow.getEndTimeStamp()) {
                sellAtMarketPrice(curTradingWindow);
                repository.refreshTradingWindows();
                continue;
            }

            double curPrice = exchange.getCurrentPrice(SYMBOL);
            double pvt = curTradingWindow.getPVT(curTimeStamp);
            System.out.println(String.format("%s,%f,%f,%f", new DateTime(curTimeStamp, UTC), curPrice, pvt, curTradingWindow.getCurTradingWindowVol(curTimeStamp)));

            if(this.MODE.equals("LONG") &&
                curTradingWindow.getBuyOrder() != null &&
                curTradingWindow.getTrailingStopPrice() > curPrice) {
                LOGGER.info("---------------LONG TRAILING STOP HIT------------------------");
                LOGGER.info("trailingStopPrice {} > curPrice {}", curTradingWindow.getTrailingStopPrice(), curPrice);
                // market sell
                sellAtMarketPrice(curTradingWindow);
                curTradingWindow.clearOutOrders();
                continue;
            }

            if(this.MODE.equals("SHORT") &&
                curTradingWindow.getSellOrder() != null &&
                (0 < curTradingWindow.getTrailingStopPrice() && curTradingWindow.getTrailingStopPrice() < curPrice)) {
                LOGGER.info("---------------SHORT TRAILING STOP HIT------------------------");
                LOGGER.info("trailingStopPrice {} < curPrice {}", curTradingWindow.getTrailingStopPrice(), curPrice);
                // market sell
                sellAtMarketPrice(curTradingWindow);
                curTradingWindow.clearOutOrders();
                continue;
            }

            if(curTradingWindow.getBuyOrder() != null || curTradingWindow.getSellOrder() != null) continue;

            double k = VolatilityBreakoutRules.getKValue(lookbehindTradingWindows);

             // sell signal!
            if(this.MODE.equals("SHORT") && curTradingWindow.isSellSignal(curPrice, k, lookbehindTradingWindows.get(0))) {
                double volume = curTradingWindow.getCurTradingWindowVol(curTimeStamp);
                double sellPrice = curPrice;

                double priceMAScore = VolatilityBreakoutRules.getPriceMAScore(lookbehindTradingWindows, curPrice, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
                double volumeMAScore = VolatilityBreakoutRules.getVolumeMAScore_conservative(lookbehindTradingWindows, volume, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
                double weightedMAScore = (PRICE_MA_WEIGHT*priceMAScore + VOLUME_MA_WEIGHT*volumeMAScore) / (PRICE_MA_WEIGHT + VOLUME_MA_WEIGHT);

                double bettingSize = USD_BALANCE * weightedMAScore;

                if(bettingSize > 0) {
                    LOGGER.info("[---------------------SELL SIGNAL DETECTED----------------------------]");
                    double amount = bettingSize / sellPrice;
                    OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, sellPrice, amount);
                    OrderInfo placedSellOrder = exchange.placeOrder(sellOrder);

                    curTradingWindow.setSellOrder(placedSellOrder);
                    LOGGER.info("[PLACED SELL ORDER] {}", placedSellOrder.toString());
                    LOGGER.debug("[PLACED SELL ORDER] liquidation expected at {} ", new DateTime(curTradingWindow.getEndTimeStamp(), UTC));
                    double sellFee = placedSellOrder.getAmountExecuted() * placedSellOrder.getPriceExecuted() * FEE_RATE / 100.0D;
                    curTradingWindow.setSellFee(sellFee);
                }
            }

            // buy signal!
            if(this.MODE.equals("LONG") && curTradingWindow.isBuySignal(curPrice, k, lookbehindTradingWindows.get(0))) {
                double buyPrice = curPrice;
                double priceMAScore = VolatilityBreakoutRules.getPriceMAScore(lookbehindTradingWindows, curPrice, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
                double volumeMAScore = VolatilityBreakoutRules.getVolumeMAScore_conservative(lookbehindTradingWindows,
                        curTradingWindow.getCurTradingWindowVol(curTimeStamp),
                        MA_MIN,
                        TRADING_WINDOW_LOOK_BEHIND);

                double weightedMAScore = (PRICE_MA_WEIGHT*priceMAScore + VOLUME_MA_WEIGHT*volumeMAScore) / (PRICE_MA_WEIGHT + VOLUME_MA_WEIGHT);

                double bettingSize = USD_BALANCE * weightedMAScore;

                if(bettingSize > 0) {
                    LOGGER.info("[---------------------BUY SIGNAL DETECTED----------------------------]");
                    double amount = bettingSize / buyPrice;
                    OrderInfo buyOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.BUY, buyPrice, amount);
                    OrderInfo placedBuyOrder = exchange.placeOrder(buyOrder);

                    curTradingWindow.setBuyOrder(placedBuyOrder);
                    LOGGER.info("[PLACED BUY ORDER] {}", placedBuyOrder.toString());
                    LOGGER.debug("[PLACED BUY ORDER] liquidation expected at {} ", new DateTime(curTradingWindow.getEndTimeStamp(), UTC));
                    double buyFee = placedBuyOrder.getAmountExecuted() * placedBuyOrder.getPriceExecuted() * FEE_RATE / 100.0D;
                    curTradingWindow.setBuyFee(buyFee);
                }
            }
        }
    }

    private void sellAtMarketPrice(TradingWindow curTradingWindow) {

        if(curTradingWindow.getBuyOrder() == null && curTradingWindow.getSellOrder() == null) return;

        if(curTradingWindow.getBuyOrder() != null) {
            OrderInfo placedBuyOrder = curTradingWindow.getBuyOrder();
            double sellPrice = curTradingWindow.getTrailingStopPrice() != 0.0 ? curTradingWindow.getTrailingStopPrice() : curTradingWindow.getClosePrice();
            double sellAmount = placedBuyOrder.getAmountExecuted();
            OrderInfo sellOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.SELL, sellPrice, sellAmount);
            OrderInfo placedSellOrder = exchange.placeOrder(sellOrder);
            LOGGER.info("[PLACED SELL ORDER] {}", placedSellOrder.toString());

            double buyFee = curTradingWindow.getBuyFee();
            double sellFee = placedSellOrder.getAmountExecuted() * placedSellOrder.getPriceExecuted() * FEE_RATE / 100.0D;
            double profit = (placedSellOrder.getPriceExecuted() - placedBuyOrder.getPriceExecuted()) * sellAmount;
            double netProfit =  profit - (buyFee + sellFee);

            curTradingWindow.setSellFee(sellFee);
            curTradingWindow.setProfit(profit);
            curTradingWindow.setNetProfit(netProfit);
            curTradingWindow.setSellOrder(placedSellOrder);

            USD_BALANCE += netProfit;

            if(netProfit > 0) {
                win++;
                this.profit += netProfit;
            } else {
                lose++;
                this.loss += netProfit;
            }

            LOGGER.info("[{}] TotalBalance={} netProfit={}, time={}, profit={}, fee={}, sellPrice={} buyPrice={}, amount={},  ",
                    netProfit >= 0 ? "PROFIT" : "LOSS",
                    String.format("%.2f", USD_BALANCE),
                    String.format("%.2f", netProfit),
                    new DateTime(placedSellOrder.getExecTimestamp(), UTC),
                    String.format("%.2f", profit),
                    String.format("%.2f", buyFee + sellFee),
                    placedSellOrder.getPriceExecuted(),
                    placedBuyOrder.getPriceExecuted(),
                    placedBuyOrder.getAmountExecuted());

            curTradingWindow.setBuyOrder(null);
            curTradingWindow.setSellOrder(null);
        }

        if(curTradingWindow.getSellOrder() != null) {
            OrderInfo placedSellOrder = curTradingWindow.getSellOrder();
            double buyPrice = curTradingWindow.getTrailingStopPrice() != 0.0 ? curTradingWindow.getTrailingStopPrice() : curTradingWindow.getClosePrice();
            double buyAmount = placedSellOrder.getAmountExecuted();
            OrderInfo buyOrder = new OrderInfo(EXCHANGE, SYMBOL, OrderSide.BUY, buyPrice, buyAmount);
            OrderInfo placedBuyOrder = exchange.placeOrder(buyOrder);
            LOGGER.info("[PLACED BUY ORDER] {}", placedBuyOrder.toString());

            double sellFee = curTradingWindow.getSellFee();
            double buyFee = placedBuyOrder.getAmountExecuted() * placedBuyOrder.getPriceExecuted() * FEE_RATE / 100.0D;
            double profit = (placedSellOrder.getPriceExecuted() - placedBuyOrder.getPriceExecuted()) * buyAmount;
            double netProfit =  profit - (buyFee + sellFee);

            curTradingWindow.setBuyFee(buyFee);
            curTradingWindow.setProfit(profit);
            curTradingWindow.setNetProfit(netProfit);
            curTradingWindow.setBuyOrder(placedBuyOrder);

            USD_BALANCE += netProfit;

            if(netProfit > 0) {
                win++;
                this.profit += netProfit;
            } else {
                lose++;
                this.loss += netProfit;
            }

            LOGGER.info("[{}] TotalBalance={} netProfit={}, time={}, profit={}, fee={}, sellPrice={} buyPrice={}, amount={},  ",
                    netProfit >= 0 ? "PROFIT" : "LOSS",
                    String.format("%.2f", USD_BALANCE),
                    String.format("%.2f", netProfit),
                    new DateTime(placedBuyOrder.getExecTimestamp(), UTC),
                    String.format("%.2f", profit),
                    String.format("%.2f", buyFee + sellFee),
                    placedSellOrder.getPriceExecuted(),
                    placedBuyOrder.getPriceExecuted(),
                    placedBuyOrder.getAmountExecuted());

            curTradingWindow.setBuyOrder(null);
            curTradingWindow.setSellOrder(null);
        }


        LOGGER.info("-----------------------------------------------------------------------------------------");
        repository.logCompleteTradingWindow(curTradingWindow);
    }

    public SimulResult collectSimulResult() {
        LOGGER.info("----------------------------------------------------------------");
        LOGGER.info("SIMUL_START {}", new DateTime(SIMUL_START, DateTimeZone.UTC));
        LOGGER.info("SIMUL_END {}", new DateTime(SIMUL_END, DateTimeZone.UTC));
        LOGGER.info("MODE {}", MODE);
        LOGGER.info("START_USD_BALANCE {}", START_USD_BALANCE);
        LOGGER.info("END_USD_BALANCE {}", USD_BALANCE);
        LOGGER.info("WINNING RATE {} (w {} / l {})", String.format("%.2f", (win*1.0 / (win+lose)) * 100.0), win, lose);
        LOGGER.info("P/L RATIO {} (P {} / L {})", String.format("%.2f", Math.abs(profit / loss)), String.format("%.2f",profit), String.format("%.2f",loss));
        LOGGER.info("TRADING_WINDOW_SIZE_IN_MIN {}", TRADING_WINDOW_SIZE_IN_MIN);
        LOGGER.info("TRADING_WINDOW_LOOK_BEHIND {}", TRADING_WINDOW_LOOK_BEHIND);
        LOGGER.info("PRICE_MA_WEIGHT {}", PRICE_MA_WEIGHT);
        LOGGER.info("VOLUME_MA_WEIGHT {}", VOLUME_MA_WEIGHT);
        LOGGER.info("MA_MIN {}", MA_MIN);
        LOGGER.info("SLIPPAGE {}", SLIPPAGE);
        LOGGER.info("TS_TRIGGER_PCT {}", TS_TRIGGER_PCT);
        LOGGER.info("TS_PCT {}", TS_PCT);
        LOGGER.info("----------------------------------------------------------------");

        System.out.println((-1)*this.USD_BALANCE);

        SimulResult result = new SimulResult();

        Integer id = simulatorDAO.getPeriodId(this.EXCHANGE,
                this.SYMBOL,
                DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_START, DateTimeZone.UTC)),
                DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_END, DateTimeZone.UTC)));

        result.setPeriodId(id);
        result.setVOLUME_MA_WEIGHT(this.VOLUME_MA_WEIGHT);
        result.setTRADING_WINDOW_SIZE_IN_MIN(this.TRADING_WINDOW_SIZE_IN_MIN);
        result.setTRADING_WINDOW_LOOK_BEHIND(this.TRADING_WINDOW_LOOK_BEHIND);
        result.setSTART_USD_BALANCE(this.START_USD_BALANCE);
        result.setSLIPPAGE(this.SLIPPAGE);
        result.setSIMUL_START(DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_START, DateTimeZone.UTC)));
        result.setSIMUL_END(DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_END, DateTimeZone.UTC)));
        result.setPRICE_MA_WEIGHT(this.PRICE_MA_WEIGHT);
        result.setMA_MIN(this.MA_MIN);
        result.setEND_USD_BALANCE(this.USD_BALANCE);
        result.setWINNING_RATE((win*1.0 / (win+lose)) * 100.0);
        result.setTS_TRIGGER_PCT(TS_TRIGGER_PCT);
        result.setTS_PCT(TS_PCT);
        result.setMODE(MODE);

        return result;
    }

    public void logSimulResult(SimulResult result) {

        if(result.getPeriodId() == null) {
            LOGGER.info("------------------------------------------------------");
            LOGGER.info("periodId was not found from DB. No result logging will happen...");
            LOGGER.info("------------------------------------------------------");
            return;
        }

        Double bestResult = simulatorDAO.getBestResult(result.getPeriodId());

        if(bestResult != null && Math.round(bestResult) >= Math.round(this.USD_BALANCE)) {
            LOGGER.info("------------------------------------------------------");
            LOGGER.info("No logging will happen. Current bestResult {} >= new simulResult {}", bestResult, this.USD_BALANCE);
            LOGGER.info("------------------------------------------------------");
        }

        Gson gson = new Gson();
        String json = gson.toJson(result);
        try {
            simulatorDAO.insertSimulResult(result.getPeriodId(), json);
        } catch(Exception e) {
            LOGGER.error("Exception occurred when logging result", e);
        }
    }

    public void logValidationResult(TopSimulResult topSimulResult, SimulResult validationResult) {
        Gson gson = new Gson();
        String s = gson.toJson(validationResult);
        simulatorDAO.insertValidationResult(topSimulResult.getPeriodId(), topSimulResult.getSimulResultId(), s);
    }
}
