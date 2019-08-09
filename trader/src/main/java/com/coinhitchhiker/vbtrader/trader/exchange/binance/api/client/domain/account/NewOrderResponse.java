package com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.account;

import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.constant.BinanceApiConstants;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.List;

/**
 * Response returned when placing a new order on the system.
 *
 * @see NewOrder for the request
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NewOrderResponse {

    /**
     * Order symbol.
     */
    private String symbol;

    /**
     * Order id.
     */
    private Long orderId;

    /**
     * This will be either a generated one, or the newClientOrderId parameter
     * which was passed when creating the new order.
     */
    private String clientOrderId;

    /**
     * Transact time for this order.
     */
    private Long transactTime;

    /**
     * Order price.
     */
    private String price;

    /**
     * Order quantity.
     */
    private String origQty;

    /**
     * Executed quantity for this order.
     */
    private String executedQty;

    /**
     * Order status.
     */
    private String status;

    /**
     * Order type.
     */
    private String type;

    /**
     * Order side.
     */
    private String side;

    /**
     * Order fill list.
     */
    private List<Fill> fills;

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

    public List<Fill> getFills() {
        return fills;
    }

    public void setFills(List<Fill> fills) {
        this.fills = fills;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, BinanceApiConstants.TO_STRING_BUILDER_STYLE)
                .append("symbol", symbol)
                .append("orderId", orderId)
                .append("clientOrderId", clientOrderId)
                .append("transactTime", transactTime)
                .append("price", price)
                .append("origQty", origQty)
                .append("executedQty", executedQty)
                .append("status", status)
                .append("type", type)
                .append("side", side)
                .append("fills", fills)
                .toString();
    }
}
