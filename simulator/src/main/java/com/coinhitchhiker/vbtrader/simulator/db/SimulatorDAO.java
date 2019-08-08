package com.coinhitchhiker.vbtrader.simulator.db;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SimulatorDAO {

    int validateConnection();

    Integer getPeriodId(@Param("exchange") String exchange
            , @Param("symbol") String symbol
            , @Param("startDate") String startDate
            , @Param("endDate") String endDate);

    Double getBestResult(@Param("periodId") int periodId);

    void insertSimulResult(@Param("periodId") int periodId, @Param("simulResult") String simulResult);

}