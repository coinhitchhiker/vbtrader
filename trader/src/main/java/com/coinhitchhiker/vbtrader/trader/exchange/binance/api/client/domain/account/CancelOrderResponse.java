package com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CancelOrderResponse {

    private String symbol;
    private Long orderId;
    private String origClientOrderId;
    private String clientOrderId;
    private Long transactTime;
    private String price;
    private String origQty;
    private String executedQty;
    private String status;
    private String timeInForce;
    private String type;
    private String side;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrigClientOrderId() {
        return origClientOrderId;
    }

    public void setOrigClientOrderId(String origClientOrderId) {
        this.origClientOrderId = origClientOrderId;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public Long getTransactTime() {
        return transactTime;
    }

    public void setTransactTime(Long transactTime) {
        this.transactTime = transactTime;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getOrigQty() {
        return origQty;
    }

    public void setOrigQty(String origQty) {
        this.origQty = origQty;
    }

    public String getExecutedQty() {
        return executedQty;
    }

    public void setExecutedQty(String executedQty) {
        this.executedQty = executedQty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    @Override
    public String toString() {
        return "CancelOrderResponse{" +
            "symbol='" + symbol + '\'' +
            ", orderId=" + orderId +
            ", origClientOrderId='" + origClientOrderId + '\'' +
            ", clientOrderId='" + clientOrderId + '\'' +
            ", transactTime=" + transactTime +
            ", price='" + price + '\'' +
            ", origQty='" + origQty + '\'' +
            ", executedQty='" + executedQty + '\'' +
            ", status='" + status + '\'' +
            ", timeInForce='" + timeInForce + '\'' +
            ", type='" + type + '\'' +
            ", side='" + side + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CancelOrderResponse that = (CancelOrderResponse) o;
        return Objects.equals(symbol, that.symbol) &&
            Objects.equals(orderId, that.orderId) &&
            Objects.equals(origClientOrderId, that.origClientOrderId) &&
            Objects.equals(clientOrderId, that.clientOrderId) &&
            Objects.equals(transactTime, that.transactTime) &&
            Objects.equals(price, that.price) &&
            Objects.equals(origQty, that.origQty) &&
            Objects.equals(executedQty, that.executedQty) &&
            Objects.equals(status, that.status) &&
            Objects.equals(timeInForce, that.timeInForce) &&
            Objects.equals(type, that.type) &&
            Objects.equals(side, that.side);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, orderId, origClientOrderId, clientOrderId, transactTime, price, origQty, executedQty, status, timeInForce, type, side);
    }
}
