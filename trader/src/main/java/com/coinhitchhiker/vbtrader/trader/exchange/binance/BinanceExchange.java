package com.coinhitchhiker.vbtrader.trader.exchange.binance;

import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.trader.config.EncryptorHelper;
import com.coinhitchhiker.vbtrader.trader.config.PropertyMapHandler;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.BinanceApiClientFactory;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.BinanceApiRestClient;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.TimeInForce;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.account.*;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.account.request.CancelOrderRequest;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.account.request.OrderStatusRequest;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.general.ExchangeInfo;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.general.FilterType;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.general.SymbolFilter;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.general.SymbolInfo;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.market.TickerPrice;
import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.exception.BinanceApiException;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BinanceExchange implements Exchange {

    public static final String BEAN_NAME_EXCHANGE_BINANCE = "exchange-binance";

    private static final Logger LOGGER = LoggerFactory.getLogger(BinanceExchange.class);

    private BinanceApiClientFactory factory = null;
    private BinanceApiRestClient client = null;

    private Gson gson = new Gson();
    private List<CoinInfo> coins = new ArrayList<>();
    private Map<String, Balance> balanceCache = new ConcurrentHashMap<>();

    @Value("#{propertyMapHandler.getString(${binance.api.key}, ${vbtrader.id})}")
    private String apiKey;

    @Value("#{propertyMapHandler.getString(${binance.api.secret}, ${vbtrader.id})}")
    private String apiSecret;

    @Value("${binance.api.secret.encryption.enabled}")
    private boolean apiSecretEncryptionEnabled;

    @Value("${trading.symbol}") private String TRADING_SYMBOL;

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private EncryptorHelper encryptorHelper;
    @Autowired private PropertyMapHandler propertyMapHandler;
    @Autowired private Repository binanceRepository;

    @PostConstruct
    public void init() {
        if(apiSecretEncryptionEnabled) {
            this.factory = BinanceApiClientFactory.newInstance(apiKey, encryptorHelper.getDecryptedValue(apiSecret));
        } else {
            this.factory = BinanceApiClientFactory.newInstance(apiKey, apiSecret);
        }

        this.client = factory.newRestClient();

        getCoinInfoList().forEach(c -> this.coins.add(c));
    }

    @Override
    public CoinInfo getCoinInfoBySymbol(String symbol) {
        return this.coins.stream()
                .filter(c -> c.getSymbol().equals(symbol))
                .findAny()
                .orElseThrow(() -> new RuntimeException("No corresponding coinInfo was found for " + symbol));
    }

    @Override
    public OrderInfo placeOrder(OrderInfo orderInfo) {

        String symbol = orderInfo.getSymbol();
        CoinInfo coinInfo = this.getCoinInfoBySymbol(symbol);

        NewOrder newOrder = null;
        if(orderInfo.getOrderSide() == OrderSide.BUY) {
            newOrder = NewOrder.limitBuy(symbol, TimeInForce.GTC, coinInfo.getCanonicalAmount(orderInfo.getAmount()), coinInfo.getCanonicalPrice(orderInfo.getPrice()));
        } else {
            newOrder = NewOrder.limitSell(symbol, TimeInForce.GTC, coinInfo.getCanonicalAmount(orderInfo.getAmount()), coinInfo.getCanonicalPrice(orderInfo.getPrice()));
        }

        long serverTime = client.getServerTime() - 500;
        long recvWindow = 5000L;

        newOrder = newOrder.recvWindow(recvWindow);
        newOrder = newOrder.timestamp(serverTime);

        NewOrderResponse response = client.newOrder(newOrder);

        OrderInfo result = orderInfo.clone();

        if (response == null) {
            result.setOrderStatus(OrderStatus.INCOMPLETE);
            LOGGER.info("Binance Order Place Error");
        } else {
            result.setExternalOrderId(String.valueOf(response.getOrderId()));
            result.setExecTimestamp(response.getTransactTime());
        }

        if(response.getStatus() != null && response.getStatus().equals("FILLED")) {
            List<Fill> fills = response.getFills();

            double priceExecuted = 0.00000D;
            double amountExecuted = 0.00000D;
            double fee = 0.00000D;
            String feeCurrency = null;

            for(Fill fill : fills) {
                amountExecuted += new Double(fill.getQty()).doubleValue();
                priceExecuted += (new Double(fill.getPrice())).doubleValue() * (new Double(fill.getQty())).doubleValue();
                fee += new Double(fill.getCommission());
                feeCurrency = fill.getCommissionAsset();
            }

            result.setFeePaid(fee);
            result.setFeeCurrency(feeCurrency);
            result.setPriceExecuted(amountExecuted != 0.00000D ? priceExecuted / amountExecuted : 0.0D);
            result.setAmountExecuted(amountExecuted);

            result.setOrderStatus(OrderStatus.COMPLETE);
        }

        return result;
    }

    @Override
    public OrderInfo cancelOrder(OrderInfo orderInfo) {
        CancelOrderRequest request = new CancelOrderRequest(orderInfo.getSymbol(), Long.valueOf(orderInfo.getExternalOrderId()));
        OrderInfo result = orderInfo.clone();
        CancelOrderResponse response = null;

        response = this.client.cancelOrder(request);
        result.setOrderStatus(convertOrderStatus(response.getStatus()));
        if(result.getOrderStatus().equals(OrderStatus.CANCELLED)) {
            return result;
        }
        return result;
    }

    @Override
    public OrderInfo getOrder(OrderInfo orderInfo) {
        OrderStatusRequest request = new OrderStatusRequest(orderInfo.getSymbol(), Long.valueOf(orderInfo.getExternalOrderId()));
        request.recvWindow(5000L);

        Order order = null;
        try {
            order = this.client.getOrderStatus(request);
        } catch(BinanceApiException e) {
            if(e.getMessage().contains("Order does not exist")) {
                LOGGER.error(e.getMessage());
                return null;
            }
            throw e;
        }

        OrderInfo result = orderInfo.clone();

        result.setAmountExecuted(Double.parseDouble(order.getExecutedQty()));
        result.setPriceExecuted(Double.parseDouble(order.getPrice()));
        result.setExecTimestamp(order.getTime());
        result.setOrderStatus(convertOrderStatus(order.getStatus().name()));

        return result;
    }

    private OrderStatus convertOrderStatus(String orderStatus) {
        if(orderStatus.equals("NEW")) {
            return OrderStatus.PENDING;
        } else if(orderStatus.equals("PARTIALLY_FILLED")) {
            return OrderStatus.PARTIALLY_FILLED;
        } else if(orderStatus.equals("CANCELED")){
            return OrderStatus.CANCELLED;
        } else if(orderStatus.equals("FILLED")){
            return OrderStatus.COMPLETE;
        } else{
            return OrderStatus.INCOMPLETE;
        }
    }

    @Override
    public List<CoinInfo> getCoinInfoList() {

        List<CoinInfo> result = new ArrayList<>();

        ExchangeInfo exchangeInfo = this.client.getExchangeInfo();

        for(SymbolInfo symbol : exchangeInfo.getSymbols()) {
            CoinInfo coinInfo = new CoinInfo();
            coinInfo.setExchange("BINANCE");
            coinInfo.setMarket(symbol.getQuoteAsset());

            String coin = symbol.getBaseAsset();
            coinInfo.setCoin(coin);
            coinInfo.setSymbol(symbol.getSymbol());

            for(SymbolFilter filter : symbol.getFilters()){
                if(filter.getFilterType() == FilterType.PRICE_FILTER) {
                    coinInfo.setUnitPrice(new Double(filter.getTickSize()));
                    coinInfo.setMinUnitPrice(new Double(filter.getMinPrice()));
                }
                if(filter.getFilterType() == FilterType.LOT_SIZE) {
                    coinInfo.setUnitAmount(new Double(filter.getStepSize()));
                    coinInfo.setMinUnitAmount(new Double(filter.getMinQty()));
                }
            }
            result.add(coinInfo);
        }

        return result;
    }

    @Override
    public double getCurrentPrice(String symbol) {
        TickerPrice tickerPrice = this.client.getPrice(symbol);
        return Double.valueOf(tickerPrice.getPrice());
    }

    @Override
    public Map<String, Balance> getBalance() {
        Map<String, Balance> balance = new HashMap<>();

        try {

            long serverTime = this.client.getServerTime();
            long recvWindow = 5000L;

            // Get account balances
            Account account = this.client.getAccount(recvWindow, serverTime);
            List<AssetBalance> acctBal = account.getBalances();
            for (AssetBalance bal : acctBal) {
                Balance bal2 = new Balance();
                bal2.setExchange("BINANCE");
                String coin = bal.getAsset();
                bal2.setCoin(coin);
                bal2.setAvailableForTrade( new Double(StringUtils.isEmpty(bal.getFree()) ? "0" : bal.getFree()));
                bal2.setAvailableForWithdraw(bal2.getAvailableForTrade());
                bal2.setBalanceTotal(new Double(StringUtils.isEmpty(bal.getLocked()) ? "0" : bal.getLocked())+ bal2.getAvailableForTrade());
                if(bal2.getBalanceTotal() == 0.0D) continue;
                balance.put(bal2.getCoin(), bal2);
            }

        } catch (Exception e) {
            balance = null;
            LOGGER.error("BINANCE GetAvailableBalance threw an exception", e);
        }

        return balance;
    }

}
