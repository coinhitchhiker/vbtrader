package com.coinhitchhiker.vbtrader.trader.exchange.bitmex;

import com.coinhitchhiker.vbtrader.common.*;
import com.coinhitchhiker.vbtrader.common.model.*;
import com.coinhitchhiker.vbtrader.trader.config.EncryptorHelper;
import com.coinhitchhiker.vbtrader.trader.config.PropertyMapHandler;
import com.google.gson.Gson;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.HmacUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;

import static org.joda.time.DateTimeZone.UTC;

public class BitMexExchange implements Exchange {

    private static final Logger LOGGER = LoggerFactory.getLogger(BitMexExchange.class);

    private String apiSecretInternal = null;
    private final RestTemplate restTemplate = new RestTemplate();;
    private int readTimeOutSeconds = 5;
    private Gson gsonMapper = new Gson();
    private List<CoinInfo> coins = new ArrayList<>();
    private String API_HOST;

    @Value("#{propertyMapHandler.getString(${bitmex.api.key}, ${vbtrader.id})}")
    private String apiKey;

    @Value("#{propertyMapHandler.getString(${bitmex.api.secret}, ${vbtrader.id})}")
    private String apiSecret;

    @Value("${bitmex.api.secret.encryption.enabled}")
    private boolean apiSecretEncryptionEnabled;

    @Value("${verboseHttp:false}")
    private boolean verboseHttp;

    @Value("${trading.symbol}") private String TRADING_SYMBOL;
    @Value("${trading.vb.window.size}") private int TRADING_WINDOW_SIZE;
    @Value("${trading.vb.look.behind}") private int TRADING_WINDOW_LOOK_BEHIND;
    @Value("${trading.vb.iceberg.order}") private boolean doIcebergOrder;
    @Value("${trading.bitmex.env}") private String BITMEX_ENV;

    @Autowired private EncryptorHelper encryptorHelper;
    @Autowired private PropertyMapHandler propertyMapHandler;
    @Autowired private Repository bitMexRepository;

    public static void main(String... args) {
        String apiKey = "LAqUlngMIQkIUjXMUreyu3qn";
        String apiSecret = "chNOOS4KvNXR_Xq4k4c9qsfoKWvnDecLATCRlcBwyKDYnWgO";

        String verb = "GET";
        String path = "/api/v1/instrument?filter=%7B%22symbol%22%3A+%22XBTM15%22%7D";
        String expires = "1518064237";
        String data = "";

        String signature = Hex.encodeHexString(HmacUtils.hmacSha256(apiSecret, verb + path + expires + data));
        System.out.println(signature);
    }

    @PostConstruct
    public void init() {
        if(apiSecretEncryptionEnabled) {
            this.apiSecretInternal = encryptorHelper.getDecryptedValue(apiSecret);
        } else {
            this.apiSecretInternal = apiSecret;
        }

        if(BITMEX_ENV.equals("TEST")) {
            API_HOST = "https://testnet.bitmex.com";
        } else {
            API_HOST = "https://www.bitmex.com";
        }

        this.restTemplate.setErrorHandler(new RESTAPIResponseErrorHandler());

        getCoinInfoList().forEach(c -> this.coins.add(c));
    }

    private String query(String method, String function, Map<String, String> params, boolean auth, boolean jsonParam) {
        String paramData = jsonParam ? this.gsonMapper.toJson(params).replaceAll("\n","").replaceAll(" ", "")
                : this.mapToQueryStr(params);
        String url = "/api/v1" + function + ((method.equals("GET") && paramData != null) ? paramData : "");
        String postData = (!method.equals("GET")) ? paramData : null;

        HttpHeaders headers = new HttpHeaders();
        if(auth) {
            String expires = String.valueOf(DateTime.now(UTC).getMillis()/1000 + readTimeOutSeconds);
            String message = method + url + expires + ((postData != null) ? postData : "");
            String signatureString = Hex.encodeHexString(HmacUtils.hmacSha256(apiSecretInternal, message));

            headers.add("api-expires", expires);
            headers.add("api-key", this.apiKey);
            headers.add("api-signature", signatureString);
        }

        if(postData != null) {
            headers.setContentType(jsonParam ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_FORM_URLENCODED);
        }

        HttpMethod httpMethod = method.equals("GET") ? HttpMethod.GET :
                                method.equals("POST") ? HttpMethod.POST :
                                method.equals("DELETE") ? HttpMethod.DELETE : null;

        HttpEntity<String> httpEntity = postData != null ? new HttpEntity<>(postData, headers) : new HttpEntity<>(null, headers);

        return this.restTemplate.exchange(API_HOST + url, httpMethod, httpEntity, String.class).getBody();
    }

