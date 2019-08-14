package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.common.OrderInfo;
import com.coinhitchhiker.vbtrader.common.OrderSide;
import com.coinhitchhiker.vbtrader.common.OrderStatus;
import com.coinhitchhiker.vbtrader.common.TradingWindow;
import com.coinhitchhiker.vbtrader.trader.db.TraderDAO;
import com.google.gson.Gson;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore
public class ITDBTest extends BaseIT {

    @Autowired
    private TraderDAO traderDAO;

    @Test
    public void validateDBConnection() {
        int i = traderDAO.validateConnection();
        assertThat(i).isEqualTo(1);
    }


    @Test
    public void testLogCompleteTransaction() {
        TradingWindow tw = new TradingWindow("BTCUSDT", 100, 100, 100.0D);
        OrderInfo buyOrder = new OrderInfo("BINANCE", "BTCUSDT", OrderSide.BUY, 100.0D, 100.0D);
        buyOrder.setFeeCurrency("BNB");
        buyOrder.setFeePaid(10.0D);
        buyOrder.setAmountExecuted(100.0D);
        buyOrder.setExecTimestamp(1000000L);
        buyOrder.setExternalOrderId("waoiejfwe");
        buyOrder.setOrderStatus(OrderStatus.COMPLETE);
        buyOrder.setPriceExecuted(100.0D);

        tw.setBuyOrder(buyOrder);
        tw.setBuyFee(0.001D);
        tw.setSellFee(0.001D);
        tw.setProfit(1.0D);
        tw.setNetProfit(0.01D);

        traderDAO.logCompleteTransaction(new Gson().toJson(tw));
    }

}
