package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.model.ExchangeEnum;
import com.coinhitchhiker.vbtrader.common.model.StrategyEnum;
import com.coinhitchhiker.vbtrader.common.model.TradingMode;
import com.coinhitchhiker.vbtrader.common.strategy.pvtobv.PVTOBV;
import com.coinhitchhiker.vbtrader.common.strategy.vb.VolatilityBreakout;
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
import org.springframework.context.event.EventListener;

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
        ExchangeEnum exchange = opts.getExchange();
        TradingMode mode = opts.getMode();

        if(!exchange.equals(ExchangeEnum.BINANCE) &&!exchange.equals(ExchangeEnum.BITMEX)) {
            throw new RuntimeException("Unsupported exchange");
        }

        if(exchange.equals(ExchangeEnum.BINANCE) && !mode.equals(TradingMode.LONG)) {
            throw new RuntimeException("BINANCE supports long only");
        }

        Map<String, Double> parsedBBInput = CmdLine.parseBlackboxInput(opts.getBlackboxInput(), opts.getStrategy());

        if(opts.getValidation()) {
            Gson gson = new Gson();
            List<TopSimulResult> topSimulResults = simulatorDAO.getTopSimulResults();
            for(TopSimulResult r : topSimulResults) {
                SimulResult simulResult = gson.fromJson(r.getSimulResult(), SimulResult.class);
                Simulator simulator = new Simulator(simulatorDAO,
                        DateTime.parse(opts.getSimulStart(), DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).getMillis(),
                        DateTime.parse(opts.getSimulEnd(), DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).plusDays(1).getMillis(),
                        opts.getExchange(),
                        opts.getSymbol(),
                        simulResult.getTS_TRIGGER_PCT(),
                        simulResult.getTS_PCT(),
                        mode,
                        opts.getQuoteCurrency(),
                        opts.getStrategy(),
                        parsedBBInput,
                        opts.isRepoUseDB());

                simulator.init();
                simulator.runSimul();
                SimulResult validationResult = simulator.collectSimulResult();
                simulator.logValidationResult(r, validationResult);
            }
        } else {

            Double tsTriggerPct = parsedBBInput.get(CmdLine.TS_TRIGGER_PCT);
            Double tsPct = parsedBBInput.get(CmdLine.TS_PCT);

            if(tsTriggerPct != null && tsPct != null) {
                if(tsTriggerPct <= tsPct) {
                    LOGGER.error(String.format("tsTriggerPct should be bigger than tsPct (tsTriggetPct %.2f <= tsPct %.2f)", tsTriggerPct, tsPct));
                    System.out.println("0");
                    System.exit(0);
                }
            }  else {
                tsTriggerPct = 0.0;
                tsPct = 0.0;
            }

            Simulator simulator = new Simulator(this.simulatorDAO,
                    DateTime.parse(opts.getSimulStart(), DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).getMillis(),
                    DateTime.parse(opts.getSimulEnd(), DateTimeFormat.forPattern("yyyyMMdd")).withZone(DateTimeZone.UTC).plusDays(1).getMillis(),
                    opts.getExchange(),
                    opts.getSymbol(),
                    tsTriggerPct,
                    tsPct,
                    mode,
                    opts.getQuoteCurrency(),
                    opts.getStrategy(),
                    parsedBBInput,
                    opts.isRepoUseDB());

            simulator.init();
            simulator.runSimul();
            SimulResult simulResult = simulator.collectSimulResult();
            simulator.logSimulResult(simulResult);
        }
        System.exit(0);
    }
}
