package com.coinhitchhiker.vbtrader.simulator.test;

import com.coinhitchhiker.vbtrader.common.model.Repository;
import com.coinhitchhiker.vbtrader.common.model.TradingWindow;
import com.coinhitchhiker.vbtrader.simulator.SimulatorRepositoryImpl;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SimulatorRepositoryImplTest {

    private Logger LOGGER = LoggerFactory.getLogger(SimulatorRepositoryImplTest.class);

    private Repository binanceRepo = new SimulatorRepositoryImpl("BINANCE", "BTCUSDT", 1551398400000L, 1564617600000L, 43, 0.7, 0.2);
    private static final int tradingWindowSizeInMinutes = 43;


    @Test
    @Ignore
    public void getLastN() throws IOException {
        String pattern = "yyyy-MM-dd HH:mm:ss";
        DateTimeFormatter formatter = DateTimeFormat.forPattern(pattern).withZoneUTC();

        String dt = "2019-08-01 23:57:00";
        LOGGER.info("curTime {}", dt);

        long curTimestamp = DateTime.parse(dt, formatter).getMillis();
        List<TradingWindow> result = this.binanceRepo.getLastNTradingWindow(20, curTimestamp);
        assertThat(result.size()).isEqualTo(20);
    }

    @Test
    @Ignore
    public void getBinanceCandle() {
        SimulatorRepositoryImpl repo = new SimulatorRepositoryImpl("BINANCE", "BTCUSDT",
                new DateTime(2019,7,14,0,0, DateTimeZone.UTC).getMillis(),
                new DateTime(2019,8,1,0,0, DateTimeZone.UTC).getMillis(),
                        720,
                0.7,
                0.2
        );

        assertThat(repo.getTradingWindows().size()).isEqualTo(37);
    }

    @Test
//    @Ignore
    public void getBitmexCandle()  {
        Repository bitmexRepo = new SimulatorRepositoryImpl("BITMEX", "XBTUSD", 1564617600000L, 1565740800000L, 43, 0.7, 0.2);


    }
}
