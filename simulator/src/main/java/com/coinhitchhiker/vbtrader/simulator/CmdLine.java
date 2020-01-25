package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.model.ExchangeEnum;
import com.coinhitchhiker.vbtrader.common.model.StrategyEnum;
import com.coinhitchhiker.vbtrader.common.model.TradingMode;
import org.apache.commons.cli.*;
import org.springframework.boot.autoconfigure.web.ResourceProperties;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class CmdLine {

    public static String TRADING_WINDOW_SIZE_IN_MIN = "1";
    public static String TRADING_WINDOW_LOOK_BEHIND = "2";
    public static String PRICE_MA_WEIGHT = "3";
    public static String VOLUME_MA_WEIGHT = "4";
    public static String TS_TRIGGER_PCT = "5";
    public static String TS_PCT = "6";
    public static String MIN_CANDLE_LOOK_BEHIND = "7";
    public static String PVTOBV_DROP_THRESHOLD = "8";
    public static String PRICE_DROP_THRESHOLD = "9";
    public static String STOP_LOSS_PCT = "10";
    public static String IBS_LOWER_THRESHOLD = "11";
    public static String IBS_UPPER_THRESHOLD = "12";
    public static String HMA_LENGTH = "13";

    public static CmdLine.CommandLineOptions parseCommandLine(String... args) {
        Options options = new Options();

        Option simulStart = new Option("s", "simul-start", true, "YYYYMMDD");
        simulStart.setRequired(true);
        options.addOption(simulStart);

        Option simulEnd = new Option("e", "simul-end", true, "YYYYMMDD");
        simulEnd.setRequired(true);
        options.addOption(simulEnd);

        Option blackBoxInput = new Option("x", "bb-input", true, "Blackbox input");
        blackBoxInput.setRequired(false);
        options.addOption(blackBoxInput);

        Option symbol = new Option("y", "symbol", true, "Binance symbol ex)BTCUSDT");
        symbol.setRequired(true);
        options.addOption(symbol);

        Option exchnage = new Option("ex", "exchange", true, "Exchange to simulate ex)BINANCE");
        exchnage.setRequired(true);
        options.addOption(exchnage);

        Option validation = new Option("v", "validation", false, "Run validations against top simul results");
        validation.setRequired(false);
        options.addOption(validation);

        Option mode = new Option("m", "mode", true, "LONG / SHORT");
        mode.setRequired(true);
        options.addOption(mode);

        Option quoteCurrency = new Option("q", "quote-currency", true, "USDT, XBt...");
        quoteCurrency.setRequired(true);
        options.addOption(quoteCurrency);

        Option strategy = new Option("str", "strategy", true, "Trading strategy (VB/PVTOBV)");
        strategy.setRequired(true);
        options.addOption(strategy);

        Option repoUseDB = new Option("db", "repo-db", false, "SimulatorRepo should use DB");
        repoUseDB.setRequired(false);
        options.addOption(repoUseDB);

        Option verbose = new Option("vb", "verbose", false, "verbose logging");
        verbose.setRequired(false);
        options.addOption(verbose);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("java -jar simulator.jar", options);

            System.exit(1);
        }

        return new CmdLine.CommandLineOptions(
                cmd.getOptionValue("x"),
                cmd.getOptionValue("s"),
                cmd.getOptionValue("e"),
                cmd.getOptionValue("y"),
                ExchangeEnum.valueOf(cmd.getOptionValue("ex")),
                cmd.hasOption("v"),
                TradingMode.valueOf(cmd.getOptionValue("m")),
                cmd.getOptionValue("q"),
                StrategyEnum.valueOf(cmd.getOptionValue("str")),
                cmd.hasOption("db"),
                cmd.hasOption("vb")
        );
    }

    static class CommandLineOptions {

        private String blackboxInput;
        private String simulStart;
        private String simulEnd;
        private String symbol;
        private ExchangeEnum exchange;
        private boolean validation;
        private TradingMode mode;
        private String quoteCurrency;
        private StrategyEnum strategy;
        private boolean repoUseDB;
        private boolean verbose;

        public CommandLineOptions(String blackboxInput
                , String simulStart
                , String simulEnd
                , String symbol
                , ExchangeEnum exchange
                , boolean validation
                , TradingMode mode
                , String quoteCurrency
                , StrategyEnum strategy
                , boolean repoUseDB
                , boolean verbose) {
            this.blackboxInput = blackboxInput;
            this.simulStart = simulStart;
            this.simulEnd = simulEnd;
            this.symbol = symbol;
            this.exchange = exchange;
            this.validation = validation;
            this.mode = mode;
            this.quoteCurrency = quoteCurrency;
            this.strategy = strategy;
            this.repoUseDB = repoUseDB;
            this.verbose = verbose;
        }

        public String getBlackboxInput() {
            return blackboxInput;
        }

        public String getSimulStart() {
            return simulStart;
        }

        public String getSimulEnd() {
            return simulEnd;
        }

        public String getSymbol() {
            return symbol;
        }

        public ExchangeEnum getExchange() {
            return exchange;
        }

        public boolean getValidation() {
            return validation;
        }

        public TradingMode getMode() {
            return mode;
        }

        public String getQuoteCurrency() {
            return quoteCurrency;
        }

        public StrategyEnum getStrategy() {
            return strategy;
        }

        public boolean isRepoUseDB() {
            return repoUseDB;
        }

        public boolean isVerbose() {
            return verbose;
        }
    }

    public static Map<String, Double> parseBlackboxInput(String filePath, StrategyEnum strategy) throws IOException {
        try(BufferedReader br = Files.newBufferedReader(Paths.get(filePath))) {
            String[] params = br.readLine().split(" ");
            Map<String, Double> result = new HashMap<>();

            if(strategy.equals(StrategyEnum.VB)) {
                if(params.length != 6) {
                    throw new RuntimeException("Invalid format was given. (Format: TRADING_WINDOW_SIZE_IN_MIN(int) TRADING_WINDOW_LOOK_BEHIND(int) PRICE_MA_WEIGHT(double) VOLUME_MA_WEIGHT(double) TS_TRIGGER_PCT(double < 1) TS_PCT(double < 1))");
                }
                result.put(CmdLine.TRADING_WINDOW_SIZE_IN_MIN, Double.valueOf(params[0]));
                result.put(CmdLine.TRADING_WINDOW_LOOK_BEHIND, Double.valueOf(params[1]));
                result.put(CmdLine.PRICE_MA_WEIGHT, Double.valueOf(params[2]));
                result.put(CmdLine.VOLUME_MA_WEIGHT, Double.valueOf(params[3]));
                result.put(CmdLine.TS_TRIGGER_PCT, Double.valueOf(params[4]));
                result.put(CmdLine.TS_PCT, Double.valueOf(params[5]));
            } else if(strategy.equals(StrategyEnum.PVTOBV)) {
                if (params.length != 6) {
                    throw new RuntimeException("Invalid format was given. (Format: MIN_CANDLE_LOOK_BEHIND(int) PVTOBV_DROP_THRESHOLD(double) PRICE_DROP_THRESHOLD(double) STOP_LOSS_PCT(double) TS_TRIGGER_PCT(double < 1) TS_PCT(double < 1))");
                }
                result.put(CmdLine.MIN_CANDLE_LOOK_BEHIND, Double.valueOf(params[0]));
                result.put(CmdLine.PVTOBV_DROP_THRESHOLD, Double.valueOf(params[1]));
                result.put(CmdLine.PRICE_DROP_THRESHOLD, Double.valueOf(params[2]));
                result.put(CmdLine.STOP_LOSS_PCT, Double.valueOf(params[3]));
                result.put(CmdLine.TS_TRIGGER_PCT, Double.valueOf(params[4]));
                result.put(CmdLine.TS_PCT, Double.valueOf(params[5]));
            } else if(strategy.equals(StrategyEnum.IBS)) {
                if (params.length != 6) {
                    throw new RuntimeException("Invalid format (TRADING_WINDOW_SIZE(int) IBS_LOWER_THRESHOLD(double<=0.5) IBS_UPPER_THRESHOLD(double>0.5) STOP_LOSS_PCT(double) TS_TRIGGER_PCT(double<1) TS_PCT(double<1)");
                }
                result.put(CmdLine.TRADING_WINDOW_SIZE_IN_MIN, Double.valueOf(params[0]));
                result.put(CmdLine.IBS_LOWER_THRESHOLD, Double.valueOf(params[1]));
                result.put(CmdLine.IBS_UPPER_THRESHOLD, Double.valueOf(params[2]));
                result.put(CmdLine.STOP_LOSS_PCT, Double.valueOf(params[3]));
                result.put(CmdLine.TS_TRIGGER_PCT, Double.valueOf(params[4]));
                result.put(CmdLine.TS_PCT, Double.valueOf(params[5]));
            } else if(strategy.equals(StrategyEnum.HMA_TRADE)) {
                if (params.length != 2) {
                    throw new RuntimeException("Invalid format (TRADING_WINDOW_LOOK_BEHIND(int) HMA_LENGTH(int)");
                }
                result.put(CmdLine.TRADING_WINDOW_LOOK_BEHIND, Double.valueOf(params[0]));
                result.put(CmdLine.HMA_LENGTH, Double.valueOf(params[1]));
            } else {
                throw new RuntimeException("Unsupported strategy was given: " + strategy);
            }
            return result;
        }
    }
}

