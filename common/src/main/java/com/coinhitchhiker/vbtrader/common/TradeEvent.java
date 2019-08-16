package com.coinhitchhiker.vbtrader.common;

public class TradeEvent {

    String exchange;
    String symbol;
    double price;
    long tradeTime;
    double amount;
    String tradeId;
    String buyOrderId;
    String sellOrderId;

    public TradeEvent(String exchange, String symbol, double price, long tradeTime, double amount, String tradeId, String buyOrderId, String sellOrderId) {
        this.exchange = exchange;
        this.symbol = symbol;
        this.price = price;
        this.tradeTime = tradeTime;
        this.amount = amount;
        this.tradeId = tradeId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
    }

    public String getExchange() {
        return exchange;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        return price;
    }

    public long getTradeTime() {
        return tradeTime;
    }

    public double getAmount() {
        return amount;
    }

    public String getTradeId() {
        return tradeId;
    }

    public String getBuyOrderId() {
        return buyOrderId;
    }

    public String getSellOrderId() {
        return sellOrderId;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "TradeEvent{" +
                "exchange='" + exchange + '\'' +
                ", symbol='" + symbol + '\'' +
                ", price=" + price +
                ", tradeTime=" + tradeTime +
                ", amount=" + amount +
                ", tradeId='" + tradeId + '\'' +
                ", buyOrderId='" + buyOrderId + '\'' +
                ", sellOrderId='" + sellOrderId + '\'' +
                '}';
    }
}
