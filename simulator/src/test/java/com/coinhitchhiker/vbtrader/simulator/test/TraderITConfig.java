package com.coinhitchhiker.vbtrader.simulator.test;

import com.coinhitchhiker.vbtrader.common.strategy.vb.VolatilityBreakout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
//@Import(value = {VolatilityBreakoutRules.class,})
public class TraderITConfig {

    @Value("${strategy.vb.look.behind}") private int TRADING_WINDOW_LOOK_BEHIND;
    @Value("${strategy.vb.window.size}") private int TRADING_WINDOW_SIZE;
    @Value("${strategy.vb.price.weight}") private double PRICE_MA_WEIGHT;
    @Value("${strategy.vb.volume.weight}") private double VOLUME_MA_WEIGHT;

}
