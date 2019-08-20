package com.coinhitchhiker.vbtrader.simulator.test;

import com.coinhitchhiker.vbtrader.common.model.VolatilityBreakoutRules;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
//@Import(value = {VolatilityBreakoutRules.class,})
public class TraderITConfig {

    @Value("${trading.look.behind}") private int TRADING_WINDOW_LOOK_BEHIND;
    @Value("${trading.window.size}") private int TRADING_WINDOW_SIZE;
    @Value("${trading.price.weight}") private double PRICE_MA_WEIGHT;
    @Value("${trading.volume.weight}") private double VOLUME_MA_WEIGHT;

    @Bean
    public VolatilityBreakoutRules volatilityBreakoutRules() {
        return new VolatilityBreakoutRules(TRADING_WINDOW_LOOK_BEHIND, 3, TRADING_WINDOW_SIZE, PRICE_MA_WEIGHT, VOLUME_MA_WEIGHT);
    }

}
