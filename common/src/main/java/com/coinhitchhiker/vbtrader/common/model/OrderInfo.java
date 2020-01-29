package com.coinhitchhiker.vbtrader.common.model;

import org.joda.time.DateTime;

import java.util.Objects;

import static org.joda.time.DateTimeZone.UTC;

public class OrderInfo implements Comparable<OrderInfo> {

    private ExchangeEnum exchange;
    private String symbol;
    private OrderSide orderSide;
    private OrderType orderType;
    private double stopPrice;
    private double price;
    private double amount;
    private String externalOrderId;
    private long execTimestamp;
    private OrderStatus orderStatus = OrderStatus.PENDING;
    private double amountExecuted;
    private double priceExecuted;
    private double feePaid;
    private String feeCurrency;
    private String borrow_id;
    private String clientOid;


    public OrderInfo(ExchangeEnum exchange, String symbol, OrderSide orderSide, OrderType orderType, double price, double amount) {
        this.exchange = exchange;
        this.symbol = symbol;
        this.orderSide = orderSide;
        this.orderType = orderType;
        this.price = price;
        this.amount = amount;
    }

    public OrderInfo clone() {
        OrderInfo o = new OrderInfo(exchange, symbol, orderSide, orderType, price, amount);
        o.setExternalOrderId(externalOrderId);
        o.setExecTimestamp(execTimestamp);
        o.setOrderStatus(orderStatus);
        o.setAmountExecuted(amountExecuted);
        o.setPriceExecuted(priceExecuted);
        o.setStopPrice(stopPrice);
        o.setClientOid(clientOid);
        o.setBorrow_id(borrow_id);
        o.setFeeCurrency(feeCurrency);
        o.setFeePaid(feePaid);
        return o;
    }

    //------------------------------------------------------------------------------------------------------

    public ExchangeEnum getExchange() {
        return exchange;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getOrderSide() {
        return orderSide;
    }

    public double getPrice() {
        return price;
    }

    public double getAmount() {
        return amount;
    }

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public void setExternalOrderId(String externalOrderId) {
        this.externalOrderId = externalOrderId;
    }

    public long getExecTimestamp() {
        return execTimestamp;
    }

    public void setExecTimestamp(Long execTimestamp) {
        this.execTimestamp = execTimestamp;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }

    public double getAmountExecuted() {
        return amountExecuted;
    }

    public void setAmountExecuted(double amountExecuted) {
        this.amountExecuted = amountExecuted;
    }

    public double getPriceExecuted() {
        return priceExecuted;
    }

    public void setPriceExecuted(double priceExecuted) {
        this.priceExecuted = priceExecuted;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getFeePaid() {
        return feePaid;
    }

    public void setFeePaid(double feePaid) {
        this.feePaid = feePaid;
    }

    public String getFeeCurrency() {
        return feeCurrency;
    }

    public void setFeeCurrency(String feeCurrency) {
        this.feeCurrency = feeCurrency;
    }

    public double getStopPrice() {
        return stopPrice;
    }

    public void setStopPrice(double stopPrice) {
        this.stopPrice = stopPrice;
    }

    public String getClientOid() {
        return clientOid;
    }

    public void setClientOid(String clientOid) {
        this.clientOid = clientOid;
    }

    public String getBorrow_id() {
        return borrow_id;
    }

    public void setBorrow_id(String borrow_id) {
        this.borrow_id = borrow_id;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderInfo orderInfo = (OrderInfo) o;
        return Double.compare(orderInfo.stopPrice, stopPrice) == 0 &&
                Double.compare(orderInfo.price, price) == 0 &&
                Double.compare(orderInfo.amount, amount) == 0 &&
                execTimestamp == orderInfo.execTimestamp &&
                Double.compare(orderInfo.amountExecuted, amountExecuted) == 0 &&
                Double.compare(orderInfo.priceExecuted, priceExecuted) == 0 &&
                Double.compare(orderInfo.feePaid, feePaid) == 0 &&
                exchange == orderInfo.exchange &&
                Objects.equals(symbol, orderInfo.symbol) &&
                orderSide == orderInfo.orderSide &&
                orderType == orderInfo.orderType &&
                Objects.equals(externalOrderId, orderInfo.externalOrderId) &&
                orderStatus == orderInfo.orderStatus &&
                Objects.equals(feeCurrency, orderInfo.feeCurrency) &&
                Objects.equals(borrow_id, orderInfo.borrow_id) &&
                Objects.equals(clientOid, orderInfo.clientOid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exchange, symbol, orderSide, orderType, stopPrice, price, amount, externalOrderId, execTimestamp, orderStatus, amountExecuted, priceExecuted, feePaid, feeCurrency, borrow_id, clientOid);
    }

    @Override
    public String toString() {
        return "OrderInfo{" +
                "exchange=" + exchange +
                ", symbol='" + symbol + '\'' +
                ", orderSide=" + orderSide +
                ", orderType=" + orderType +
                ", stopPrice=" + stopPrice +
                ", price=" + price +
                ", amount=" + amount +
                ", externalOrderId='" + externalOrderId + '\'' +
                ", execTimestamp=" + new DateTime(execTimestamp, UTC) +
                ", orderStatus=" + orderStatus +
                ", amountExecuted=" + amountExecuted +
                ", priceExecuted=" + priceExecuted +
                ", feePaid=" + feePaid +
                ", feeCurrency='" + feeCurrency + '\'' +
                ", borrow_id='" + borrow_id + '\'' +
                ", clientOid='" + clientOid + '\'' +
                '}';
    }

    @Override
    public int compareTo(OrderInfo o) {
        if(this.price > o.getPrice()) {
            return 1;
        } else if(this.price < o.getPrice()) {
            return -1;
        }
        return 0;
    }
}
