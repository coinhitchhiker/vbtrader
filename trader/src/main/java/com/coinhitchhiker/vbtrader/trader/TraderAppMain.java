package com.coinhitchhiker.vbtrader.trader;

import com.coinhitchhiker.vbtrader.common.model.Exchange;
import com.coinhitchhiker.vbtrader.common.model.TradingEngine;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import static org.joda.time.DateTimeZone.UTC;

@SpringBootApplication(scanBasePackages = {
        "com.coinhitchhiker.vbtrader.trader.config"
})
@EnableScheduling
public class TraderAppMain implements CommandLineRunner {

    @Autowired TradingEngine tradingEngine;
    @Autowired Exchange exchange;

    @Value("${trading.symbol}") private String SYMBOL;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(TraderAppMain.class);
        app.run(args);
    }

    @Override
    public void run(String... args) throws Exception {
    }

    @Scheduled(cron = "${trading.frequency}")
    public void triggerTrade() {
        long timestamp = DateTime.now(UTC).getMillis();
        double curPrice = exchange.getCurrentPrice(SYMBOL);

        tradingEngine.trade(curPrice, timestamp, 0);
    }

}
