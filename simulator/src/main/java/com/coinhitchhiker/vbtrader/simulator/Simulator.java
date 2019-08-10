package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.Repository;
import com.coinhitchhiker.vbtrader.common.TradingWindow;
import com.coinhitchhiker.vbtrader.common.VolatilityBreakoutRules;
import com.coinhitchhiker.vbtrader.simulator.db.SimulatorDAO;
import com.google.gson.Gson;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class Simulator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Simulator.class);

    private String DATA_FILE;
    private int TRADING_WINDOW_SIZE_IN_MIN;
    private int TRADING_WINDOW_LOOK_BEHIND;
    private double PRICE_MA_WEIGHT;
    private double VOLUME_MA_WEIGHT;
    private long SIMUL_START;
    private long SIMUL_END;
    private String EXCHANGE;
    private String SYMBOL;

    private Repository repository;
    private SimulatorDAO simulatorDAO;

    private final double SLIPPAGE = 0.1/100.0D;
    private final double H_VOLUME_WEIGHT = 0.677D;
    private static final int MA_MIN = 3;
    private double USD_BALANCE = 10000.0D;
    private double START_USD_BALANCE = 10000.0D;

    public Simulator(SimulatorDAO simulatorDAO,
                     String DATA_FILE,
                     int TRADING_WINDOW_SIZE_IN_MIN,
                     int TRADING_WINDOW_LOOK_BEHIND,
                     double PRICE_MA_WEIGHT,
                     double VOLUME_MA_WEIGHT,
                     long SIMUL_START,
                     long SIMUL_END,
                     String EXCHANGE,
                     String SYMBOL)  {

        this.simulatorDAO = simulatorDAO;
        this.DATA_FILE = DATA_FILE;
        this.TRADING_WINDOW_SIZE_IN_MIN = TRADING_WINDOW_SIZE_IN_MIN;
        this.TRADING_WINDOW_LOOK_BEHIND = TRADING_WINDOW_LOOK_BEHIND;
        this.PRICE_MA_WEIGHT = PRICE_MA_WEIGHT;
        this.VOLUME_MA_WEIGHT = VOLUME_MA_WEIGHT;
        this.SIMUL_START = SIMUL_START;
        this.SIMUL_END = SIMUL_END;
        this.EXCHANGE = EXCHANGE;
        this.SYMBOL = SYMBOL;

    }

    public void runSimul() throws IOException {

        if(this.DATA_FILE == null) {
            this.repository = new SimulatorRepositoryImpl(SYMBOL, SIMUL_START, SIMUL_END, TRADING_WINDOW_SIZE_IN_MIN);
        } else {
            this.repository = new SimulatorRepositoryImpl(DATA_FILE, TRADING_WINDOW_SIZE_IN_MIN);
        }

        int win = 0, lose = 0;
        double profit = 0.0D, loss = 0.0D;

        // advance by minute
        long curTimeStamp = 0;
        for(curTimeStamp = SIMUL_START; curTimeStamp < SIMUL_END; curTimeStamp+=60_000) {
            List<TradingWindow> lookbehindTradingWindows = repository.getLastNTradingWindow(TRADING_WINDOW_LOOK_BEHIND, curTimeStamp);
            if(lookbehindTradingWindows.size() < TRADING_WINDOW_LOOK_BEHIND) {
                continue;
            }

            TradingWindow curTradingWindow = repository.getCurrentTradingWindow(curTimeStamp);
            if(curTradingWindow == null) break;

            double k = lookbehindTradingWindows.stream().mapToDouble(TradingWindow::getNoiseRatio).average().getAsDouble();
            double hypotheticalCurPrice = curTradingWindow.getHighPrice();
            // assume that volume at the hypotheticalCurPrice is 2/3 of total vol of curwindow
            double hypotheticalVolume = curTradingWindow.getVolume() * H_VOLUME_WEIGHT;
            double hypotheticalBuyPrice = (curTradingWindow.getOpenPrice() + k * lookbehindTradingWindows.get(0).getRange()) * (1+SLIPPAGE);

            // buy signal!
            if(curTradingWindow.isBuySignal(hypotheticalCurPrice, k, lookbehindTradingWindows.get(0))) {
                double priceMAScore = VolatilityBreakoutRules.getPriceMAScore(lookbehindTradingWindows, hypotheticalCurPrice, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
                double volumeMAScore = VolatilityBreakoutRules.getVolumeMAScore(lookbehindTradingWindows, hypotheticalVolume, MA_MIN, TRADING_WINDOW_LOOK_BEHIND);
                double weightedMAScore = (PRICE_MA_WEIGHT*priceMAScore + VOLUME_MA_WEIGHT*volumeMAScore) / (PRICE_MA_WEIGHT + VOLUME_MA_WEIGHT);

                double bettingSize = USD_BALANCE * weightedMAScore;

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

                    LOGGER.info("Balance {} P/L {}/{} Win-Lose {}/{}/{}% curTime {}, h-CurPrice {}, h-BuyPrice {}, h-SellPrice {}, k {}, weightedMAScore {}, bettingSize {}"
                            , String.format("%.2f", USD_BALANCE)
                            , String.format("%.2f", profit)
                            , loss
                            , win
                            , lose
                            , String.format("%.2f", (win*1.0 / (win+lose)) * 100.0)
                            , new DateTime(curTimeStamp).withZone(DateTimeZone.UTC).toString()
                            , String.format("%.2f", hypotheticalCurPrice)
                            , String.format("%.2f", hypotheticalBuyPrice)
                            , String.format("%.2f", curTradingWindow.getClosePrice())
                            , String.format("%.2f", k)
                            , String.format("%.2f", weightedMAScore)
                            , String.format("%.2f", bettingSize)
                    );

                    // advance to the next trading window
                    curTimeStamp = curTradingWindow.getEndTimeStamp() + 60_000L;
                }
            }
        }
    }

    public SimulResult collectSimulResult() {
        LOGGER.info("----------------------------------------------------------------");
        LOGGER.info("SIMUL_START {}", new DateTime(SIMUL_START, DateTimeZone.UTC));
        LOGGER.info("SIMUL_END {}", new DateTime(SIMUL_END, DateTimeZone.UTC));
        LOGGER.info("MA_MIN {}", MA_MIN);
        LOGGER.info("TRADING_WINDOW_SIZE_IN_MIN {}", TRADING_WINDOW_SIZE_IN_MIN);
        LOGGER.info("TRADING_WINDOW_LOOK_BEHIND {}", TRADING_WINDOW_LOOK_BEHIND);
        LOGGER.info("DATA_FILE {}", DATA_FILE);
        LOGGER.info("START_USD_BALANCE {}", START_USD_BALANCE);
        LOGGER.info("END_USD_BALANCE {}", USD_BALANCE);
        LOGGER.info("PRICE_MA_WEIGHT {}", PRICE_MA_WEIGHT);
        LOGGER.info("VOLUME_MA_WEIGHT {}", VOLUME_MA_WEIGHT);
        LOGGER.info("SLIPPAGE {}", SLIPPAGE);
        LOGGER.info("H_VOLUME_WEIGHT {}", H_VOLUME_WEIGHT);
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
        result.setH_VOLUME_WEIGHT(this.H_VOLUME_WEIGHT);
        result.setEND_USD_BALANCE(this.USD_BALANCE);

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
