package shuhuai.badmintonflashbackend.config;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

public class JasyptConfig {
    @Value("${jasypt.encryptor.password}")
    private String password;
    @Value("${jasypt.encryptor.algorithm}")
    private String algorithm;
}