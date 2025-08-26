package shuhuai.badmintonflashbackend.utils;

import lombok.NoArgsConstructor;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.annotation.Value;

@NoArgsConstructor
public class JasyptComputer implements StringEncryptor {
    @Value("${jasypt.encryptor.password}")
    private String password;
    @Value("${jasypt.encryptor.algorithm}")
    private String algorithm;

    @Override
    public String encrypt(String message) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setConfig(getConfig());
        return encryptor.encrypt(message);
    }

    @Override
    public String decrypt(String encryptedMessage) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setConfig(getConfig());
        return encryptor.decrypt(encryptedMessage);
    }

    public SimpleStringPBEConfig getConfig() {
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(this.password);
        config.setAlgorithm(this.algorithm);
        config.setKeyObtentionIterations(1000);
        config.setPoolSize(1);
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.NoIvGenerator");
        config.setStringOutputType("base64");
        return config;
    }
}