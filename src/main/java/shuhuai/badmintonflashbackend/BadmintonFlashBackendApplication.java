package shuhuai.badmintonflashbackend;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import shuhuai.badmintonflashbackend.utils.JasyptComputer;

@SpringBootApplication
@EnableEncryptableProperties
@EnableScheduling
public class BadmintonFlashBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BadmintonFlashBackendApplication.class, args);
    }
}
