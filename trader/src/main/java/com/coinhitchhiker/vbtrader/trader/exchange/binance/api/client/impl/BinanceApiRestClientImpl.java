package com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.impl;

import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.BinanceApiRestClient;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.constant.BinanceApiConstants;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.account.*;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.account.request.AllOrdersRequest;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.account.request.CancelOrderRequest;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.account.request.OrderRequest;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.account.request.OrderStatusRequest;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.general.Asset;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.general.ExchangeInfo;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.market.*;

import java.util.List;

import static com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.impl.BinanceApiServiceGenerator.createService;
import static com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.impl.BinanceApiServiceGenerator.executeSync;

/**
 * Implementation of Binance's REST API using Retrofit with synchronous/blocking method calls.
 */
public class BinanceApiRestClientImpl implements BinanceApiRestClient {

  private final BinanceApiService binanceApiService;

  public BinanceApiRestClientImpl(String apiKey, String secret) {
    binanceApiService = createService(BinanceApiService.class, apiKey, secret);
  }

  // General endpoints

  public void ping() {
    executeSync(binanceApiService.ping());
  }

  public Long getServerTime() {
    return executeSync(binanceApiService.getServerTime()).getServerTime();
  }

  public ExchangeInfo getExchangeInfo() {
    return executeSync(binanceApiService.getExchangeInfo());
  }

  public List<Asset> getAllAssets() {
    return executeSync(binanceApiService.getAllAssets(
        BinanceApiConstants.ASSET_INFO_API_BASE_URL + "assetWithdraw/getAllAsset.html"));
  }

  // Market Data endpoints
  public OrderBook getOrderBook(String symbol, Integer limit) {
    return executeSync(binanceApiService.getOrderBook(symbol, limit));
  }

  public List<TradeHistoryItem> getTrades(String symbol, Integer limit) {
    return executeSync(binanceApiService.getTrades(symbol, limit));
  }

  public List<TradeHistoryItem> getHistoricalTrades(String symbol, Integer limit, Long fromId) {
    return executeSync(binanceApiService.getHistoricalTrades(symbol, limit, fromId));
  }

  public List<AggTrade> getAggTrades(String symbol, String fromId, Integer limit, Long startTime,
      Long endTime) {
    return executeSync(binanceApiService.getAggTrades(symbol, fromId, limit, startTime, endTime));
  }

  public List<AggTrade> getAggTrades(String symbol) {
    return getAggTrades(symbol, null, null, null, null);
  }

  public List<Candlestick> getCandlestickBars(String symbol, CandlestickInterval interval,
      Integer limit, Long startTime, Long endTime) {
    return executeSync(binanceApiService.getCandlestickBars(symbol, interval.getIntervalId(), limit,
        startTime, endTime));
  }


  public List<Candlestick> getCandlestickBars(String symbol, CandlestickInterval interval) {
    return getCandlestickBars(symbol, interval, null, null, null);
  }


  public TickerStatistics get24HrPriceStatistics(String symbol) {
    return executeSync(binanceApiService.get24HrPriceStatistics(symbol));
  }


  public List<TickerStatistics> getAll24HrPriceStatistics() {
    return executeSync(binanceApiService.getAll24HrPriceStatistics());
  }


  public TickerPrice getPrice(String symbol) {
    return executeSync(binanceApiService.getLatestPrice(symbol));
  }


  public List<TickerPrice> getAllPrices() {
    return executeSync(binanceApiService.getLatestPrices());
  }


  public List<BookTicker> getBookTickers() {
    return executeSync(binanceApiService.getBookTickers());
  }


  public NewOrderResponse newOrder(NewOrder order) {
    return executeSync(binanceApiService.newOrder(order.getSymbol(), order.getSide(),
        order.getType(), order.getTimeInForce(), order.getQuantity(), order.getPrice(),
        order.getNewClientOrderId(), order.getStopPrice(), order.getIcebergQty(),
        order.getRecvWindow(), order.getTimestamp()));
  }


  public void newOrderTest(NewOrder order) {
    executeSync(binanceApiService.newOrderTest(order.getSymbol(), order.getSide(), order.getType(),
        order.getTimeInForce(), order.getQuantity(), order.getPrice(), order.getNewClientOrderId(),
        order.getStopPrice(), order.getIcebergQty(), order.getRecvWindow(), order.getTimestamp()));
  }

