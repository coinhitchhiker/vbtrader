package com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.account;

/**
 * A deposit address for a given asset.
 */
public class DepositAddress {

  private String address;

  private boolean success;

  private String addressTag;

  private String asset;

  private String msg;

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getAddressTag() {
    return addressTag;
  }

  public void setAddressTag(String addressTag) {
    this.addressTag = addressTag;
  }

  public String getAsset() {
    return asset;
  }

  public void setAsset(String asset) {
    this.asset = asset;
  }

  public String getMsg() {
    return msg;
  }

  public void setMsg(String msg) {
    this.msg = msg;
  }

  @Override
  public String toString() {
    return "DepositAddress{" +
        "address='" + address + '\'' +
        ", success=" + success +
        ", addressTag='" + addressTag + '\'' +
        ", asset='" + asset + '\'' +
        ", msg='" + msg + '\'' +
        '}';
  }
}