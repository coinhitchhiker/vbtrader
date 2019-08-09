package com.coinhitchhiker.vbtrader.trader.config;

import org.jasypt.encryption.StringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EncryptorHelper {
    protected static final Logger LOGGER = LoggerFactory.getLogger(EncryptorHelper.class);

    private static final String ENCRYPTED_VALUE_PREFIX = "ENC(";
    private static final String ENCRYPTED_VALUE_SUFFIX = ")";

    @Autowired
    private StringEncryptor stringEncryptor;

    public String getDecryptedValue(final String value) {
        return stringEncryptor.decrypt(isEncrypted(value) ? getInnerEncryptedValue(value) : value);
    }

    public boolean isEncrypted (final String value) {
        if (value != null) {
            return value.startsWith(ENCRYPTED_VALUE_PREFIX) && value.endsWith(ENCRYPTED_VALUE_SUFFIX);
        }
        return false;
    }

    private static String getInnerEncryptedValue(final String value) {
        return value.substring(ENCRYPTED_VALUE_PREFIX.length(), (value.length() - ENCRYPTED_VALUE_SUFFIX.length()));
    }
}