    @Override
    public OrderInfo placeOrder(OrderInfo orderInfo) {
        CoinInfo coinInfo = this.getCoinInfoBySymbol(TRADING_SYMBOL);

        Map<String, String> params = new HashMap<>();
        params.put("symbol", this.TRADING_SYMBOL);
        params.put("side", orderInfo.getOrderSide() == OrderSide.BUY ? "Buy" : "Sell");
        params.put("orderQty", String.valueOf(coinInfo.getCanonicalAmount(orderInfo.getAmount())));
        params.put("orderType", "Limit");
        params.put("execInst", "ParticipateDoNotInitiate");     // LIMIT_MAKER order. will throw an exception when taken immediately
        params.put("price", String.valueOf(orderInfo.getPrice()));
        params.put("timeInForce", "GoodTillCancel");

        String response = this.query("POST", "/order", params, true, true);
        Map<String, Object> parsed = this.gsonMapper.fromJson(response, Map.class);
        LOGGER.info(parsed.toString());
        String externalOrderId = (String)parsed.get("orderId");
        long timestamp = new DateTime((String)parsed.get("timestamp"), UTC).getMillis();
        double priceExecuted = (double)parsed.get("avgPx");
        double amountExecuted = (double)parsed.get("cumQty");
        String orderStatus = (String)parsed.get("ordStatus");

        orderInfo.setExecTimestamp(timestamp);
        orderInfo.setAmountExecuted(amountExecuted);
        orderInfo.setPriceExecuted(priceExecuted);
        orderInfo.setExternalOrderId(externalOrderId);
        orderInfo.setOrderStatus((orderStatus.equals("Filled") ? OrderStatus.COMPLETE : OrderStatus.PENDING));

        return orderInfo;
    }

    @Override
    public OrderInfo cancelOrder(OrderInfo orderInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public OrderInfo getOrder(OrderInfo orderInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<CoinInfo> getCoinInfoList() {
        // XBt hardcoding
        CoinInfo coinInfo = new CoinInfo();
        coinInfo.setCoin("XBt");
        coinInfo.setSymbol("XBTUSD");
        coinInfo.setExchange("BITMEX");
        coinInfo.setMarket("XBt");
        coinInfo.setUnitAmount(1.0);
        coinInfo.setMinUnitAmount(1.0);
        coinInfo.setUnitPrice(0.5);
        coinInfo.setMinUnitPrice(0.5);

        List<CoinInfo> result = new ArrayList<>();
        result.add(coinInfo);

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
        // We do XBTUSD on bitmex. symbol is no-op here.
//        TradeEvent e = bitMexRepository.getCurrentTradingWindow(0).getPrevTradeEvent();
//        if(e != null) {
//            return e.getPrice();
//        } else {
//            LOGGER.warn("prevTradeEvent was not received. Returning default 10000...");
            return 0.0D;
//        }
    }

    @Override
    public Map<String, Balance> getBalance() {
        Map<String, String> params = new HashMap<>();
        String response = this.query("GET", "/user/wallet", params, true, false);
        Map<String, Object> parsed = this.gsonMapper.fromJson(response, Map.class);
        double amount = (double)parsed.get("amount");
        String currency = (String)parsed.get("currency");

        Map<String, Balance> result = new HashMap<>();
        Balance bal = new Balance();
        bal.setCoin(currency);
        bal.setExchange("BITMEX");
        bal.setAvailableForTrade(amount);
        bal.setAvailableForWithdraw(amount);
        bal.setBalanceTotal(amount);
        result.put(currency, bal);

        return result;
    }

    private String mapToQueryStr(Map<String, String> map) {
        String result = "";
        for(Map.Entry<String, String> entry : map.entrySet()) {
            result = result + entry.getKey() + "=" + entry.getValue() + "&";
        }
        return result.length() == 0 ? result : "?" + result.substring(0, result.length()-1);
    }
}
