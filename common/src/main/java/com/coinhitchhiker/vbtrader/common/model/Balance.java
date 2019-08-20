package com.coinhitchhiker.vbtrader.common.model;

import java.util.Objects;

public class Balance {

  private String coin;
  private String exchange;
  private double balanceTotal;
  private double availableForTrade;
  private double availableForWithdraw;

  public Balance() {

    balanceTotal = 0;
    availableForTrade = 0;
    availableForWithdraw = 0;
  }

  public String getCoin() {
    return coin;
  }

  public void setCoin(String coin) {
    this.coin = coin;
  }

  public String getExchange() {
    return exchange;
  }

  public void setExchange(String exchange) {
    this.exchange = exchange;
  }

  public double getBalanceTotal() {
    return balanceTotal;
  }

  public void setBalanceTotal(double balanceTotal) {
    this.balanceTotal = balanceTotal;
  }

  public double getAvailableForTrade() {
    return availableForTrade;
  }

  public void setAvailableForTrade(double availableForTrade) {
    this.availableForTrade = availableForTrade;
  }

  public double getAvailableForWithdraw() {
    return availableForWithdraw;
  }

  public void setAvailableForWithdraw(double availableForWithdraw) {
    this.availableForWithdraw = availableForWithdraw;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Balance balance = (Balance) o;
    return Double.compare(balance.balanceTotal, balanceTotal) == 0 &&
        Double.compare(balance.availableForTrade, availableForTrade) == 0 &&
        Double.compare(balance.availableForWithdraw, availableForWithdraw) == 0 &&
        Objects.equals(coin, balance.coin) &&
        exchange == balance.exchange;
  }

  @Override
  public int hashCode() {

    return Objects.hash(coin, exchange, balanceTotal, availableForTrade, availableForWithdraw);
  }

  @Override
  public String toString() {
    return "Balance{" +
        "coin='" + coin + '\'' +
        ", exchange=" + exchange +
        ", balanceTotal=" + balanceTotal +
        ", availableForTrade=" + availableForTrade +
        ", availableForWithdraw=" + availableForWithdraw +
        '}';
  }
}
