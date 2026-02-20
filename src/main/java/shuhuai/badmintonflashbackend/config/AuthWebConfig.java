package shuhuai.badmintonflashbackend.config;


import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import shuhuai.badmintonflashbackend.utils.TokenValidator;

/**
 * 认证Web配置类
 * 配置Token验证拦截器，用于保护API接口
 */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {
    /**
     * Token验证器，用于验证请求中的Token
     */
    @Resource
    TokenValidator tokenValidator;

    /**
     * 配置拦截器
     * 添加Token验证拦截器，并设置拦截路径和排除路径
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenValidator)
                // 拦截所有API路径
                .addPathPatterns("/**")
                .excludePathPatterns("/v3/api-docs/**")
                .excludePathPatterns("/doc.html", "/webjars/**", "/swagger-resources/**")
                // 排除生成Token的测试路径
                .excludePathPatterns("/test/generate-token")
                // 排除登录路径
                .excludePathPatterns("/auth/login")
                // 排除注册路径
                .excludePathPatterns("/auth/register")
                // 排除刷新路径
                .excludePathPatterns("/auth/refresh")
                // 排除协议路径
                .excludePathPatterns("/user/protocol");
    }
}
