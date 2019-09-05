package com.coinhitchhiker.vbtrader.common.model;

import java.util.List;
import java.util.Map;

public interface Exchange {

    OrderInfo placeOrder(OrderInfo orderInfo, boolean makerOrder);

    OrderInfo cancelOrder(OrderInfo orderInfo);

    OrderInfo getOrder(OrderInfo orderInfo);

    List<CoinInfo> getCoinInfoList();

    CoinInfo getCoinInfoBySymbol(String symbol);

    double getCurrentPrice(String symbol);

    Map<String, Balance> getBalance();

}
