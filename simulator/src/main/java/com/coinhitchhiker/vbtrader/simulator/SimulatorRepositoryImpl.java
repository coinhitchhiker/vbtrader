package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.Repository;
import com.coinhitchhiker.vbtrader.common.TradingWindow;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SimulatorRepositoryImpl implements Repository {

    private List<TradingWindow> tradingWindows = new ArrayList<>();

    public SimulatorRepositoryImpl(String dataFile, int tradingWindowSizeInMinutes) throws IOException {
        try(BufferedReader br = Files.newBufferedReader(Paths.get(dataFile))) {
            //symbol interval openTime closeTime openPrice highPrice lowPrice closePrice volume
            //BTCUSDT|1m|1551398400000|1551398459999|3814.2600000000|3814.5000000000|3813.5500000000|3814.3300000000|9.3097660000

            List<Candle> tempList = new ArrayList<>();
            DateTime tradingWindowCloseTime = null, candleCloseTime = null;
            Candle candle = null;
            while(true) {
                String s = br.readLine();

                if(s != null) {
                    candle = parseCandleDataLine(s);
                    candleCloseTime = new DateTime(candle.getCloseTime(), DateTimeZone.UTC);
                    // we only expect 1m interval data from binance candle
                    if(!candle.getInterval().equals("1m")) continue;
                    if(tradingWindowCloseTime == null) {
                        tradingWindowCloseTime = new DateTime(candle.getOpenTime(), DateTimeZone.UTC).plusMinutes(tradingWindowSizeInMinutes).minusSeconds(1);
                    }
                    tempList.add(candle);
                } else {
                    break;
                }

                if(tradingWindowCloseTime.isBefore(candleCloseTime)) {
                    tradingWindows.add(aggregateCandlesToTradingWindow(tempList));
                    tempList = new ArrayList<>();
                    tradingWindowCloseTime = null;
                }
            }
        }
    }

    private TradingWindow aggregateCandlesToTradingWindow(List<Candle> candles) {
        int tempListSize = candles.size();

        String symbol = candles.get(0).getSymbol();
        long openTime = candles.get(0).getOpenTime();
        long closeTime = candles.get(tempListSize-1).getCloseTime();
        double openPrice = candles.get(0).getOpenPrice();
        double highPrice = candles.stream().mapToDouble(Candle::getHighPrice).max().getAsDouble();
        double lowPrice = candles.stream().mapToDouble(Candle::getLowPrice).min().getAsDouble();
        double closePrice = candles.get(tempListSize-1).getClosePrice();
        double volume = candles.stream().mapToDouble(Candle::getVolume).sum();

        return new TradingWindow(symbol, openTime, closeTime, openPrice, highPrice, closePrice, lowPrice, volume);
    }

    private Candle parseCandleDataLine(String s) {
        String[] strs = s.split("\\|");
        String symbol = strs[0];
        String interval = strs[1];

        long openTime = Long.valueOf(strs[2]);
        long closeTime = Long.valueOf(strs[3]);
        double openPrice = Double.valueOf(strs[4]);
        double highPrice = Double.valueOf(strs[5]);
        double lowPrice = Double.valueOf(strs[6]);
        double closePrice = Double.valueOf(strs[7]);
        double volume = Double.valueOf(strs[8]);

        return new Candle(symbol, interval, openTime, closeTime, openPrice, highPrice, lowPrice, closePrice, volume);
    }

    public List<TradingWindow> getTradingWindows() {
        return this.tradingWindows;
    }

    @Override
    public List<TradingWindow> getLastNTradingWindow(int n, int tradingWindowSizeInMinutes, long curTimestamp) {
        List<TradingWindow> result = new ArrayList();
        for(int i = this.tradingWindows.size()-1; i >= 0; i--) {
            TradingWindow curTw = this.tradingWindows.get(i);
            if(curTw.getEndTimeStamp() < curTimestamp) {
                result.add(curTw);
                if(result.size() == n) break;
            }
        }
        return result;
    }

    @Override
    public TradingWindow getCurrentTradingWindow(long curTimestamp) {
        for(int i = this.tradingWindows.size()-1; i >= 0; i--) {
            TradingWindow curTw = this.tradingWindows.get(i);
            if(curTw.isBetween(curTimestamp)) {
                return curTw;
            }
        }
        return null;
    }
}
