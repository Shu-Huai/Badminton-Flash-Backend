package shuhuai.badmintonflashbackend.utils;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import shuhuai.badmintonflashbackend.config.JasyptConfig;

public class JasyptComputer implements StringEncryptor {
    private final JasyptConfig jasyptConfig;

    public JasyptComputer(JasyptConfig jasyptConfig) {
        this.jasyptConfig = jasyptConfig;
    }

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
        config.setPassword(jasyptConfig.getPassword());
        config.setAlgorithm(jasyptConfig.getAlgorithm());
        config.setKeyObtentionIterations(1000);
        config.setPoolSize(1);
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.NoIvGenerator");
        config.setStringOutputType("base64");
        return config;
    }
}