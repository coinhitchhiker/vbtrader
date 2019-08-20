package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.trade.LongTradingEngine;
import com.coinhitchhiker.vbtrader.common.trade.ShortTradingEngine;
import com.coinhitchhiker.vbtrader.simulator.db.SimulatorDAO;
import com.google.gson.Gson;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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
    private String QUOTE_CURRRENCY;

    private SimulatorRepositoryImpl repository;
    private SimulatorExchange exchange;
    private SimulatorOrderBookCache orderBookCache;
    private SimulatorDAO simulatorDAO;

    private final double SLIPPAGE = 0.01/100.0D;
    private static final int MA_MIN = 3;
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
                     String mode,
                     String QUOTE_CURRENCY)  {

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
        this.QUOTE_CURRRENCY = QUOTE_CURRENCY;
    }

    public void init() {
        this.repository = new SimulatorRepositoryImpl(EXCHANGE, SYMBOL, SIMUL_START, SIMUL_END, TRADING_WINDOW_SIZE_IN_MIN, TS_TRIGGER_PCT, TS_PCT);
        this.orderBookCache = new SimulatorOrderBookCache();
        this.exchange = new SimulatorExchange(this.repository, this.orderBookCache, SLIPPAGE);
    }

    public void runSimul() throws IOException {

        if(this.repository == null) throw new RuntimeException("call init() first");
        if(this.exchange == null) throw new RuntimeException("call init() first");
        if(this.orderBookCache == null) throw new RuntimeException("call init() first");

        TradingEngine tradingEngine = null;
        if(this.MODE.equals("LONG")) {
            tradingEngine = new LongTradingEngine(repository, exchange, orderBookCache, TRADING_WINDOW_LOOK_BEHIND, SYMBOL, QUOTE_CURRRENCY, 0.0, EXCHANGE, FEE_RATE, true);
        } else {
            tradingEngine = new ShortTradingEngine(repository, exchange, orderBookCache, TRADING_WINDOW_LOOK_BEHIND, SYMBOL, QUOTE_CURRRENCY, 0.0, EXCHANGE, FEE_RATE, true);
        }

        long curTimestamp = 0; double curPrice = 0;

        for(TradingWindow tradingWindow : this.repository.getTradingWindows()) {
            for(Candle candle : tradingWindow.getCandles()) {
                // trade open price point
                curTimestamp = candle.getOpenTime(); curPrice = candle.getOpenPrice();
                tradeWith(curPrice, curTimestamp, tradingEngine);

                long timeDiff = candle.getCloseTime() - candle.getOpenTime();

                // assume low price point comes first
                curTimestamp = timeDiff / 3 + candle.getOpenTime(); curPrice = candle.getLowPrice();
                tradeWith(curPrice, curTimestamp, tradingEngine);

                // high  price point comes next
                curTimestamp = 2*timeDiff / 3 + candle.getOpenTime(); curPrice = candle.getHighPrice();
                tradeWith(curPrice, curTimestamp, tradingEngine);

                // trade close price
                curTimestamp = candle.getCloseTime(); curPrice = candle.getClosePrice();
                tradeWith(curPrice, curTimestamp, tradingEngine);
            }
        }
    }

    private void tradeWith(double curPrice, long curTimestamp, TradingEngine tradingEngine) {
        this.exchange.setTimestampAndPrice(curTimestamp, curPrice);
        TradeResult tradeResult = tradingEngine.run(curPrice, curTimestamp);
        if(tradeResult != null) {
            Balance balance = this.exchange.getBalance().get(QUOTE_CURRRENCY);
            balance.setAvailableForTrade(balance.getAvailableForTrade() + tradeResult.getNetProfit());
            this.exchange.getBalance().put(QUOTE_CURRRENCY, balance);

            if(tradeResult.getNetProfit() > 0) {
                win++;
                this.profit += tradeResult.getNetProfit();
            } else {
                lose++;
                this.loss += tradeResult.getNetProfit();
            }
        }
    }

    public SimulResult collectSimulResult() {
        LOGGER.info("----------------------------------------------------------------");
        LOGGER.info("SIMUL_START {}", new DateTime(SIMUL_START, DateTimeZone.UTC));
        LOGGER.info("SIMUL_END {}", new DateTime(SIMUL_END, DateTimeZone.UTC));
        LOGGER.info("MODE {}", MODE);
        LOGGER.info("START_USD_BALANCE {}", exchange.getSTART_BALANCE());
        LOGGER.info("END_USD_BALANCE {}", exchange.getBalance().get(QUOTE_CURRRENCY).getAvailableForTrade());
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

        System.out.println((-1)*exchange.getBalance().get(QUOTE_CURRRENCY).getAvailableForTrade());

        SimulResult result = new SimulResult();

        Integer id = simulatorDAO.getPeriodId(this.EXCHANGE,
                this.SYMBOL,
                DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_START, DateTimeZone.UTC)),
                DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_END, DateTimeZone.UTC)));

        result.setPeriodId(id);
        result.setVOLUME_MA_WEIGHT(this.VOLUME_MA_WEIGHT);
        result.setTRADING_WINDOW_SIZE_IN_MIN(this.TRADING_WINDOW_SIZE_IN_MIN);
        result.setTRADING_WINDOW_LOOK_BEHIND(this.TRADING_WINDOW_LOOK_BEHIND);
        result.setSTART_USD_BALANCE(exchange.getSTART_BALANCE());
        result.setSLIPPAGE(this.SLIPPAGE);
        result.setSIMUL_START(DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_START, DateTimeZone.UTC)));
        result.setSIMUL_END(DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_END, DateTimeZone.UTC)));
        result.setPRICE_MA_WEIGHT(this.PRICE_MA_WEIGHT);
        result.setMA_MIN(this.MA_MIN);
        result.setEND_USD_BALANCE(exchange.getBalance().get(QUOTE_CURRRENCY).getAvailableForTrade());
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

        if(bestResult != null && Math.round(bestResult) >= Math.round(exchange.getBalance().get(QUOTE_CURRRENCY).getAvailableForTrade())) {
            LOGGER.info("------------------------------------------------------");
            LOGGER.info("No logging will happen. Current bestResult {} >= new simulResult {}", bestResult, exchange.getBalance().get(QUOTE_CURRRENCY).getAvailableForTrade());
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
