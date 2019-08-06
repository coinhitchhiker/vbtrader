package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.Repository;
import com.coinhitchhiker.vbtrader.common.TradingWindow;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SimulatorRepositoryImplTest {

    private Logger LOGGER = LoggerFactory.getLogger(SimulatorRepositoryImplTest.class);

    private Repository repo = null;
    private static final int tradingWindowSizeInMinutes = 43;

    @BeforeAll
    public void init() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("com/coinhitchhiker/vbtrader/simulator/BTCUSDT-0301-0801.txt").getFile());
        String path = file.getAbsolutePath();
        System.out.println(path);

        this.repo = new SimulatorRepositoryImpl(path, tradingWindowSizeInMinutes);

        ((SimulatorRepositoryImpl)this.repo).getTradingWindows().forEach(tw -> LOGGER.info("{}", tw));
    }

    @Test
    public void getLastN() throws IOException {
        String pattern = "yyyy-MM-dd HH:mm:ss";
        DateTimeFormatter formatter = DateTimeFormat.forPattern(pattern).withZoneUTC();

        String dt = "2019-08-01 23:57:00";
        LOGGER.info("curTime {}", dt);

        long curTimestamp = DateTime.parse(dt, formatter).getMillis();
        List<TradingWindow> result = repo.getLastNTradingWindow(20, tradingWindowSizeInMinutes, curTimestamp);
        assertEquals(result.size(), 20);

        result.forEach(tw -> LOGGER.info("{}", tw));

    }
}
