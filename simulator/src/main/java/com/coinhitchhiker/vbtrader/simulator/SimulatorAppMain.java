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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@SpringBootApplication(scanBasePackages = {
        "com.coinhitchhiker.vbtrader.simulator.db",
        "com.coinhitchhiker.vbtrader.simulator.config"
})
public class SimulatorAppMain implements CommandLineRunner {

    @Value("${trading.exchange}") String EXCHANGE;
    @Value("${trading.simul.start}") String SIMUL_START;    // YYYYMMDD
    @Value("${trading.simul.end}") String SIMUL_END;    // YYYYMMDD
    @Value("${trading.quote.currency}") String QUOTE_CURRRENCY;    // USDT? BTC?

    @Value("${trading.mode}") private String MODE;
    @Value("${trading.symbol}") private String SYMBOL;
    @Value("${trading.ts.trigger.pct}") private double TS_TRIGGER_PCT;
    @Value("${trading.ts.pct}") private double TS_PCT;

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorAppMain.class);

    @Autowired private SimulatorDAO simulatorDAO;
    @Autowired private Simulator simulator;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SimulatorAppMain.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }

    public void run(String... args) throws IOException {

        ExchangeEnum exchange = ExchangeEnum.valueOf(EXCHANGE);
        TradingMode mode = TradingMode.valueOf(MODE);

        if(!exchange.equals(ExchangeEnum.BINANCE) && !exchange.equals(ExchangeEnum.BITMEX) && !exchange.equals(ExchangeEnum.OKEX)) {
            throw new RuntimeException("Unsupported exchange");
        }

        if(exchange.equals(ExchangeEnum.BINANCE) && !mode.equals(TradingMode.LONG)) {
            throw new RuntimeException("BINANCE supports long only");
        }

//        Map<String, Double> parsedBBInput = CmdLine.parseBlackboxInput(opts.getBlackboxInput(), opts.getStrategy());
//
//        if(opts.getValidation()) {
//            Gson gson = new Gson();
//            List<TopSimulResult> topSimulResults = simulatorDAO.getTopSimulResults(opts.getMode().name(), opts.getStrategy().name());
//            for(TopSimulResult r : topSimulResults) {
//                SimulResult simulResult = gson.fromJson(r.getSimulResult(), SimulResult.class);
//                simulator.runSimul();
//                SimulResult validationResult = simulator.collectSimulResult();
//                simulator.logValidationResult(r, validationResult);
//            }
//        } else {

            if(TS_TRIGGER_PCT > 0 && TS_PCT > 0) {
                if(TS_TRIGGER_PCT <= TS_PCT) {
                    LOGGER.error(String.format("tsTriggerPct should be bigger than tsPct (tsTriggetPct %.2f <= tsPct %.2f)", TS_TRIGGER_PCT, TS_PCT));
                    System.out.println("0");
                    System.exit(0);
                }
            }  else {
                TS_TRIGGER_PCT = 0.0;
                TS_PCT = 0.0;
            }

            simulator.runSimul();
            SimulResult simulResult = simulator.collectSimulResult();
            simulator.logSimulResult(simulResult);
//        }
        System.exit(0);
    }
}
