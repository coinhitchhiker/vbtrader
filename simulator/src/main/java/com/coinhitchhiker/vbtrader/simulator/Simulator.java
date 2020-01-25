package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.indicator.HullMovingAverage;
import com.coinhitchhiker.vbtrader.common.indicator.SMA;
import com.coinhitchhiker.vbtrader.common.indicator.WMA;
import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.common.strategy.hmatrade.HMATradeLongTradingEngine;
import com.coinhitchhiker.vbtrader.common.strategy.ibs.IBSLongTradingEngine;
import com.coinhitchhiker.vbtrader.common.strategy.ibs.IBSShortTradingEngine;
import com.coinhitchhiker.vbtrader.common.strategy.pvtobv.PVTOBVLongTradingEngine;
import com.coinhitchhiker.vbtrader.common.strategy.vb.VBLongTradingEngine;
import com.coinhitchhiker.vbtrader.common.strategy.vb.VBShortTradingEngine;
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
    private static final Logger LOGGERBUYSELL = LoggerFactory.getLogger("BUYSELLLOGGER");

    private final long SIMUL_START;
    private final long SIMUL_END;
    private final ExchangeEnum EXCHANGE;
    private final String SYMBOL;
    private final double TS_TRIGGER_PCT;
    private final double TS_PCT;
    private final TradingMode MODE;
    private final String QUOTE_CURRRENCY;
    private final StrategyEnum STRATEGY;
    private final Map<String, Double> STRATEGY_PARAMS;
    private final boolean REPO_USE_DB;

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
                     ExchangeEnum EXCHANGE,
                     String SYMBOL,
                     double TS_TRIGGER_PCT,
                     double TS_PCT,
                     TradingMode MODE,
                     String QUOTE_CURRENCY,
                     StrategyEnum strategy,
                     Map<String, Double> strategyParams,
                     boolean REPO_USE_DB)  {

        this.simulatorDAO = simulatorDAO;
        this.SIMUL_START = SIMUL_START;
        this.SIMUL_END = SIMUL_END;
        this.EXCHANGE = EXCHANGE;
        this.SYMBOL = SYMBOL;
        this.TS_TRIGGER_PCT = TS_TRIGGER_PCT;
        this.TS_PCT = TS_PCT;
        this.MODE = MODE;
        this.QUOTE_CURRRENCY = QUOTE_CURRENCY;
        this.STRATEGY = strategy;
        this.STRATEGY_PARAMS = strategyParams;
        this.REPO_USE_DB = REPO_USE_DB;
        this.FEE_RATE = getFEE(EXCHANGE);
    }

    private double getFEE(ExchangeEnum exchangeEnum) {
        if(exchangeEnum == ExchangeEnum.BINANCE) {
            return 0.045;
        } else if(exchangeEnum == ExchangeEnum.OKEX) {
            return 0.064;
        } else if(exchangeEnum == ExchangeEnum.BITMEX) {
            return 0.06375;
        } else {
            throw new RuntimeException("Unsupported exchange was given");
        }
    }

    public void init() {
        this.repository = new SimulatorRepositoryImpl(EXCHANGE, SYMBOL, SIMUL_START, SIMUL_END, simulatorDAO, REPO_USE_DB);
        this.orderBookCache = new SimulatorOrderBookCache();
        this.exchange = new SimulatorExchange(this.repository, this.orderBookCache, SLIPPAGE);
        this.tradingEngine = createTradingEngine(this.STRATEGY_PARAMS);
        ((SimulatorExchange)this.exchange).setTradingEngine(this.tradingEngine);
    }

    private TradingEngine createTradingEngine(Map<String, Double> strategyParams) {
        TradingEngine tradingEngine = null;
        if(this.MODE.equals(TradingMode.LONG) && this.STRATEGY.equals(StrategyEnum.VB)) {
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
                    TS_PCT,
                    false);
        } else if(this.MODE.equals(TradingMode.SHORT) && this.STRATEGY.equals(StrategyEnum.VB)) {
            tradingEngine = new VBShortTradingEngine(repository,
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
                    TS_PCT,
                    false);
        } else if(this.MODE.equals(TradingMode.LONG) && this.STRATEGY.equals(StrategyEnum.PVTOBV)) {
            tradingEngine = new PVTOBVLongTradingEngine(repository,
                    exchange,
                    orderBookCache,
                    SYMBOL,
                    QUOTE_CURRRENCY,
                    EXCHANGE,
                    FEE_RATE,
                    true,
                    true,
                    TS_TRIGGER_PCT,
                    TS_PCT,
                    0.0,
                    strategyParams.get(CmdLine.MIN_CANDLE_LOOK_BEHIND).intValue(),
                    strategyParams.get(CmdLine.PVTOBV_DROP_THRESHOLD),
                    strategyParams.get(CmdLine.PRICE_DROP_THRESHOLD),
                    strategyParams.get(CmdLine.STOP_LOSS_PCT),
                    false);
        } else if(this.MODE.equals(TradingMode.LONG) && this.STRATEGY.equals(StrategyEnum.IBS)) {
            if(strategyParams.get(CmdLine.IBS_LOWER_THRESHOLD) >= strategyParams.get(CmdLine.IBS_UPPER_THRESHOLD)) {
                System.out.println("-10000");
                System.exit(0);
            }

            tradingEngine = new IBSLongTradingEngine(repository, exchange, orderBookCache,
                    SYMBOL, QUOTE_CURRRENCY, 0.0, EXCHANGE, FEE_RATE,
                    true, true, TS_TRIGGER_PCT,
                    TS_PCT, true, strategyParams.get(CmdLine.STOP_LOSS_PCT),
                    strategyParams.get(CmdLine.TRADING_WINDOW_SIZE_IN_MIN).intValue(),
                    strategyParams.get(CmdLine.IBS_LOWER_THRESHOLD),
                    strategyParams.get(CmdLine.IBS_UPPER_THRESHOLD),
                    false);
        } else if(this.MODE.equals(TradingMode.SHORT) && this.STRATEGY.equals(StrategyEnum.IBS)) {
            tradingEngine = new IBSShortTradingEngine(repository, exchange, orderBookCache, SYMBOL, QUOTE_CURRRENCY,
                    0.0, EXCHANGE, FEE_RATE, true, true, TS_TRIGGER_PCT,
                    TS_PCT, false, strategyParams.get(CmdLine.STOP_LOSS_PCT), strategyParams.get(CmdLine.TRADING_WINDOW_SIZE_IN_MIN).intValue(),
                    strategyParams.get(CmdLine.IBS_UPPER_THRESHOLD), false);
        } else if (this.MODE.equals(TradingMode.LONG) && this.STRATEGY.equals(StrategyEnum.HMA_TRADE)) {
            tradingEngine = new HMATradeLongTradingEngine(repository, exchange, orderBookCache, SYMBOL, QUOTE_CURRRENCY,
                    EXCHANGE, FEE_RATE, true, TimeFrame.M5, strategyParams.get(CmdLine.HMA_LENGTH).intValue(),
                    strategyParams.get(CmdLine.TRADING_WINDOW_LOOK_BEHIND).intValue(), true, SIMUL_START);
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

        long curTimestamp = 0; double curPrice = 0; double curVol = 0;

        for(Candle candle : allCandles) {
            // trade open price point
            curTimestamp = candle.getOpenTime(); curPrice = candle.getOpenPrice(); curVol = candle.getVolume();
            tradeWith(curPrice, curTimestamp, curVol, tradingEngine);

            long timeDiff = candle.getCloseTime() - candle.getOpenTime();

            // high  price point comes first
            curTimestamp = timeDiff / 3 + candle.getOpenTime(); curPrice = candle.getHighPrice(); curVol = candle.getVolume();
            tradeWith(curPrice, curTimestamp, curVol, tradingEngine);

            // assume low price point comes second
            curTimestamp = 2*timeDiff / 3 + candle.getOpenTime(); curPrice = candle.getLowPrice(); curVol = candle.getVolume();
            tradeWith(curPrice, curTimestamp, curVol, tradingEngine);

            // trade close price
            curTimestamp = candle.getCloseTime(); curPrice = candle.getClosePrice(); curVol = candle.getVolume();
            tradeWith(curPrice, curTimestamp, curVol, tradingEngine);
        }
    }

    private void tradeWith(double curPrice, long curTimestamp, double curVol, TradingEngine tradingEngine) {
        this.exchange.setTimestampAndPrice(curTimestamp, curPrice);
        TradeResult tradeResult = tradingEngine.trade(curPrice, curTimestamp, curVol);
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
        LOGGERBUYSELL.info("----------------------------------------------------------------");
        LOGGERBUYSELL.info("STRATEGY {}", STRATEGY.name());
        LOGGERBUYSELL.info("SIMUL_START {}", new DateTime(SIMUL_START, DateTimeZone.UTC));
        LOGGERBUYSELL.info("SIMUL_END {}", new DateTime(SIMUL_END, DateTimeZone.UTC));
        LOGGERBUYSELL.info("MODE {}", MODE);
        LOGGERBUYSELL.info("START_USD_BALANCE {}", exchange.getSTART_BALANCE());
        LOGGERBUYSELL.info("END_USD_BALANCE {}", exchange.getBalance().get(QUOTE_CURRRENCY).getAvailableForTrade());
        LOGGERBUYSELL.info("WINNING RATE {} (w {} / l {})", String.format("%.2f", (win*1.0 / (win+lose)) * 100.0), win, lose);
        LOGGERBUYSELL.info("P/L RATIO {} (P {} / L {})", String.format("%.2f", Math.abs(profit / (loss+0.00001))), String.format("%.2f",profit), String.format("%.2f",loss));
        LOGGERBUYSELL.info("MA_MIN {}", MA_MIN);
        LOGGERBUYSELL.info("SLIPPAGE {}", SLIPPAGE);
        LOGGERBUYSELL.info("TS_TRIGGER_PCT {}", TS_TRIGGER_PCT);
        LOGGERBUYSELL.info("TS_PCT {}", TS_PCT);

        if(this.STRATEGY.equals(StrategyEnum.VB)) {
            LOGGERBUYSELL.info("TRADING_WINDOW_SIZE_IN_MIN {}", STRATEGY_PARAMS.get(CmdLine.TRADING_WINDOW_SIZE_IN_MIN));
            LOGGERBUYSELL.info("TRADING_WINDOW_LOOK_BEHIND {}", STRATEGY_PARAMS.get(CmdLine.TRADING_WINDOW_LOOK_BEHIND));
            LOGGERBUYSELL.info("PRICE_MA_WEIGHT {}", STRATEGY_PARAMS.get(CmdLine.PRICE_MA_WEIGHT));
            LOGGERBUYSELL.info("VOLUME_MA_WEIGHT {}", STRATEGY_PARAMS.get(CmdLine.VOLUME_MA_WEIGHT));
        } else if(this.STRATEGY.equals(StrategyEnum.PVTOBV)) {
            LOGGERBUYSELL.info("MIN_CANDLE_LOOK_BEHIND {}", STRATEGY_PARAMS.get(CmdLine.MIN_CANDLE_LOOK_BEHIND));
            LOGGERBUYSELL.info("PVTOBV_DROP_THRESHOLD {}", STRATEGY_PARAMS.get(CmdLine.PVTOBV_DROP_THRESHOLD));
            LOGGERBUYSELL.info("PRICE_DROP_THRESHOLD {}", STRATEGY_PARAMS.get(CmdLine.PRICE_DROP_THRESHOLD));
            LOGGERBUYSELL.info("STOP_LOSS_PCT {}", STRATEGY_PARAMS.get(CmdLine.STOP_LOSS_PCT));
        } else if(this.STRATEGY.equals(StrategyEnum.IBS)) {
            LOGGERBUYSELL.info("TRADING_WINDOW_SIZE_IN_MIN {}", STRATEGY_PARAMS.get(CmdLine.TRADING_WINDOW_SIZE_IN_MIN));
            LOGGERBUYSELL.info("IBS_LOWER_THRESHOLD {}", STRATEGY_PARAMS.get(CmdLine.IBS_LOWER_THRESHOLD));
            LOGGERBUYSELL.info("IBS_UPPER_THRESHOLD {}", STRATEGY_PARAMS.get(CmdLine.IBS_UPPER_THRESHOLD));
            LOGGERBUYSELL.info("STOP_LOSS_PCT {}", STRATEGY_PARAMS.get(CmdLine.STOP_LOSS_PCT));
            LOGGERBUYSELL.info("TS_TRIGGER_PCT {}", STRATEGY_PARAMS.get(CmdLine.TS_TRIGGER_PCT));
            LOGGERBUYSELL.info("TS_PCT {}", STRATEGY_PARAMS.get(CmdLine.TS_PCT));
        }

        LOGGERBUYSELL.info("----------------------------------------------------------------");

        System.out.println((-1)*exchange.getBalance().get(QUOTE_CURRRENCY).getAvailableForTrade());
//        if(loss != 0) {
//            System.out.println(  -1*(win*1.0 / (win+lose)) - Math.abs(  profit / loss  ) );
//        } else {
//            System.out.println(  -1*(win*1.0 / (win+lose)) );
//        }


        SimulResult result = new SimulResult();

        Integer id = simulatorDAO.getPeriodId(this.EXCHANGE.name(),
                this.SYMBOL,
                DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_START, DateTimeZone.UTC)),
                DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_END, DateTimeZone.UTC)));

        result.setPeriodId(id);

        result.setSTRATEGY(STRATEGY);
        result.setSTART_USD_BALANCE(exchange.getSTART_BALANCE());
        result.setSLIPPAGE(this.SLIPPAGE);
        result.setSIMUL_START(DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_START, DateTimeZone.UTC)));
        result.setSIMUL_END(DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_END, DateTimeZone.UTC)));
        result.setEND_USD_BALANCE(exchange.getBalance().get(QUOTE_CURRRENCY).getAvailableForTrade());
        result.setWINNING_RATE((win*1.0 / (win+lose)) * 100.0);
        result.setPL_RATIO(Math.abs(profit / (loss+0.00001)));
        result.setTS_TRIGGER_PCT(TS_TRIGGER_PCT);
        result.setTS_PCT(TS_PCT);
        result.setMODE(MODE);

        if(STRATEGY.equals(StrategyEnum.VB)) {
            result.setVOLUME_MA_WEIGHT(STRATEGY_PARAMS.get(CmdLine.VOLUME_MA_WEIGHT));
            result.setTRADING_WINDOW_SIZE_IN_MIN(STRATEGY_PARAMS.get(CmdLine.TRADING_WINDOW_SIZE_IN_MIN).intValue());
            result.setTRADING_WINDOW_LOOK_BEHIND(STRATEGY_PARAMS.get(CmdLine.TRADING_WINDOW_LOOK_BEHIND).intValue());
            result.setPRICE_MA_WEIGHT(STRATEGY_PARAMS.get(CmdLine.PRICE_MA_WEIGHT));
            result.setMA_MIN(this.MA_MIN);
        } else if(STRATEGY.equals(StrategyEnum.PVTOBV)) {
            result.setMIN_CANDLE_LOOK_BEHIND(STRATEGY_PARAMS.get(CmdLine.MIN_CANDLE_LOOK_BEHIND).intValue());
            result.setPVTOBV_DROP_THRESHOLD(STRATEGY_PARAMS.get(CmdLine.PVTOBV_DROP_THRESHOLD));
            result.setPRICE_DROP_THRESHOLD(STRATEGY_PARAMS.get(CmdLine.PRICE_DROP_THRESHOLD));
            result.setSTOP_LOSS_PCT(STRATEGY_PARAMS.get(CmdLine.STOP_LOSS_PCT));
        } else if(STRATEGY.equals(StrategyEnum.IBS)) {
            result.setIBS_LOWER_THRESHOLD(STRATEGY_PARAMS.get(CmdLine.IBS_LOWER_THRESHOLD));
            result.setIBS_UPPER_THRESHOLD(STRATEGY_PARAMS.get(CmdLine.IBS_UPPER_THRESHOLD));
            result.setTRADING_WINDOW_SIZE_IN_MIN(STRATEGY_PARAMS.get(CmdLine.TRADING_WINDOW_SIZE_IN_MIN).intValue());
        } else if(STRATEGY.equals(StrategyEnum.HMA_TRADE)) {
            // do things for now
        } else {
            throw new RuntimeException("Unsupported strategy was given: " + STRATEGY);
        }

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
