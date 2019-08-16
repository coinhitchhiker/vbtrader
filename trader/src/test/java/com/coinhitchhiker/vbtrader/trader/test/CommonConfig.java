package com.coinhitchhiker.vbtrader.trader.test;

import com.coinhitchhiker.vbtrader.trader.config.EncryptorHelper;
import com.coinhitchhiker.vbtrader.trader.config.PropertyMapHandler;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Configuration
@Import(value = {EncryptorHelper.class,})
public class CommonConfig {

    @Bean(name="encryptorBean")
    public StringEncryptor stringEncryptor() {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setAlgorithm("PBEWithMD5AndDES");
        config.setKeyObtentionIterations("1000");
        config.setPassword(System.getenv("JASYPT_ENCRYPTOR_PASSWORD"));
        config.setPoolSize("1");
        encryptor.setConfig(config);
        return encryptor;
    }

    @Bean
    @Primary
    public PropertyMapHandler propertyMapHandler() {
        return new PropertyMapHandler();
    }

    @Bean
    @Primary
    public ApplicationEventPublisher applicationEventPublisher() {
        return Mockito.mock(ApplicationEventPublisher.class);
    }
}
