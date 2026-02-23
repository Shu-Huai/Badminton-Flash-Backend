package shuhuai.badmintonflashbackend.config;

import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReserveModeConfig {
    @Value("${reserve.mode}")
    private String reserveMode;
}
