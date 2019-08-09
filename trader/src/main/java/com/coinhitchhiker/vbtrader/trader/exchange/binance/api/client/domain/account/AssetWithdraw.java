package com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.account;

public class AssetWithdraw {

    private String minWithdrawAmount;

    private boolean depositStatus;

    private String withdrawFee;

    private boolean withdrawStatus;

    private String depositTip;

    public String getMinWithdrawAmount() {
        return minWithdrawAmount;
    }

    public void setMinWithdrawAmount(String minWithdrawAmount) {
        this.minWithdrawAmount = minWithdrawAmount;
    }

    public boolean isDepositStatus() {
        return depositStatus;
    }

    public void setDepositStatus(boolean depositStatus) {
        this.depositStatus = depositStatus;
    }

    public String getWithdrawFee() {
        return withdrawFee;
    }

    public void setWithdrawFee(String withdrawFee) {
        this.withdrawFee = withdrawFee;
    }

    public boolean isWithdrawStatus() {
        return withdrawStatus;
    }

    public void setWithdrawStatus(boolean withdrawStatus) {
        this.withdrawStatus = withdrawStatus;
    }

    public String getDepositTip() {
        return depositTip;
    }

    public void setDepositTip(String depositTip) {
        this.depositTip = depositTip;
    }
}
