package com.coinhitchhiker.vbtrader.simulator.test;

import com.coinhitchhiker.vbtrader.simulator.SimulResult;
import com.coinhitchhiker.vbtrader.simulator.db.SimulatorDAO;
import com.google.gson.Gson;
import org.joda.time.DateTime;
import org.junit.Ignore;
import org.junit.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

//@Ignore
public class ITDBTest extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ITDBTest.class);

    @Autowired
    private SimulatorDAO simulatorDAO;

    @Test
    public void  testConnection() {
        simulatorDAO.validateConnection();
    }

    @Test
    public void testGetPeriodId() {
        Integer id = simulatorDAO.getPeriodId("BINANCE", "BTCUSDT", "20190701", "20190801");
        assertThat(id).isNotNegative();

        id = simulatorDAO.getPeriodId("XXX", "BTCUSDT", "20190701", "20190801");
        assertThat(id).isNull();

    }

    @Test
    public void testGetBestResult() {
        Double bestResult = simulatorDAO.getBestResult(0);
        assertThat(bestResult).isNull();
    }

    @Test
    public void insertSimulResult() {
        SimulResult result = new SimulResult();

        result.setEND_USD_BALANCE(20000);
        result.setH_VOLUME_WEIGHT(0.7);
        result.setMA_MIN(3);
        result.setPRICE_MA_WEIGHT(0.7);
        result.setSIMUL_END("20190701");
        result.setSIMUL_START("20190801");
        result.setSLIPPAGE(0.1);
        result.setSTART_USD_BALANCE(10000);
        result.setTRADING_WINDOW_LOOK_BEHIND(20);
        result.setTRADING_WINDOW_SIZE_IN_MIN(240);
        result.setVOLUME_MA_WEIGHT(0.3);

        Gson gson = new Gson();
        String json = gson.toJson(result);

        Integer id = simulatorDAO.getPeriodId("BINANCE", "BTCUSDT", "20190701", "20190801");
        simulatorDAO.insertSimulResult(id, json);

        Double bestResult = simulatorDAO.getBestResult(id);
        assertThat(bestResult).isEqualTo(20000);

    }

}
