package com.coinhitchhiker.vbtrader.simulator.db;

import com.coinhitchhiker.vbtrader.common.model.Candle;
import com.coinhitchhiker.vbtrader.simulator.TopSimulResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SimulatorDAO {

    int validateConnection();

    Integer getPeriodId(@Param("exchange") String exchange
            , @Param("symbol") String symbol
            , @Param("startDate") String startDate
            , @Param("endDate") String endDate);

    Double getBestResult(@Param("periodId") int periodId);

    void insertSimulResult(@Param("periodId") int periodId, @Param("simulResult") String simulResult);

    List<TopSimulResult> getTopSimulResults();

    void insertValidationResult(@Param("periodId") int periodId, @Param("simulResultId") int simulResultId, @Param("validationResult") String validationResult);

    List<Candle> getBinanceCandles(@Param("symbol") String symbol,
                                   @Param("interval") String interval,
                                   @Param("simulStart") long simulStart,
                                   @Param("simulEnd") long simulEnd);

}
