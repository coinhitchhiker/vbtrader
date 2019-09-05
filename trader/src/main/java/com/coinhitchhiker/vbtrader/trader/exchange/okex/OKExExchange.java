package com.coinhitchhiker.vbtrader.trader.exchange.okex;

import com.coinhitchhiker.vbtrader.common.RESTAPIResponseErrorHandler;
import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.trader.config.EncryptorHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.HmacUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.Base64Utils;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class OKExExchange implements Exchange {

    private Logger LOGGER = LoggerFactory.getLogger(OKExExchange.class);

    @Value("#{propertyMapHandler.getString(${okex.api.key}, ${vbtrader.id})}")
    private String apiKey;

    @Value("#{propertyMapHandler.getString(${okex.api.secret}, ${vbtrader.id})}")
    private String apiSecret;

    @Value("#{propertyMapHandler.getString(${okex.api.passphrase}, ${vbtrader.id})}")
    private String apiPassPhrase;

    @Value("${okex.api.secret.encryption.enabled}")
    private boolean apiSecretEncryptionEnabled;

    @Autowired private EncryptorHelper encryptorHelper;

    private static final String API_SERVER_URL = "https://www.okex.com";
    private static final String GET_TOKEN_PAIR_DETAILS = "/api/spot/v3/instruments";
    private static final String ORDERS = "/api/margin/v3/orders";
    private static final String GET_BALANCE = "/api/margin/v3/accounts";
    private static final String BORROW = "/api/margin/v3/accounts/borrow";
    private static final String REPAY = "/api/margin/v3/accounts/repayment";

    private RestTemplate restTemplate = new RestTemplate();
    private Gson gsonMapper = new Gson();
    private List<CoinInfo> coins = new ArrayList<>();
    private TradingMode MODE;

    public OKExExchange(TradingMode MODE) {
        this.MODE = MODE;
    }

    @PostConstruct
    public void init() {
        this.restTemplate.setErrorHandler(new RESTAPIResponseErrorHandler());

        getCoinInfoList().forEach(c -> this.coins.add(c));
    }

    private OrderInfo placeOrderShort(OrderInfo orderInfo) {

        if(!this.MODE.equals(TradingMode.SHORT)) throw new RuntimeException("This method should be called in SHORT mode");

        String borrow_id = null;
        String symbol = orderInfo.getSymbol();
        CoinInfo coinInfo = getCoinInfoBySymbol(symbol);
        String coin = symbol.split("-")[0];
        String client_oid = UUID.randomUUID().toString().replace("-", "");
        double amount = orderInfo.getAmount();

        if(orderInfo.getOrderSide() == OrderSide.SELL) {
            // borrow coin to short
            borrow_id = borrow(symbol, coin, amount);

            Map<String, Object> params = new HashMap<>();

            params.put("client_oid", client_oid);
            params.put("instrument_id", symbol);
            params.put("type", "limit");
            params.put("side", "sell");
            params.put("margin_trading", 2);
            params.put("price", coinInfo.getCanonicalPrice(orderInfo.getPrice()));
            params.put("size", coinInfo.getCanonicalAmount(amount));

            HttpMethod method = HttpMethod.POST;
            HttpHeaders headers = getHttpHeaders(method, ORDERS, params);
            String body = jsonStringfy(params);
            String response = this.restTemplate.exchange(API_SERVER_URL + ORDERS, method, new HttpEntity<>(body, headers), String.class).getBody();
            Map<String, Object> parsedResponse = this.gsonMapper.fromJson(response, Map.class);
            String order_id = (String)parsedResponse.get("order_id");
            orderInfo.setExternalOrderId(order_id);
            orderInfo.setBorrow_id(borrow_id);
            orderInfo.setClientOid(client_oid);

            OrderInfo placedOrderInfo = getOrder(orderInfo);

            return placedOrderInfo;
        } else {
            borrow_id = orderInfo.getBorrow_id();
            double amountToRepay = getBalance().get(coin).getBorrowed();

            Map<String, Object> params = new HashMap<>();

            params.put("client_oid", client_oid);
            params.put("instrument_id", symbol);
            params.put("type", "limit");
            params.put("side", "buy");
            params.put("margin_trading", 2);
            params.put("price", coinInfo.getCanonicalPrice(orderInfo.getPrice()));
            params.put("size", coinInfo.getCanonicalAmount(amountToRepay));

            HttpMethod method = HttpMethod.POST;
            HttpHeaders headers = getHttpHeaders(method, ORDERS, params);
            String body = jsonStringfy(params);
            String response = this.restTemplate.exchange(API_SERVER_URL + ORDERS, method, new HttpEntity<>(body, headers), String.class).getBody();
            Map<String, Object> parsedResponse = this.gsonMapper.fromJson(response, Map.class);
            String order_id = (String)parsedResponse.get("order_id");
            orderInfo.setExternalOrderId(order_id);
            orderInfo.setClientOid(client_oid);

            OrderInfo placedOrderInfo = getOrder(orderInfo);

            // repay the borrowed amount
            repay(symbol, coin, amountToRepay, borrow_id);

            return placedOrderInfo;
        }

    }

    private OrderInfo placeOrderLong(OrderInfo orderInfo) {
        if(!this.MODE.equals(TradingMode.LONG)) throw new RuntimeException("This method should be called in LONG mode");

        String symbol = orderInfo.getSymbol();
        String coin = symbol.split("-")[0];
        CoinInfo coinInfo = getCoinInfoBySymbol(symbol);
        String client_oid = UUID.randomUUID().toString().replace("-", "");
        double amount = orderInfo.getAmount();

        Map<String, Object> params = new HashMap<>();

        params.put("client_oid", client_oid);
        params.put("instrument_id", symbol);
        params.put("type", "limit");
        if(orderInfo.getOrderSide() == OrderSide.BUY) {
            params.put("side", "buy");
            params.put("size", coinInfo.getCanonicalAmount(amount));
        } else {
            params.put("side", "sell");
            params.put("size", getBalance().get(coin).getAvailableForTrade());
        }
        params.put("margin_trading", 2);
        params.put("price", coinInfo.getCanonicalPrice(orderInfo.getPrice()));


        HttpMethod method = HttpMethod.POST;
        HttpHeaders headers = getHttpHeaders(method, ORDERS, params);
        String body = jsonStringfy(params);
        String response = this.restTemplate.exchange(API_SERVER_URL + ORDERS, method, new HttpEntity<>(body, headers), String.class).getBody();
        Map<String, Object> parsedResponse = this.gsonMapper.fromJson(response, Map.class);
        String order_id = (String)parsedResponse.get("order_id");
        orderInfo.setExternalOrderId(order_id);
        orderInfo.setClientOid(client_oid);

        OrderInfo placedOrderInfo = getOrder(orderInfo);

        return placedOrderInfo;
    }

    @Override
    public OrderInfo placeOrder(OrderInfo orderInfo, boolean makerOrder) {
        if(this.MODE.equals(TradingMode.SHORT)) {
            return placeOrderShort(orderInfo);
        } else {
            return placeOrderLong(orderInfo);
        }
    }

    @Override
    public OrderInfo cancelOrder(OrderInfo orderInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public OrderInfo getOrder(OrderInfo orderInfo) {
        Map<String, Object> params = new HashMap<>();
        params.put("instrument_id", orderInfo.getSymbol());
        params.put("order_id", orderInfo.getExternalOrderId());
        params.put("client_oid", orderInfo.getClientOid());

        HttpMethod method = HttpMethod.GET;
        String requestPath = ORDERS + "/" + orderInfo.getExternalOrderId() + "?" + mapToQueryString(params);

        HttpHeaders headers = getHttpHeaders(method, requestPath, new HashMap<>());

        String response = this.restTemplate.exchange(
                API_SERVER_URL + requestPath
                , method
                , new HttpEntity<>(null, headers)
                , String.class).getBody();

        Map<String, Object> map = this.gsonMapper.fromJson(response, Map.class);
        orderInfo.setOrderStatus(this.mapOrderStatus((String)map.get("status")));
        orderInfo.setAmountExecuted(Double.valueOf((String)map.get("filled_size")));
        orderInfo.setPriceExecuted(Double.valueOf((String)map.get("price_avg")));

        return orderInfo;
    }

    @Override
    public List<CoinInfo> getCoinInfoList() {
        List<CoinInfo> result = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        HttpMethod method = HttpMethod.GET;

        HttpHeaders headers = getHttpHeaders(method, GET_TOKEN_PAIR_DETAILS, params);

        String response = this.restTemplate.exchange(
                    API_SERVER_URL + GET_TOKEN_PAIR_DETAILS
                    , method
                    , new HttpEntity<>(null, headers)
                    , String.class).getBody();

        Object[] objs = this.gsonMapper.fromJson(response, Object[].class);
        for(Object o : objs) {
            Map<String, Object> entry = (Map)o;
            CoinInfo c = new CoinInfo();
            c.setMarket((String)entry.get("quote_currency"));
            c.setSymbol((String)entry.get("instrument_id"));
            c.setCoin((String)entry.get("base_currency"));
            c.setMinUnitAmount(Double.parseDouble((String)entry.get("min_size")));
            c.setUnitAmount(Double.parseDouble((String)entry.get("size_increment")));
            c.setUnitPrice(Double.parseDouble((String)entry.get("tick_size")));
            c.setExchange(ExchangeEnum.OKEX);
            result.add(c);
        }

        return result;
    }

    @Override
    public CoinInfo getCoinInfoBySymbol(String symbol) {
        return this.coins.stream()
                .filter(c -> c.getSymbol().equals(symbol))
                .findAny()
                .orElseThrow(() -> new RuntimeException("No corresponding coinInfo was found for " + symbol));
    }

    @Override
    public double getCurrentPrice(String symbol) {
        String url = API_SERVER_URL + "/api/spot/v3/instruments/" + symbol + "/ticker";
        Map<String, Object> params = new HashMap<>();
        HttpMethod method = HttpMethod.GET;

        HttpHeaders headers = getHttpHeaders(method, "/api/spot/v3/instruments/" + symbol + "/ticker", params);

        String response = this.restTemplate.exchange(url,  HttpMethod.GET
                , new HttpEntity<>(null, headers)
                , String.class).getBody();

        Map<String, Object> parsedResponse = this.gsonMapper.fromJson(response, Map.class);
        return Double.valueOf((String)parsedResponse.get("last"));
    }

    public String borrow(String symbol, String coin, double amount) {
        Map<String, Object> params = new HashMap<>();
        params.put("instrument_id", symbol);
        params.put("currency", coin);

        CoinInfo coinInfo = this.getCoinInfoBySymbol(symbol);
        params.put("amount", coinInfo.getCanonicalAmount(amount));

        HttpMethod method = HttpMethod.POST;
        HttpHeaders headers = getHttpHeaders(method, BORROW, params);
        String body = jsonStringfy(params);

        String response = this.restTemplate.exchange(API_SERVER_URL + BORROW, method, new HttpEntity<>(body, headers), String.class).getBody();
        Map<String, Object> parsedResponse = this.gsonMapper.fromJson(response, Map.class);
        return (String)parsedResponse.get("borrow_id");
    }

    public String repay(String symbol, String coin, double amount, String borrow_id) {
        Map<String, Object> params = new HashMap<>();
        params.put("instrument_id", symbol);
        params.put("currency", coin);

        CoinInfo coinInfo = this.getCoinInfoBySymbol(symbol);
        params.put("amount", coinInfo.getCanonicalAmount(amount));

        params.put("borrow_id", borrow_id);
        HttpMethod method = HttpMethod.POST;
        HttpHeaders headers = getHttpHeaders(method, REPAY, params);
        String body = jsonStringfy(params);

        String response = this.restTemplate.exchange(API_SERVER_URL + REPAY, method, new HttpEntity<>(body, headers), String.class).getBody();
        Map<String, Object> parsedResponse = this.gsonMapper.fromJson(response, Map.class);
        return (String)parsedResponse.get("repayment_id");
    }

    @Override
    public Map<String, Balance> getBalance() {
        HttpMethod method = HttpMethod.GET;
        HttpHeaders headers = getHttpHeaders(method, GET_BALANCE, new HashMap<>());

        Map<String, Balance> result = new HashMap<>();
        String response = this.restTemplate.exchange(
                API_SERVER_URL + GET_BALANCE
                , method
                , new HttpEntity<>(null, headers)
                , String.class).getBody();


        Object[] objs = this.gsonMapper.fromJson(response, Object[].class);
        for(Object o : objs) {
            Map<String, Object> balance = (Map)o;
            String productId = (String)balance.get("product_id");
            if(!productId.equals("BTC-USDT")) continue;

            Map<String, String> btcInfo = (Map)balance.get("currency:BTC");
            Balance btcBal = new Balance();
            btcBal.setAvailableForTrade(Double.parseDouble(btcInfo.get("available")));
            btcBal.setAvailableForWithdraw(Double.parseDouble(btcInfo.get("available")));
            btcBal.setBalanceTotal(Double.parseDouble(btcInfo.get("balance")));
            btcBal.setCoin("BTC");
            btcBal.setBorrowed(Double.valueOf(btcInfo.get("borrowed")) + Double.valueOf(btcInfo.get("lending_fee")));
            btcBal.setExchange(ExchangeEnum.OKEX);
            result.put(btcBal.getCoin(), btcBal);

            Map<String, String> usdtInfo = (Map)balance.get("currency:USDT");
            Balance usdtBal = new Balance();
            usdtBal.setAvailableForTrade(Double.parseDouble(usdtInfo.get("available")));
            usdtBal.setAvailableForWithdraw(Double.parseDouble(usdtInfo.get("available")));
            usdtBal.setBalanceTotal(Double.parseDouble(usdtInfo.get("balance")));
            usdtBal.setCoin("USDT");
            usdtBal.setBorrowed(Double.valueOf(usdtInfo.get("borrowed")) + Double.valueOf(btcInfo.get("lending_fee")));
            usdtBal.setExchange(ExchangeEnum.OKEX);
            result.put(usdtBal.getCoin(), usdtBal);

        }
        return result;
    }

    private HttpHeaders getHttpHeaders(HttpMethod method, String requestPath, Map<String, Object> params) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8);

        headers.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0");
        headers.add("OK-ACCESS-KEY", this.apiKey);
        headers.add("OK-ACCESS-PASSPHRASE", this.apiPassPhrase);
        String timestamp = DateTime.now().withZone(DateTimeZone.UTC).toString(DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        headers.add("OK-ACCESS-TIMESTAMP", timestamp);

        if(method.equals(HttpMethod.GET)) {
            headers.add("OK-ACCESS-SIGN", sign(timestamp, method, requestPath, new HashMap<>()));
        } else {
            headers.add("OK-ACCESS-SIGN", sign(timestamp, method, requestPath, params));
        }

        return headers;
    }

    private String sign(String timestamp, HttpMethod method, String requestPath, Map<String, Object> params) {
        String json = jsonStringfy(params);
        if(json.equals("{}")) {
            json = "";
        }
        String toSign = timestamp + method.name() + requestPath + json;
        String decSecret;
        if(apiSecretEncryptionEnabled) {
            decSecret = this.encryptorHelper.getDecryptedValue(this.apiSecret);
        } else {
            decSecret = this.apiSecret;
        }
        String result = Base64Utils.encodeToString(HmacUtils.hmacSha256(decSecret, toSign));
        return result;
    }

    private String jsonStringfy(Map<String, Object> params) {
        if(params == null) {
            return null;
        }

        ObjectMapper mapper = new ObjectMapper();
        String json = null;
        try {
            json = mapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return json;
    }

    private OrderStatus mapOrderStatus(String status) {
        if(status.equals("open")) {
            return OrderStatus.PENDING;
        } else if(status.equals("part_filled")) {
            return  OrderStatus.INCOMPLETE;
        } else if(status.equals("canceling")) {
            return OrderStatus.INCOMPLETE;
        } else if(status.equals("filled")) {
            return OrderStatus.COMPLETE;
        } else if(status.equals("cancelled")) {
            return OrderStatus.CANCELLED;
        } else if(status.equals("failure")) {
            return OrderStatus.INCOMPLETE;
        } else if(status.equals("ordering")) {
            return OrderStatus.PENDING;
        } else {
            throw new RuntimeException("Invalid status has been received: " + status);
        }
    }

    private String mapToQueryString(Map<String, Object> map) {
        String result = map.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
        return result;
    }

    public void setMODE(TradingMode MODE) {
        this.MODE = MODE;
    }
}