  // Account endpoints

  
  public Order getOrderStatus(OrderStatusRequest orderStatusRequest) {
    return executeSync(binanceApiService.getOrderStatus(orderStatusRequest.getSymbol(),
        orderStatusRequest.getOrderId(), orderStatusRequest.getOrigClientOrderId(),
        orderStatusRequest.getRecvWindow(), orderStatusRequest.getTimestamp()));
  }

  
  public CancelOrderResponse cancelOrder(CancelOrderRequest cancelOrderRequest) {
    return executeSync(binanceApiService.cancelOrder(cancelOrderRequest.getSymbol(),
        cancelOrderRequest.getOrderId(), cancelOrderRequest.getOrigClientOrderId(),
        cancelOrderRequest.getNewClientOrderId(), cancelOrderRequest.getRecvWindow(),
        cancelOrderRequest.getTimestamp()));
  }

  
  public List<Order> getOpenOrders(OrderRequest orderRequest) {
    return executeSync(binanceApiService.getOpenOrders(orderRequest.getSymbol(),
        orderRequest.getRecvWindow(), orderRequest.getTimestamp()));
  }

  
  public List<Order> getAllOrders(AllOrdersRequest orderRequest) {
    return executeSync(
        binanceApiService.getAllOrders(orderRequest.getSymbol(), orderRequest.getOrderId(),
            orderRequest.getLimit(), orderRequest.getRecvWindow(), orderRequest.getTimestamp()));
  }

  
  public Account getAccount(Long recvWindow, Long timestamp) {
    return executeSync(binanceApiService.getAccount(recvWindow, timestamp));
  }

  
  public Account getAccount() {
    return getAccount(BinanceApiConstants.DEFAULT_RECEIVING_WINDOW, System.currentTimeMillis());
  }

  
  public List<Trade> getMyTrades(String symbol, Integer limit, Long fromId, Long recvWindow,
      Long timestamp) {
    return executeSync(binanceApiService.getMyTrades(symbol, limit, fromId, recvWindow, timestamp));
  }

  
  public List<Trade> getMyTrades(String symbol, Integer limit) {
    return getMyTrades(symbol, limit, null, BinanceApiConstants.DEFAULT_RECEIVING_WINDOW,
        System.currentTimeMillis());
  }

  
  public List<Trade> getMyTrades(String symbol) {
    return getMyTrades(symbol, null, null, BinanceApiConstants.DEFAULT_RECEIVING_WINDOW,
        System.currentTimeMillis());
  }

  
  public WithdrawResponse withdraw(String asset, String address, String addressTag, String amount, String name, Long recvWindow,
      Long timestamp) {
    return executeSync(binanceApiService.withdraw(asset, address, addressTag, amount, name, recvWindow, timestamp));
  }

  
  public WithdrawResponse withdraw(String asset, String address, String addressTag, String amount, String name) {
    return executeSync(binanceApiService.withdraw(asset, address, addressTag, amount, name,
        BinanceApiConstants.DEFAULT_RECEIVING_WINDOW, System.currentTimeMillis()));
  }

  
  public DepositHistory getDepositHistory(String asset) {
    return executeSync(binanceApiService.getDepositHistory(asset,
        BinanceApiConstants.DEFAULT_RECEIVING_WINDOW, System.currentTimeMillis()));
  }

  
  public WithdrawHistory getWithdrawHistory(String asset) {
    return executeSync(binanceApiService.getWithdrawHistory(asset,
        BinanceApiConstants.DEFAULT_RECEIVING_WINDOW, System.currentTimeMillis()));
  }

  
  public DepositAddress getDepositAddress(String asset, long recvWindow, long serverTime) {
    return executeSync(binanceApiService.getDepositAddress(asset,recvWindow, serverTime));
  }

  
  public AssetDetail getAssetDetail(long recvWindow, long serverTime) {
    return executeSync(binanceApiService.getAssetDetail(recvWindow, serverTime));
  }

  // User stream endpoints

  
  public String startUserDataStream() {
    return executeSync(binanceApiService.startUserDataStream()).toString();
  }

  
  public void keepAliveUserDataStream(String listenKey) {
    executeSync(binanceApiService.keepAliveUserDataStream(listenKey));
  }

  
  public void closeUserDataStream(String listenKey) {
    executeSync(binanceApiService.closeAliveUserDataStream(listenKey));
  }
}
