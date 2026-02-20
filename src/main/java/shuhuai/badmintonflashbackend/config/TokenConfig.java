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

    /**
     * 年轻Token过期时间（毫秒）
     */
    @Value("${token.youngToken}")
    private Long youngToken;

    /**
     * 旧Token过期时间（毫秒）
     */
    @Value("${token.oldToken}")
    private Long oldToken;
}
