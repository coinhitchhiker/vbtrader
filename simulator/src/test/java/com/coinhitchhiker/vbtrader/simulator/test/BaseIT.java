package com.coinhitchhiker.vbtrader.simulator.test;

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
@ContextConfiguration(classes = TraderITConfig.class)
@TestPropertySource(locations = "classpath:/com/coinhitchhiker/vbtrader/simulator/test.properties")
@MapperScan("com.coinhitchhiker.vbtrader.simulator.db")
@Transactional
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@MybatisTest
public class BaseIT {

    public BaseIT() {
    }

    @Test
    public void dummy() {

    }
}
