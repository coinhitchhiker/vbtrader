package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.strategy.vb.VBLongTradingEngine;
import com.coinhitchhiker.vbtrader.simulator.db.SimulatorDAO;
import com.google.gson.Gson;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Simulator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Simulator.class);

    private final long SIMUL_START;
    private final long SIMUL_END;
    private final String EXCHANGE;
    private final String SYMBOL;
    private final double TS_TRIGGER_PCT;
    private final double TS_PCT;
    private final String MODE;
    private final String QUOTE_CURRRENCY;
    private final String STRATEGY;
    private final Map<String, Double> STRATEGY_PARAMS;

    private SimulatorRepositoryImpl repository;
    private SimulatorExchange exchange;
    private SimulatorOrderBookCache orderBookCache;
    private SimulatorDAO simulatorDAO;
    private TradingEngine tradingEngine;

    private final double SLIPPAGE = 0.01/100.0D;
    private static final int MA_MIN = 3;
    private double FEE_RATE = 0.045;

    private int win = 0;
    private int lose = 0;

    private double profit = 0.0D, loss = 0.0D;

    public Simulator(SimulatorDAO simulatorDAO,
                     long SIMUL_START,
                     long SIMUL_END,
                     String EXCHANGE,
                     String SYMBOL,
                     double TS_TRIGGER_PCT,
                     double TS_PCT,
                     String mode,
                     String QUOTE_CURRENCY,
                     String strategy,
                     Map<String, Double> strategyParams)  {

        this.simulatorDAO = simulatorDAO;
        this.SIMUL_START = SIMUL_START;
        this.SIMUL_END = SIMUL_END;
        this.EXCHANGE = EXCHANGE;
        this.SYMBOL = SYMBOL;
        this.TS_TRIGGER_PCT = TS_TRIGGER_PCT;
        this.TS_PCT = TS_PCT;
        this.MODE = mode;
        this.QUOTE_CURRRENCY = QUOTE_CURRENCY;
        this.STRATEGY = strategy;
        this.STRATEGY_PARAMS = strategyParams;
    }

    public void init() {
        this.repository = new SimulatorRepositoryImpl(EXCHANGE, SYMBOL, SIMUL_START, SIMUL_END, simulatorDAO);
        this.orderBookCache = new SimulatorOrderBookCache();
        this.exchange = new SimulatorExchange(this.repository, this.orderBookCache, SLIPPAGE);
        this.tradingEngine = createTradingEngine(this.STRATEGY_PARAMS);
        ((SimulatorExchange)this.exchange).setTradingEngine(this.tradingEngine);

        if(this.STRATEGY.equals("VB")) {

        } else if(this.STRATEGY.equals("PVTOBV")) {

        } else {
            throw new RuntimeException("Unknown startegy was given: " + this.STRATEGY);
        }
    }

    private TradingEngine createTradingEngine(Map<String, Double> strategyParams) {
        TradingEngine tradingEngine = null;
        if(this.MODE.equals("LONG")) {
            tradingEngine = new VBLongTradingEngine(repository,
                    exchange,
                    orderBookCache,
                    strategyParams.get(CmdLine.TRADING_WINDOW_LOOK_BEHIND).intValue(),
                    strategyParams.get(CmdLine.TRADING_WINDOW_SIZE_IN_MIN).intValue(),
                    strategyParams.get(CmdLine.PRICE_MA_WEIGHT),
                    strategyParams.get(CmdLine.VOLUME_MA_WEIGHT),
                    SYMBOL,
                    QUOTE_CURRRENCY,
                    0.0,
                    EXCHANGE,
                    FEE_RATE,
                    true,
                    true,
                    TS_TRIGGER_PCT,
                    TS_PCT);
        } else {
            throw new UnsupportedOperationException();
        }

        return tradingEngine;
    }

    public void runSimul() throws IOException {

        if(this.repository == null) throw new RuntimeException("call init() first");
        if(this.exchange == null) throw new RuntimeException("call init() first");
        if(this.orderBookCache == null) throw new RuntimeException("call init() first");
        if(this.tradingEngine == null) throw new RuntimeException("call init() first");

        List<Candle> allCandles = this.repository.getCandles(SYMBOL, SIMUL_START, SIMUL_END);

        long curTimestamp = 0; double curPrice = 0;

        for(Candle candle : allCandles) {
            // trade open price point
            curTimestamp = candle.getOpenTime(); curPrice = candle.getOpenPrice();
            tradeWith(curPrice, curTimestamp, tradingEngine);

            long timeDiff = candle.getCloseTime() - candle.getOpenTime();

            // high  price point comes first
            curTimestamp = timeDiff / 3 + candle.getOpenTime(); curPrice = candle.getHighPrice();
            tradeWith(curPrice, curTimestamp, tradingEngine);

            // assume low price point comes second
            curTimestamp = 2*timeDiff / 3 + candle.getOpenTime(); curPrice = candle.getLowPrice();
            tradeWith(curPrice, curTimestamp, tradingEngine);

            // trade close price
            curTimestamp = candle.getCloseTime(); curPrice = candle.getClosePrice();
            tradeWith(curPrice, curTimestamp, tradingEngine);
        }
    }

    private void tradeWith(double curPrice, long curTimestamp, TradingEngine tradingEngine) {
        this.exchange.setTimestampAndPrice(curTimestamp, curPrice);
        TradeResult tradeResult = tradingEngine.trade(curPrice, curTimestamp);
        if(tradeResult != null) {
            Balance balance = this.exchange.getBalance().get(QUOTE_CURRRENCY);
            balance.setAvailableForTrade(balance.getAvailableForTrade() + tradeResult.getNetProfit());
            this.exchange.getBalance().put(QUOTE_CURRRENCY, balance);

            if(tradeResult.getNetProfit() > 0) {
                win++;
//                this.profit += tradeResult.getNetProfit();
            } else {
                lose++;
                this.loss += tradeResult.getNetProfit();
            }
        }
    }

    public SimulResult collectSimulResult() {
        LOGGER.info("----------------------------------------------------------------");
        LOGGER.info("STRATEGY {}", STRATEGY);
        LOGGER.info("SIMUL_START {}", new DateTime(SIMUL_START, DateTimeZone.UTC));
        LOGGER.info("SIMUL_END {}", new DateTime(SIMUL_END, DateTimeZone.UTC));
        LOGGER.info("MODE {}", MODE);
        LOGGER.info("START_USD_BALANCE {}", exchange.getSTART_BALANCE());
        LOGGER.info("END_USD_BALANCE {}", exchange.getBalance().get(QUOTE_CURRRENCY).getAvailableForTrade());
        LOGGER.info("WINNING RATE {} (w {} / l {})", String.format("%.2f", (win*1.0 / (win+lose)) * 100.0), win, lose);
        LOGGER.info("P/L RATIO {} (P {} / L {})", String.format("%.2f", Math.abs(profit / (loss+0.00001))), String.format("%.2f",profit), String.format("%.2f",loss));
        LOGGER.info("MA_MIN {}", MA_MIN);
        LOGGER.info("SLIPPAGE {}", SLIPPAGE);
        LOGGER.info("TS_TRIGGER_PCT {}", TS_TRIGGER_PCT);
        LOGGER.info("TS_PCT {}", TS_PCT);

        if(this.STRATEGY.equals("VB")) {
            LOGGER.info("TRADING_WINDOW_SIZE_IN_MIN {}", STRATEGY_PARAMS.get(CmdLine.TRADING_WINDOW_SIZE_IN_MIN));
            LOGGER.info("TRADING_WINDOW_LOOK_BEHIND {}", STRATEGY_PARAMS.get(CmdLine.TRADING_WINDOW_LOOK_BEHIND));
            LOGGER.info("PRICE_MA_WEIGHT {}", STRATEGY_PARAMS.get(CmdLine.PRICE_MA_WEIGHT));
            LOGGER.info("VOLUME_MA_WEIGHT {}", STRATEGY_PARAMS.get(CmdLine.VOLUME_MA_WEIGHT));
        } else if(this.STRATEGY.equals("PVTOBV")) {
            LOGGER.info("PVT_LOOK_BEHIND {}", STRATEGY_PARAMS.get(CmdLine.PVT_LOOK_BEHIND));
            LOGGER.info("PVT_SIGNAL_THRESHOLD {}", STRATEGY_PARAMS.get(CmdLine.PVT_SIGNAL_THRESHOLD));
            LOGGER.info("OBV_LOOK_BEHIND {}", STRATEGY_PARAMS.get(CmdLine.OBV_LOOK_BEHIND));
            LOGGER.info("OBV_BUY_SIGNAL_THRESHOLD {}", STRATEGY_PARAMS.get(CmdLine.OBV_BUY_SIGNAL_THRESHOLD));
            LOGGER.info("OBV_SELL_SIGNAL_THRESHOLD {}", STRATEGY_PARAMS.get(CmdLine.OBV_SELL_SIGNAL_THRESHOLD));
        }

        LOGGER.info("----------------------------------------------------------------");

        System.out.println((-1)*exchange.getBalance().get(QUOTE_CURRRENCY).getAvailableForTrade());

        SimulResult result = new SimulResult();

        Integer id = simulatorDAO.getPeriodId(this.EXCHANGE,
                this.SYMBOL,
                DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_START, DateTimeZone.UTC)),
                DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_END, DateTimeZone.UTC)));

        result.setPeriodId(id);
        result.setVOLUME_MA_WEIGHT(STRATEGY_PARAMS.get(CmdLine.VOLUME_MA_WEIGHT));
        result.setTRADING_WINDOW_SIZE_IN_MIN(STRATEGY_PARAMS.get(CmdLine.TRADING_WINDOW_SIZE_IN_MIN).intValue());
        result.setTRADING_WINDOW_LOOK_BEHIND(STRATEGY_PARAMS.get(CmdLine.TRADING_WINDOW_LOOK_BEHIND).intValue());
        result.setPRICE_MA_WEIGHT(STRATEGY_PARAMS.get(CmdLine.PRICE_MA_WEIGHT));
        result.setSTART_USD_BALANCE(exchange.getSTART_BALANCE());
        result.setSLIPPAGE(this.SLIPPAGE);
        result.setSIMUL_START(DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_START, DateTimeZone.UTC)));
        result.setSIMUL_END(DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_END, DateTimeZone.UTC)));
        result.setMA_MIN(this.MA_MIN);
        result.setEND_USD_BALANCE(exchange.getBalance().get(QUOTE_CURRRENCY).getAvailableForTrade());
        result.setWINNING_RATE((win*1.0 / (win+lose)) * 100.0);
        result.setTS_TRIGGER_PCT(TS_TRIGGER_PCT);
        result.setTS_PCT(TS_PCT);
        result.setMODE(MODE);
        result.setPVT_LOOK_BEHIND(STRATEGY_PARAMS.get(CmdLine.PVT_LOOK_BEHIND).intValue());
        result.setPVT_SIGNAL_THRESHOLD(STRATEGY_PARAMS.get(CmdLine.PVT_SIGNAL_THRESHOLD).intValue());
        result.setOBV_LOOK_BEHIND(STRATEGY_PARAMS.get(CmdLine.OBV_LOOK_BEHIND).intValue());
        result.setOBV_BUY_SIGNAL_THRESHOLD(STRATEGY_PARAMS.get(CmdLine.OBV_BUY_SIGNAL_THRESHOLD).intValue());
        result.setOBV_SELL_SIGNAL_THRESHOLD(STRATEGY_PARAMS.get(CmdLine.OBV_SELL_SIGNAL_THRESHOLD).intValue());

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
