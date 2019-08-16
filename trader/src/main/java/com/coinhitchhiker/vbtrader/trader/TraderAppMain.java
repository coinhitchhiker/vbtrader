package com.coinhitchhiker.vbtrader.trader;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.coinhitchhiker.vbtrader.trader.config"
})
@EnableScheduling
public class TraderAppMain implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(TraderAppMain.class);
        app.run(args);
    }

    @Override
    public void run(String... args) throws Exception {
    }
}
