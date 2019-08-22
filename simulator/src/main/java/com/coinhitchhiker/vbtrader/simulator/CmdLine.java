package com.coinhitchhiker.vbtrader.simulator;

import org.apache.commons.cli.*;

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
    public static String PVT_LOOK_BEHIND = "7";
    public static String PVT_SIGNAL_THRESHOLD = "8";
    public static String OBV_LOOK_BEHIND = "9";
    public static String OBV_BUY_SIGNAL_THRESHOLD = "10";
    public static String OBV_SELL_SIGNAL_THRESHOLD = "11";

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
                cmd.getOptionValue("ex"),
                cmd.hasOption("v"),
                cmd.getOptionValue("m"),
                cmd.getOptionValue("q")
        );
    }

    static class CommandLineOptions {

        private String blackboxInput;
        private String simulStart;
        private String simulEnd;
        private String symbol;
        private String exchange;
        private boolean validation;
        private String mode;
        private String quoteCurrency;

        public CommandLineOptions(String blackboxInput
                , String simulStart
                , String simulEnd
                , String symbol
                , String exchange
                , boolean validation
                , String mode
                , String quoteCurrency) {
            this.blackboxInput = blackboxInput;
            this.simulStart = simulStart;
            this.simulEnd = simulEnd;
            this.symbol = symbol;
            this.exchange = exchange;
            this.validation = validation;
            this.mode = mode;
            this.quoteCurrency = quoteCurrency;
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

        public String getExchange() {
            return exchange;
        }

        public boolean getValidation() {
            return validation;
        }

        public String getMode() {
            return mode;
        }

        public String getQuoteCurrency() {
            return quoteCurrency;
        }
    }

    public static Map<String, Double> parseBlackboxInput(String filePath) throws IOException {
        try(BufferedReader br = Files.newBufferedReader(Paths.get(filePath))) {
            String[] params = br.readLine().split(" ");
            if(params.length != 11) {
                throw new RuntimeException("Invalid format was given. (Format: TRADING_WINDOW_SIZE_IN_MIN(int) TRADING_WINDOW_LOOK_BEHIND(int) PRICE_MA_WEIGHT(double) VOLUME_MA_WEIGHT(double) TS_TRIGGER_PCT(double < 1) TS_PCT(double < 1))");
            }

            Map<String, Double> result = new HashMap<>();
            result.put(CmdLine.TRADING_WINDOW_SIZE_IN_MIN, Double.valueOf(params[0]));
            result.put(CmdLine.TRADING_WINDOW_LOOK_BEHIND, Double.valueOf(params[1]));
            result.put(CmdLine.PRICE_MA_WEIGHT, Double.valueOf(params[2]));
            result.put(CmdLine.VOLUME_MA_WEIGHT, Double.valueOf(params[3]));
            result.put(CmdLine.TS_TRIGGER_PCT, Double.valueOf(params[4]));
            result.put(CmdLine.TS_PCT, Double.valueOf(params[5]));
            result.put(CmdLine.PVT_LOOK_BEHIND, Double.valueOf(params[6]));
            result.put(CmdLine.PVT_SIGNAL_THRESHOLD, Double.valueOf(params[7]));
            result.put(CmdLine.OBV_LOOK_BEHIND, Double.valueOf(params[8]));
            result.put(CmdLine.OBV_BUY_SIGNAL_THRESHOLD, Double.valueOf(params[9]));
            result.put(CmdLine.OBV_SELL_SIGNAL_THRESHOLD, Double.valueOf(params[10]));

            return result;
        }
    }
}
