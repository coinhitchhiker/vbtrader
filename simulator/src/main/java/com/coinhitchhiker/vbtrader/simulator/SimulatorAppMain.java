package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.Repository;
import com.coinhitchhiker.vbtrader.common.TradingWindow;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class SimulatorAppMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorAppMain.class);

    private static final int TRADING_WINDOW_SIZE_IN_MIN = 1440;
    private static final int TRADING_WINDOW_LOOK_BEHIND = 20;
    private static final int MA_MIN = 3;
    private static final long SIMUL_START = new DateTime(2019,3,1,0,1, DateTimeZone.UTC).getMillis();
    private static final long SIMUL_END = new DateTime(2019,8,1,23,55,DateTimeZone.UTC).getMillis();
    private static final String testDataFile = "./BTCUSDT-0301-0801.txt";
    private static double USD_BALANCE = 10000.0D;
    private static final double SLIPPAGE = 0.1/100.0D;

    private Repository repository;

    public static void main(String... args) {
        SimulatorAppMain app = new SimulatorAppMain();
        try {
            app.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() throws IOException {

        this.repository = new SimulatorRepositoryImpl(testDataFile, TRADING_WINDOW_SIZE_IN_MIN);

        int win = 0, lose = 0;
        double profit = 0.0D, loss = 0.0D;

        // advance by minute
        for(long curTimestamp = SIMUL_START; curTimestamp < SIMUL_END; curTimestamp+=60_000) {
            List<TradingWindow> lookbehindTradingWindows = repository.getLastNTradingWindow(TRADING_WINDOW_LOOK_BEHIND, TRADING_WINDOW_SIZE_IN_MIN, curTimestamp);
            if(lookbehindTradingWindows.size() < TRADING_WINDOW_LOOK_BEHIND) {
                continue;
            }

            TradingWindow curTradingWindow = repository.getCurrentTradingWindow(curTimestamp);
            if(curTradingWindow == null) {
                outputSimulationResult();
                return;
            }
            double k = lookbehindTradingWindows.stream().mapToDouble(TradingWindow::getNoiseRatio).average().getAsDouble();

            double hypotheticalCurPrice = curTradingWindow.getHighPrice();
            double hypotheticalBuyPrice = (curTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange()) * (1+SLIPPAGE);

            // buy signal!
            if(curTradingWindow.isBuySignal(hypotheticalCurPrice, k, lookbehindTradingWindows.get(0))) {
                double maScore = this.maScore(lookbehindTradingWindows, hypotheticalCurPrice);
                double bettingSize = USD_BALANCE * maScore;

                LOGGER.info("curTime {}, hypotheticalCurPrice {}, hypotheticalBreakoutPrice {}, closePrice {}, k {}, maScore {}, bettingSize {}"
                        , new DateTime(curTimestamp).withZone(DateTimeZone.UTC).toString()
                        , hypotheticalCurPrice , hypotheticalBuyPrice, curTradingWindow.getClosePrice(), k , maScore, bettingSize);

                if(bettingSize > 0) {
                    double amount = bettingSize / hypotheticalBuyPrice;
                    double hypotheticalSellPrice = curTradingWindow.getClosePrice() * (1-SLIPPAGE);
                    double diff = (hypotheticalSellPrice - hypotheticalBuyPrice) * amount;

                    USD_BALANCE += diff;

                    if(hypotheticalSellPrice > hypotheticalBuyPrice) {
                        profit += diff; win++;
                    } else {
                        loss += diff; lose++;
                    }
                    LOGGER.info("Balance {} P/L {}/{}, Win(%) {}", USD_BALANCE, profit, loss, (win*1.0 / (win+lose)) * 100.0);

                    // advance to the next trading window
                    curTimestamp = curTradingWindow.getEndTimeStamp() + 60_000L;
                }
            }
        }
    }

    private void outputSimulationResult() {
        LOGGER.info("Simulation has ended");
    }

    private double maScore(List<TradingWindow> lookbehindTradingWindows, double curPrice) {
        int aboveMaCnt = 0;
        for(int i = MA_MIN; i <= TRADING_WINDOW_LOOK_BEHIND; i++) {
            double closePriceSum = 0.0D;
            for(int j = 0; j < i; j++) {
                double closePrice = lookbehindTradingWindows.get(j).getClosePrice();
                closePriceSum += closePrice;
            }
            if(curPrice > (closePriceSum / i)) {
                aboveMaCnt++;
            }
        }
        return (aboveMaCnt * 1.0) / (TRADING_WINDOW_LOOK_BEHIND - MA_MIN + 1);
    }
}
