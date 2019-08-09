package com.coinhitchhiker.vbtrader.trader.db;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TraderDAO {

    int validateConnection();

}
