package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.common.model.*;
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
    @Ignore
    public void validateDBConnection() {
        int i = traderDAO.validateConnection();
        assertThat(i).isEqualTo(1);
    }


    @Test
    @Ignore
    public void testLogCompleteTransaction() {
        TradingWindow tw = new TradingWindow("BTCUSDT", 100, 100, 100.0D);
        OrderInfo buyOrder = new OrderInfo(ExchangeEnum.BINANCE, "BTCUSDT", OrderSide.BUY, 100.0D, 100.0D);
        buyOrder.setFeeCurrency("BNB");
        buyOrder.setFeePaid(10.0D);
        buyOrder.setAmountExecuted(100.0D);
        buyOrder.setExecTimestamp(1000000L);
        buyOrder.setExternalOrderId("waoiejfwe");
        buyOrder.setOrderStatus(OrderStatus.COMPLETE);
        buyOrder.setPriceExecuted(100.0D);

        traderDAO.logCompleteTransaction(new Gson().toJson(tw));
    }

}
