package com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.general;

import com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.constant.BinanceApiConstants;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Rate limits.
 */
public class RateLimit {

  private String rateLimitType;

  private RateLimitInterval interval;

  private Integer limit;

  private Integer intervalNum;

  public String getRateLimitType() {
    return rateLimitType;
  }

  public void setRateLimitType(String rateLimitType) {
    this.rateLimitType = rateLimitType;
  }

  public RateLimitInterval getInterval() {
    return interval;
  }

  public void setInterval(RateLimitInterval interval) {
    this.interval = interval;
  }

  public Integer getLimit() {
    return limit;
  }

  public void setLimit(Integer limit) {
    this.limit = limit;
  }

  public Integer getIntervalNum() {
    return intervalNum;
  }

  public void setIntervalNum(Integer intervalNum) {
    this.intervalNum = intervalNum;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, BinanceApiConstants.TO_STRING_BUILDER_STYLE)
        .append("rateLimitType", rateLimitType)
        .append("interval", interval)
        .append("limit", limit)
        .append("intervalNum", intervalNum)
        .toString();
  }
}
