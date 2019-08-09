package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.trader.config.TraderAppConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {
        TraderITConfig.class,
})
@TestPropertySource(locations = "classpath:/com/coinhitchhiker/vbtrader/trader/test/test.properties")
@MapperScan("com.coinhitchhiker.vbtrader.trader.db")
@Transactional
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@MybatisTest
public class BaseIT {

    public BaseIT() {
    }
}
