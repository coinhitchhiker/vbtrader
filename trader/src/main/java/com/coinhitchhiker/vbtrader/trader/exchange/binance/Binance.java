package com.coinhitchhiker.vbtrader.trader.exchange.binance;

import com.coinhitchhiker.vbtrader.common.*;
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
import com.neovisionaries.ws.client.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Component(value = Binance.BEAN_NAME_BINANCE)
public class Binance implements Exchange, Repository {

    public static final String BEAN_NAME_BINANCE = "exchange-binance";

    private static final Logger LOGGER = LoggerFactory.getLogger(Binance.class);
    private static final String BIDS = "BIDS";
    private static final String ASKS = "ASKS";
    private static final int TIMEOUT = 5000;

    private long lastUpdateId = -1;
    private final Map<String, NavigableMap<BigDecimal, BigDecimal>> depthCache = new ConcurrentHashMap<>();
    private WebSocket ws;
    private BinanceWsCallback callback = new BinanceWsCallback();
    private BinanceApiClientFactory factory = null;
    private BinanceApiRestClient client = null;

    private RestTemplate restTemplate;
    private Gson gson = new Gson();
    private List<TradingWindow> pastTradingWindows;
    private TradingWindow currentTradingWindow;
    private List<CoinInfo> coins = new ArrayList<>();

//    @Value("#{propertyMapHandler.getString(${binance.api.key}, ${vbtrader.id})}")
    private String apiKey = "6G8Q3f2asHNBJvlPLDCs7C99ojp8gw3sKxThZkjuM4jqpWo63RI2JbFhjVqTvuOt";

//    @Value("#{propertyMapHandler.getString(${binance.api.secret}, ${vbtrader.id})}")
    private String apiSecret = "N4oaXJPQ9WKMrGBUMi14iQsPGQ0jA6iIAUyORh4ay2GLG4Dh5irqG1kVZg54SmH8";

    @Value("${trading.symbol}") private String TRADING_SYMBOL;
    @Value("${trading.window.size}") private int TRADING_WINDOW_SIZE;
    @Value("${trading.look.behind}") private int TRADING_WINDOW_LOOK_BEHIND;
    @Value("${trading.iceberg.order}") private boolean doIcebergOrder;

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private EncryptorHelper encryptorHelper;
    @Autowired private PropertyMapHandler propertyMapHandler;

    private boolean refreshingTradingWindows = false;
    private ReadWriteLock rwlock = new ReentrantReadWriteLock();

    @PostConstruct
    public void init() {

        this.restTemplate = new RestTemplateBuilder().setConnectTimeout(Duration.ofMillis(1000L)).setReadTimeout(Duration.ofMillis(5000L)).build();
//        this.factory = BinanceApiClientFactory.newInstance(apiKey, encryptorHelper.getDecryptedValue(apiSecret));
        this.factory = BinanceApiClientFactory.newInstance(apiKey, apiSecret);
        this.client = factory.newRestClient();

        subscribeToOrderBookAndTrades();
        getCoinInfoList().forEach(c -> this.coins.add(c));
        refreshTradingWindows();
    }

    private double getMidPrice() {
        return (getBestAsk() + getBestBid())/2;
    }

    private DateTime getClosestMin(DateTime now) {
        int y = now.getYear();
        int m = now.getMonthOfYear();
        int d = now.getDayOfMonth();
        int h = now.getHourOfDay();
        int mm = now.getMinuteOfHour();

        DateTime closestMin = new DateTime(y,m,d,h,mm,DateTimeZone.UTC);
        return closestMin;
    }

