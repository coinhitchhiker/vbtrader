package com.coinhitchhiker.vbtrader.trader.db;

import com.coinhitchhiker.vbtrader.common.model.Candle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TraderDAO {

    int validateConnection();

    void logCompleteTransaction(@Param("completeTransaction") String json);

    List<Candle> getBinanceCandles(@Param("symbol") String symbol, @Param("interval") String interval, @Param("openTime") long openTime, @Param("closeTime") long closeTime);

}
