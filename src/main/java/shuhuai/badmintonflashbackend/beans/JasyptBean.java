package shuhuai.badmintonflashbackend.beans;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.config.JasyptConfig;
import shuhuai.badmintonflashbackend.utils.JasyptComputer;

@Component
public class JasyptBean {
    private final JasyptConfig jasyptConfig;

    @Autowired
    public JasyptBean(JasyptConfig jasyptConfig) {
        this.jasyptConfig = jasyptConfig;
    }

    @Bean("jasyptComputer")
    public StringEncryptor jasyptComputer() {
        return new JasyptComputer(jasyptConfig);
    }
}