    @Override
    public void refreshTradingWindows() {
        this.refreshingTradingWindows = true;
        LOGGER.debug("refreshingTradingWindows is set to TRUE");

        DateTime now = DateTime.now(DateTimeZone.UTC);
        DateTime closestMin = getClosestMin(now);

        this.currentTradingWindow = constructTradingWindow(closestMin);
        LOGGER.debug("Refreshed curTW {}", this.currentTradingWindow.toString());

        this.pastTradingWindows = new ArrayList<>();
        long curTimestamp = closestMin.getMillis();

        long windowEnd = curTimestamp - 1000;
        long windowStart = windowEnd - TRADING_WINDOW_SIZE * 60 * 1000;

        for(int i = 0; i <= TRADING_WINDOW_LOOK_BEHIND; i++) {
            List<Candle> candles = this.getBinanceCandles(TRADING_SYMBOL, windowStart, windowEnd);
            TradingWindow tw = TradingWindow.of(candles);
            LOGGER.debug("{}/{} {}", i, TRADING_WINDOW_LOOK_BEHIND, tw.toString());
            pastTradingWindows.add(tw);
            windowEnd -= TRADING_WINDOW_SIZE * 60 * 1000;
            windowStart -= TRADING_WINDOW_SIZE * 60 * 1000;
            try {Thread.sleep(300L);} catch(Exception e) {}
        }
        this.refreshingTradingWindows = false;
        LOGGER.debug("refreshingTradingWindows is set to FALSE");
    }

    private TradingWindow constructTradingWindow(DateTime startTime) {
        long timestamp = startTime.getMillis();

        TradingWindow result =  new TradingWindow(this.TRADING_SYMBOL, timestamp, timestamp + TRADING_WINDOW_SIZE * 60 * 1000, getMidPrice());
        List<Candle> binanceCandles = getBinanceCandles(TRADING_SYMBOL, timestamp, timestamp + TRADING_WINDOW_SIZE * 60 * 1000);
        if(binanceCandles.size() > 0) {
            TradingWindow twFromBinanceData = TradingWindow.of(binanceCandles);
            result.setHighPrice(twFromBinanceData.getHighPrice());
            result.setOpenPrice(twFromBinanceData.getOpenPrice());
        }
        return result;
    }

    private List<Candle> getBinanceCandles(String symbol, long startTime, long endTime) {
        List<Candle> candles = new ArrayList<>();
        String interval = "1m";
        while(true) {
            String url = "https://api.binance.com//api/v1/klines?symbol="+symbol+"&interval="+interval+"&limit=1000&startTime="+startTime+"&endTime="+endTime;
            String response;
            try {
                response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null, null), String.class).getBody();
            } catch (Exception e) {
                LOGGER.error("Error reading binance candles. Retrying...", e);
                continue;
            }

