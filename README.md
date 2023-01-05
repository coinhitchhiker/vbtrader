# Intro
Started as a Volatility Breakout Strategy trader, evolved into a framework for

- Technical indicator-based trading strategy back-testing
- Actual trading based on the back-tested trading strategy on Binance

Once you implement a strategy following framework rules, you can either test the strategy with past Binance price data or perform actual trading only by switching mode. But stopped further investment as it turned out technical indicator-based trading didn't yield desired profit. We realized the approach is very prone to overfitting for past data, which never guarantees future performance. 

# Tested Strategies
- Hull Moving Average Trade
- Internal Bar Strength
- M5 Scalping
- Volatility Breakout
- Price Volume Trend - On Balance Volume based trade
