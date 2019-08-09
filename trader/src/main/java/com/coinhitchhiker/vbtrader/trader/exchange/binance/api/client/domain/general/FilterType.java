package com.coinhitchhiker.vbtrader.trader.exchange.binance.api.client.domain.general;

/**
 * Filters define trading rules on a symbol or an exchange. Filters come in two forms: symbol filters and exchange filters.
 */
public enum FilterType {
  // Symbol
  PRICE_FILTER,
  LOT_SIZE,
  MIN_NOTIONAL,
  ICEBERG_PARTS,
  MAX_NUM_ALGO_ORDERS,
  PERCENT_PRICE,
  MARKET_LOT_SIZE
}
