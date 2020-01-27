package com.coinhitchhiker.vbtrader.common.model.event;

import com.coinhitchhiker.vbtrader.common.model.ExchangeEnum;

public class TradeResultEvent {

    private ExchangeEnum exchange;
    private String symbol;
    private String quoteCurrency;
    private double netProfit;
    private double profit;
    private double sellPrice;
    private double buyPrice;
    private double amountExecuted;
    private double fee;

    public TradeResultEvent(ExchangeEnum exchange, String symbol, String quoteCurrency, double netProfit, double profit, double sellPrice, double buyPrice, double amountExecuted, double fee) {
        this.exchange = exchange;
        this.symbol = symbol;
        this.quoteCurrency = quoteCurrency;
        this.sellPrice = sellPrice;
        this.buyPrice = buyPrice;
        this.amountExecuted = amountExecuted;
        this.fee = fee;
        this.netProfit = netProfit;
        this.profit = profit;
    }

    public ExchangeEnum getExchange() {
        return exchange;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public double getAmountExecuted() {
        return amountExecuted;
    }

    public double getFee() {
        return fee;
    }

    public double getNetProfit() {
        return netProfit;
    }

    public double getProfit() {
        return profit;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }
}
