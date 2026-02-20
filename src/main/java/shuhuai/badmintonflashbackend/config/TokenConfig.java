package shuhuai.badmintonflashbackend.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Component
public class TokenConfig {
    /**
     * Token私钥
     */
    @Value("${token.privateKey}")
    private String privateKey;

    @Value("${token.accessTokenTtl}")
    private Long accessTokenTtl;

    @Value("${token.refreshTokenTtl}")
    private Long refreshTokenTtl;
}
