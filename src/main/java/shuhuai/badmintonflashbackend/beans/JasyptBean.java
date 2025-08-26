package shuhuai.badmintonflashbackend.beans;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.utils.JasyptComputer;

@Component
public class JasyptBean {
    @Bean("jasyptComputer")
    public StringEncryptor jasyptComputer() {
        return new JasyptComputer();
    }
}