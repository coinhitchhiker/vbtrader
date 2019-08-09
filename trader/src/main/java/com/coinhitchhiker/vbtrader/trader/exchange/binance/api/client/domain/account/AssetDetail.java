package com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.account;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class AssetDetail {

    private boolean success;

    @JsonProperty("assetDetail")
    private Map<String, AssetWithdraw> assetDetail;

    private String msg;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Map<String, AssetWithdraw> getAssetDetail() {
        return assetDetail;
    }

    public void setAssetDetail(Map<String, AssetWithdraw> assetDetail) {
        this.assetDetail = assetDetail;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
