package com.coinhitchhiker.vbtrader.common;

import java.util.List;

public interface Exchange {

    void refreshTradingWindows();

    double getBestAsk();

    double getBestBid();

    OrderInfo placeOrder(OrderInfo orderInfo);

    OrderInfo cancelOrder(OrderInfo orderInfo);

    OrderInfo getOrder(OrderInfo orderInfo);

    List<CoinInfo> getCoinInfoList();

    CoinInfo getCoinInfoBySymbol(String symbol);

    double getCurrentPrice(String symbol);

}