            List<List<Object>> list = gson.fromJson(response, List.class);
            for(List<Object> data : list) {
                candles.add(Candle.fromBinanceCandle(symbol, interval, data));
            }
            if(list.size() < 1000) {
                break;
            }
            startTime = candles.get(candles.size()-1).getCloseTime() + 1;
            try {Thread.sleep(300L);} catch(Exception e) {}
        }
        return candles;
    }

    public List<TradingWindow> getLastNTradingWindow(int n, long curTimestamp) {
        return this.pastTradingWindows.stream().limit(n).collect(Collectors.toList());
    }

    public TradingWindow getCurrentTradingWindow(long curTimestamp) {
        return this.currentTradingWindow;
    }

    public void subscribeToOrderBookAndTrades() {

        initializeDepthCache();

        String url = "wss://stream.binance.com:9443/stream?streams=" + TRADING_SYMBOL.toLowerCase() + "@depth20/" + TRADING_SYMBOL.toLowerCase() + "@trade";
        LOGGER.info(url);

        try {
            this.ws = new WebSocketFactory()
                    .setConnectionTimeout(TIMEOUT)
                    .createSocket(url)
                    .addExtension(WebSocketExtension.PERMESSAGE_DEFLATE)
                    .connect();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.ws.addListener(new BinanceWebSocketAdapter(this.callback));
    }

    private void onTraderEvent(Map<String, Object> trade) {

        if(this.refreshingTradingWindows) {
            LOGGER.debug("refreshingTradingWindows is ongoing... returning");
            return;
        }

        double price = Double.parseDouble(((String)trade.get("p")));
        long tradeTime = ((Double)trade.get("T")).longValue();
        double amount = Double.parseDouble((String)trade.get("q"));
        String tradeId = String.valueOf(((Double)trade.get("t")).longValue());
        String buyOrderId = String.valueOf(((Double)trade.get("b")).longValue());
        String sellOrderId = String.valueOf(((Double)trade.get("a")).longValue());

        TradeEvent e = new TradeEvent("BINANCE", this.TRADING_SYMBOL, price, tradeTime , amount, tradeId, buyOrderId, sellOrderId);

        this.currentTradingWindow.updateWindowData(e);

//        this.eventPublisher.publishEvent(e);
    }

    protected void updateOrderBook(long timestamp, List<List<String>> bidData, List<List<String>> askData) {
        if(this.lastUpdateId >= timestamp)
            return;

        NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>(Comparator.reverseOrder());
        askData.forEach(entry -> {
            BigDecimal price = new BigDecimal(entry.get(0));
            BigDecimal amount = new BigDecimal(entry.get(1));

            asks.put(price, amount);
        });
        this.depthCache.put(ASKS, asks);

        NavigableMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Comparator.reverseOrder());
        bidData.forEach(entry -> {
            BigDecimal price = new BigDecimal(entry.get(0));
            BigDecimal amount = new BigDecimal(entry.get(1));

            bids.put(price, amount);
        });
        this.depthCache.put(BIDS, bids);

        this.lastUpdateId = timestamp;
    }

    public void printBestAskBidMidprice() {
        LOGGER.info("--------------------------------------------");
        LOGGER.info("BestAsk: " + getBestAsk());
        LOGGER.info("BestBid: " + getBestBid());
        LOGGER.info("MidPrice: " + getMidPrice());
        LOGGER.info("--------------------------------------------");
    }

    private void initializeDepthCache() {
        String url = "https://api.binance.com/api/v1/depth?symbol=" + this.TRADING_SYMBOL + "&limit=20";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        String body = response.getBody();
        Map<String, Object> map = this.gson.fromJson(body, Map.class);
        long timestamp = ((Double)map.get("lastUpdateId")).longValue();
        List<List<String>> bidData = (List)map.get("bids");
        List<List<String>> askData = (List)map.get("asks");

        this.lastUpdateId = timestamp;

        NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>(Comparator.reverseOrder());
        askData.forEach(entry -> {
            BigDecimal price = new BigDecimal(entry.get(0));
            BigDecimal amount = new BigDecimal(entry.get(1));

            asks.put(price, amount);
        });
        this.depthCache.put(ASKS, asks);

        NavigableMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Comparator.reverseOrder());
        bidData.forEach(entry -> {
            BigDecimal price = new BigDecimal(entry.get(0));
            BigDecimal amount = new BigDecimal(entry.get(1));

            bids.put(price, amount);
        });
        this.depthCache.put(BIDS, bids);
    }

    @Override
    public double getBestAsk() {
        return this.depthCache.get(ASKS).lastEntry().getKey().doubleValue();
    }

    @Override
    public double getBestBid() {
        return this.depthCache.get(BIDS).firstEntry().getKey().doubleValue();
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

        if(doIcebergOrder) {
            String icebergQty = coinInfo.getCanonicalAmount(orderInfo.getAmount()/10);
            newOrder = newOrder.icebergQty(icebergQty);
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
    public void recordOrder(TradingWindow tradingWindow, OrderInfo orderInfo) {

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
    //----------------------------------------------------------------------
    protected final class BinanceWsCallback {

        public void onOrderBookUpdate(long timestamp, List<List<String>> bids, List<List<String>> asks) {
            updateOrderBook(timestamp, bids, asks);
        }

        public void onTradeEvent(Map<String, Object> trade) {
            onTraderEvent(trade);
        }

        public void onPingFrame() {
            ws.sendPong("I am live");
        }

        public void onFailure(Throwable t) {
            LOGGER.error("WS connection failed. Reconnecting. cause:", t);
            try {
                ws.sendClose();
            } catch (Exception e) {
                LOGGER.error("Exception when closing WS connection...", e);
            }
            LOGGER.info("Restarting connection...");
            initializeDepthCache();
            subscribeToOrderBookAndTrades();
        }
    }

}
