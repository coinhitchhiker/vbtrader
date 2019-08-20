package com.coinhitchhiker.vbtrader.common.model;

import java.util.Objects;

public class CoinInfo {

    String exchange;
    String market;
    String coin;
    String symbol;
    Double unitAmount;
    Double unitPrice;
    Double minUnitAmount;
    Double minUnitPrice;

    public CoinInfo() {
    }

    public CoinInfo(String exchange, String market, String coin, String symbol, Double unitAmount, Double unitPrice, Double minUnitAmount, Double minUnitPrice) {
        this.exchange = exchange;
        this.market = market;
        this.coin = coin;
        this.symbol = symbol;
        this.unitAmount = unitAmount;
        this.unitPrice = unitPrice;
        this.minUnitAmount = minUnitAmount;
        this.minUnitPrice = minUnitPrice;
    }

    public String getCanonicalAmount(double amount) {
        int amountScale = (int)(-1*Math.log10(unitAmount));
        return String.format("%." + amountScale + "f", new Object[]{Double.valueOf(amount)});
    }

    public String getCanonicalPrice(double price) {
        int priceScale = (int)(-1*Math.log10(unitPrice));
        return String.format("%." + priceScale + "f", new Object[]{Double.valueOf(price)});
    }

    public int getAmountScale() {
        return (int)(-1*Math.log10(unitAmount));
    }

    public int getPriceScale() {
        return (int)(-1*Math.log10(unitPrice));
    }

    //----------------------------------------------------------------------------------------
    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getCoin() {
        return coin;
    }

    public void setCoin(String coin) {
        this.coin = coin;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Double getUnitAmount() {
        return unitAmount;
    }

    public void setUnitAmount(Double unitAmount) {
        this.unitAmount = unitAmount;
    }

    public Double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Double getMinUnitAmount() {
        return minUnitAmount;
    }

    public void setMinUnitAmount(Double minUnitAmount) {
        this.minUnitAmount = minUnitAmount;
    }

    public Double getMinUnitPrice() {
        return minUnitPrice;
    }

    public void setMinUnitPrice(Double minUnitPrice) {
        this.minUnitPrice = minUnitPrice;
    }

    @Override
    public String toString() {
        return "CoinInfo{" +
            "exchange='" + exchange + '\'' +
            ", market='" + market + '\'' +
            ", coin='" + coin + '\'' +
            ", symbol='" + symbol + '\'' +
            ", unitAmount=" + unitAmount +
            ", unitPrice=" + unitPrice +
            ", minUnitAmount=" + minUnitAmount +
            ", minUnitPrice=" + minUnitPrice +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CoinInfo coinInfo = (CoinInfo) o;
        return Objects.equals(exchange, coinInfo.exchange) &&
            Objects.equals(market, coinInfo.market) &&
            Objects.equals(coin, coinInfo.coin) &&
            Objects.equals(symbol, coinInfo.symbol) &&
            Objects.equals(unitAmount, coinInfo.unitAmount) &&
            Objects.equals(unitPrice, coinInfo.unitPrice) &&
            Objects.equals(minUnitAmount, coinInfo.minUnitAmount) &&
            Objects.equals(minUnitPrice, coinInfo.minUnitPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exchange, market, coin, symbol, unitAmount, unitPrice, minUnitAmount, minUnitPrice);
    }
}
