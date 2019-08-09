package com.coinhitchhiker.vbtrader.simulator.test;

import com.coinhitchhiker.vbtrader.common.Repository;
import com.coinhitchhiker.vbtrader.common.TradingWindow;
import com.coinhitchhiker.vbtrader.simulator.SimulatorRepositoryImpl;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SimulatorRepositoryImplTest {

    private Logger LOGGER = LoggerFactory.getLogger(SimulatorRepositoryImplTest.class);

    private static Repository repo = null;
    private static final int tradingWindowSizeInMinutes = 43;

    @BeforeClass
    public static void init() throws IOException {
        ClassLoader classLoader = SimulatorRepositoryImplTest.class.getClassLoader();
        File file = new File(classLoader.getResource("com/coinhitchhiker/vbtrader/simulator/BTCUSDT-0301-0801.txt").getFile());
        String path = file.getAbsolutePath();
        System.out.println(path);

        SimulatorRepositoryImplTest.repo = new SimulatorRepositoryImpl(path, tradingWindowSizeInMinutes);

//        ((SimulatorRepositoryImpl)this.repo).getTradingWindows().forEach(tw -> LOGGER.info("{}", tw));
    }

    @Test
//    @Ignore
    public void getLastN() throws IOException {
        String pattern = "yyyy-MM-dd HH:mm:ss";
        DateTimeFormatter formatter = DateTimeFormat.forPattern(pattern).withZoneUTC();

        String dt = "2019-08-01 23:57:00";
        LOGGER.info("curTime {}", dt);

        long curTimestamp = DateTime.parse(dt, formatter).getMillis();
        List<TradingWindow> result = SimulatorRepositoryImplTest.repo.getLastNTradingWindow(20, curTimestamp);
        assertThat(result.size()).isEqualTo(20);

//        result.forEach(tw -> LOGGER.info("{}", tw));
    }

    @Test
    public void getBinanceCandle() {
        SimulatorRepositoryImpl repo = new SimulatorRepositoryImpl("BTCUSDT",
                new DateTime(2019,7,14,0,0, DateTimeZone.UTC).getMillis(),
                new DateTime(2019,8,1,0,0, DateTimeZone.UTC).getMillis(),
                        720);

        assertThat(repo.getTradingWindows().size()).isEqualTo(37);
    }
}
