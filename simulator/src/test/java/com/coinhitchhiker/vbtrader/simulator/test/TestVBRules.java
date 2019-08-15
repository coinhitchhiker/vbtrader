package com.coinhitchhiker.vbtrader.simulator.test;

import com.coinhitchhiker.vbtrader.common.Repository;
import com.coinhitchhiker.vbtrader.common.TradingWindow;
import com.coinhitchhiker.vbtrader.common.VolatilityBreakoutRules;
import com.coinhitchhiker.vbtrader.simulator.SimulatorRepositoryImpl;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestVBRules extends BaseIT {

    private long SIMUL_END = 1564617600000L;
    // 2019년 March 1일 Friday  ~ 2019년 August 1일 Thursday AM 12:00:00
    private Repository repo = new SimulatorRepositoryImpl("BINANCE", "BTCUSDT", 1564531200000L, 1564617600000L - 1, 20, 0.7, 0.2);

    @Test
    public void test() {

        int LOOK_BEHIND = 5;
        //TradingWindow(String symbol, long startTimeStamp, long endTimeStamp, double openPrice, double highPrice, double closePrice, double lowPrice, double volume) {
        TradingWindow curTradingWindow = new TradingWindow("BTCUSDT", 1564617600000L,1564617600000L + 1000*60*20,0,0,0,0,4500);
        double score = VolatilityBreakoutRules.getVolumeMAScore_aggresive(repo.getLastNTradingWindow(LOOK_BEHIND+1, SIMUL_END+60_000)
                , curTradingWindow
                , 3
                , LOOK_BEHIND
                , 20
                , SIMUL_END+10*60_000
        ) ;

        assertThat(score).isEqualTo(1.0D);


    }
}
