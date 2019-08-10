package com.coinhitchhiker.vbtrader.trader.db;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TraderDAO {

    int validateConnection();

    void logCompleteTransaction(@Param("completeTransaction") String json);

}
