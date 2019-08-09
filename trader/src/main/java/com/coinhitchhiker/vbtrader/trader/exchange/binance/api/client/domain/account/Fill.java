package com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.account;

import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.constant.BinanceApiConstants;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Order fill information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Fill {

    private String price;

    private String qty;

    private String commission;

    private String commissionAsset;

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getQty() {
        return qty;
    }

    public void setQty(String qty) {
        this.qty = qty;
    }

    public String getCommission() {
        return commission;
    }

    public void setCommission(String commission) {
        this.commission = commission;
    }

    public String getCommissionAsset() {
        return commissionAsset;
    }

    public void setCommissionAsset(String commissionAsset) {
        this.commissionAsset = commissionAsset;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, BinanceApiConstants.TO_STRING_BUILDER_STYLE)
                .append("price", price)
                .append("qty", qty)
                .append("commission", commission)
                .append("commissionAsset", commissionAsset)
                .toString();
    }
}
