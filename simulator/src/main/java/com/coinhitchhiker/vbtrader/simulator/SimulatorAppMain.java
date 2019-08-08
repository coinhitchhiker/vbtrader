package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.Repository;
import com.coinhitchhiker.vbtrader.common.TradingWindow;
import com.coinhitchhiker.vbtrader.common.VolatilityBreakoutRules;
import com.coinhitchhiker.vbtrader.simulator.db.SimulatorDAO;
import com.google.gson.Gson;
import org.apache.commons.cli.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication(scanBasePackages = {"com.coinhitchhiker.vbtrader.simulator.db"})
public class SimulatorAppMain implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorAppMain.class);
    private static final int MA_MIN = 3;

    private long SIMUL_START;
    private long SIMUL_END;

    private int TRADING_WINDOW_SIZE_IN_MIN = 61;
    private int TRADING_WINDOW_LOOK_BEHIND = 20;
    private String DATA_FILE = null;
    private double START_USD_BALANCE = 10000.0D;
    private double USD_BALANCE = 10000.0D;
    private double PRICE_MA_WEIGHT = 0.3D;
    private double VOLUME_MA_WEIGHT= 0.7D;
    private String EXCHANGE;
    private String SYMBOL;

    private final double SLIPPAGE = 0.1/100.0D;
    private final double H_VOLUME_WEIGHT = 0.677D;

    @Autowired
    private SimulatorDAO simulatorDAO;

    @Autowired
    DataSource dataSource;

    private Repository repository;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication((SimulatorAppMain.class));
        app.setBannerMode(Banner.Mode.OFF);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }

    private CommandLineOptions parseCommandLine(String... args) {
        Options options = new Options();

        Option simulStart = new Option("s", "simul-start", true, "YYYYMMDD");
        simulStart.setRequired(true);
        options.addOption(simulStart);

        Option simulEnd = new Option("e", "simul-end", true, "YYYYMMDD");
        simulEnd.setRequired(true);
        options.addOption(simulEnd);

        Option blackBoxInput = new Option("x", "bb-input", true, "Blackbox input");
        blackBoxInput.setRequired(true);
        options.addOption(blackBoxInput);

        Option spreadFile = new Option("f", "data-file", true, "data file");
        spreadFile.setRequired(false);
        options.addOption(spreadFile);

        Option symbol = new Option("y", "symbol", true, "Binance symbol ex)BTCUSDT");
        symbol.setRequired(true);
        options.addOption(symbol);

        Option exchnage = new Option("ex", "exchange", true, "Exchange to simulate ex)BINANCE");
        exchnage.setRequired(true);
        options.addOption(exchnage);

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

        return new CommandLineOptions(
                cmd.getOptionValue("x"),
                cmd.getOptionValue("f"),
                cmd.getOptionValue("s"),
                cmd.getOptionValue("e"),
                cmd.getOptionValue("y"),
                cmd.getOptionValue("ex")
        );
    }

    private Map<String, Double> parseBlackboxInput(String filePath) throws IOException {
        try(BufferedReader br = Files.newBufferedReader(Paths.get(filePath))) {
            String[] params = br.readLine().split(" ");
            if(params.length != 4) {
                throw new RuntimeException("Invalid format was given. (Format: TRADING_WINDOW_SIZE_IN_MIN(int) TRADING_WINDOW_LOOK_BEHIND(int) PRICE_MA_WEIGHT(double) VOLUME_MA_WEIGHT(double))");
            }

            Map<String, Double> result = new HashMap<>();
            result.put("tradingWindowSizeInMin", Double.valueOf(params[0]));
            result.put("tradingWindowLookBehind", Double.valueOf(params[1]));
            result.put("priceMaWeight", Double.valueOf(params[2]));
            result.put("volumeMaWeight", Double.valueOf(params[3]));
            return result;
        }
    }

    public void run(String... args) throws IOException {

        LOGGER.info(dataSource.toString());

        CommandLineOptions opts = this.parseCommandLine(args);

        if(!opts.getExchange().equals("BINANCE")) throw new RuntimeException("Only BINANCE is supported for now");

        Map<String, Double> parsedBBInput = parseBlackboxInput(opts.getBlackboxInput());

        this.DATA_FILE = opts.getDataFile();
        this.TRADING_WINDOW_SIZE_IN_MIN = parsedBBInput.get("tradingWindowSizeInMin").intValue();
        this.TRADING_WINDOW_LOOK_BEHIND = parsedBBInput.get("tradingWindowLookBehind").intValue();
        this.PRICE_MA_WEIGHT = parsedBBInput.get("priceMaWeight");
        this.VOLUME_MA_WEIGHT = parsedBBInput.get("volumeMaWeight");
        this.SIMUL_START = DateTime.parse(opts.getSimulStart(), DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).getMillis();
        this.SIMUL_END = DateTime.parse(opts.getSimulEnd(), DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).getMillis();
        this.EXCHANGE = opts.getExchange();
        this.SYMBOL = opts.getSymbol();

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
            List<TradingWindow> lookbehindTradingWindows = repository.getLastNTradingWindow(TRADING_WINDOW_LOOK_BEHIND, TRADING_WINDOW_SIZE_IN_MIN, curTimeStamp);
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

        outputSimulationResult();

        System.exit(0);

    }

    private void outputSimulationResult() {
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

        Integer id = simulatorDAO.getPeriodId(this.EXCHANGE,
                this.SYMBOL,
                DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_START, DateTimeZone.UTC)),
                DateTimeFormat.forPattern("yyyyMMdd").print(new DateTime(this.SIMUL_END, DateTimeZone.UTC)));

        if(id == null) {
            LOGGER.info("------------------------------------------------------");
            LOGGER.info("periodId was not found from DB. No result logging will happen...");
            LOGGER.info("------------------------------------------------------");
            return;
        }

        Double bestResult = simulatorDAO.getBestResult(id);

        if(bestResult != null && Math.round(bestResult) >= Math.round(this.USD_BALANCE)) {
            LOGGER.info("------------------------------------------------------");
            LOGGER.info("No logging will happen. Current bestResult {} >= new simulResult {}", bestResult, this.USD_BALANCE);
            LOGGER.info("------------------------------------------------------");
            return;
        }

        SimulResult result = new SimulResult();
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

        Gson gson = new Gson();
        String json = gson.toJson(result);
        try {
            simulatorDAO.insertSimulResult(id, json);
        } catch(Exception e) {
            LOGGER.error("Exception occured when logging result", e);
        }
    }

    class CommandLineOptions {

        private String blackboxInput;
        private String dataFile;
        private String simulStart;
        private String simulEnd;
        private String symbol;
        private String exchange;

        public CommandLineOptions(String blackboxInput
                , String dataFile
                , String simulStart
                , String simulEnd
                , String symbol
                , String exchange) {
            this.blackboxInput = blackboxInput;
            this.dataFile = dataFile;
            this.simulStart = simulStart;
            this.simulEnd = simulEnd;
            this.symbol = symbol;
            this.exchange = exchange;
        }

        public String getBlackboxInput() {
            return blackboxInput;
        }

        public String getDataFile() {
            return dataFile;
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
    }
}
