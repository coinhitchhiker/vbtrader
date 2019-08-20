package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.simulator.db.SimulatorDAO;
import com.google.gson.Gson;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@SpringBootApplication(scanBasePackages = {"com.coinhitchhiker.vbtrader.simulator.db"})
public class SimulatorAppMain implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorAppMain.class);

    @Autowired
    private SimulatorDAO simulatorDAO;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SimulatorAppMain.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }

    public void run(String... args) throws IOException {

        CmdLine.CommandLineOptions opts = CmdLine.parseCommandLine(args);
        String exchange = opts.getExchange();
        String mode = opts.getMode();

        if(!exchange.equals("BINANCE") &&!exchange.equals("BITMEX")) {
            throw new RuntimeException("Unsupported exchange");
        }

        if(exchange.equals("BINANCE") && !mode.equals("LONG")) {
            throw new RuntimeException("BINANCE supports long only");
        }

        if(opts.getValidation()) {
            Gson gson = new Gson();
            List<TopSimulResult> topSimulResults = simulatorDAO.getTopSimulResults();
            for(TopSimulResult r : topSimulResults) {
                SimulResult simulResult = gson.fromJson(r.getSimulResult(), SimulResult.class);
                Simulator simulator = new Simulator(simulatorDAO,
                        simulResult.getTRADING_WINDOW_SIZE_IN_MIN(),
                        simulResult.getTRADING_WINDOW_LOOK_BEHIND(),
                        simulResult.getPRICE_MA_WEIGHT(),
                        simulResult.getVOLUME_MA_WEIGHT(),
                        DateTime.parse(opts.getSimulStart(), DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).getMillis(),
                        DateTime.parse(opts.getSimulEnd(), DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).plusDays(1).getMillis(),
                        opts.getExchange(),
                        opts.getSymbol(),
                        simulResult.getTS_TRIGGER_PCT(),
                        simulResult.getTS_PCT(),
                        mode,
                        opts.getQuoteCurrency()
                        );

                simulator.init();
                simulator.runSimul();
                SimulResult validationResult = simulator.collectSimulResult();
                simulator.logValidationResult(r, validationResult);
            }
        } else {
            Map<String, Double> parsedBBInput = CmdLine.parseBlackboxInput(opts.getBlackboxInput());

            double tsTriggerPct = parsedBBInput.get("tsTriggerPct");
            double tsPct = parsedBBInput.get("tsPct");

            if(tsTriggerPct <= tsPct) {
                LOGGER.error(String.format("tsTriggerPct should be bigger than tsPct (tsTriggetPct %.2f <= tsPct %.2f)", tsTriggerPct, tsPct));
                System.out.println("0");
                System.exit(0);
            }

            Simulator simulator = new Simulator(this.simulatorDAO,
                    parsedBBInput.get("tradingWindowSizeInMin").intValue(),
                    parsedBBInput.get("tradingWindowLookBehind").intValue(),
                    parsedBBInput.get("priceMaWeight"),
                    parsedBBInput.get("volumeMaWeight"),
                    DateTime.parse(opts.getSimulStart(), DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).getMillis(),
                    DateTime.parse(opts.getSimulEnd(), DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).plusDays(1).getMillis(),
                    opts.getExchange(),
                    opts.getSymbol(),
                    tsTriggerPct,
                    tsPct,
                    mode,
                    opts.getQuoteCurrency()
                    );

            simulator.init();
            simulator.runSimul();
            SimulResult simulResult = simulator.collectSimulResult();
            simulator.logSimulResult(simulResult);
        }
        System.exit(0);
    }
}
