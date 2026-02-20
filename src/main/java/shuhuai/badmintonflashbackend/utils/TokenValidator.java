package shuhuai.badmintonflashbackend.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import shuhuai.badmintonflashbackend.auth.RequireRole;
import shuhuai.badmintonflashbackend.config.TokenConfig;
import shuhuai.badmintonflashbackend.enm.UserRole;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.response.ResponseCode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Date;
import java.util.Map;

/**
 * Token验证工具类
 * 实现HandlerInterceptor接口，用于拦截请求并验证Token
 * 提供Token的生成、解析和验证功能
 */
@Component
public class TokenValidator implements HandlerInterceptor {
    private final TokenConfig tokenConfig;

    public TokenValidator(TokenConfig tokenConfig) {
        this.tokenConfig = tokenConfig;
    }

    /**
     * 线程本地存储，用于存储用户信息
     */
    private final static ThreadLocal<Map<String, String>> threadLocal = new ThreadLocal<>();


    /**
     * 获取当前线程的用户信息
     *
     * @return 用户信息映射
     */
    public static Map<String, String> getUser() {
        return threadLocal.get();
    }

    /**
     * 设置当前线程的用户信息
     *
     * @param userIdentify 用户信息映射
     */
    public static void setUser(Map<String, String> userIdentify) {
        threadLocal.set(userIdentify);
    }

    /**
     * 移除当前线程的用户信息
     */
    public static void removeUser() {
        threadLocal.remove();
    }

    /**
     * 生成Token
     *
     * @param userId 用户ID
     * @return 生成的Token
     */
    public String getToken(Integer userId, String role) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiresAt = new Date(now + tokenConfig.getOldToken());
        return JWT.create()
                .withClaim("userId", userId.toString())
                .withClaim("role", role)
                .withIssuedAt(issuedAt)
                .withExpiresAt(expiresAt)
                .sign(Algorithm.HMAC256(tokenConfig.getPrivateKey()));
    }

    /**
     * 解析Token
     *
     * @param token Token字符串
     * @return 解析后的用户信息映射
     */
    public Map<String, String> parseToken(String token) {
        HashMap<String, String> map = new HashMap<>();
        DecodedJWT decodedjwt = JWT.require(Algorithm.HMAC256(tokenConfig.getPrivateKey())).build().verify(token);
        String userId = decodedjwt.getClaim("userId").asString();
        String role = decodedjwt.getClaim("role").asString();
        Date issuedAt = decodedjwt.getIssuedAt();
        if (userId == null || issuedAt == null) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        if (role == null || role.isBlank()) {
            role = UserRole.USER.name();
        }
        try {
            UserRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        map.put("userId", userId);
        map.put("role", role);
        map.put("issuedAt", String.valueOf(issuedAt.getTime()));
        return map;
    }

    /**
     * 请求处理前的拦截方法
     * 验证Token的有效性，并处理Token的过期逻辑
     *
     * @param httpServletRequest  HttpServletRequest对象
     * @param httpServletResponse HttpServletResponse对象
     * @param object              处理请求的对象
     * @return 是否继续处理请求
     */
    @Override
    public boolean preHandle(@NonNull HttpServletRequest httpServletRequest, @NonNull HttpServletResponse httpServletResponse, @NonNull Object object) {
        if (!(object instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        String authorization = httpServletRequest.getHeader("Authorization");
        if (authorization == null || authorization.trim().isEmpty()) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        final String bearerPrefix = "Bearer ";
        if (!authorization.startsWith(bearerPrefix)) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        String token = authorization.substring(bearerPrefix.length()).trim();
        if (token.isEmpty() || token.contains(" ")) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }

        Map<String, String> map;
        try {
            map = parseToken(token);
        } catch (TokenExpiredException e) {
            throw new BaseException(ResponseCode.TOKEN_EXPIRED);
        } catch (Exception e) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        Integer userId = Integer.parseInt(map.get("userId"));
        String role = map.get("role");
        long timeOfUse = System.currentTimeMillis() - Long.parseLong(map.get("issuedAt"));
        if (timeOfUse >= tokenConfig.getYoungToken()) {
            httpServletResponse.setHeader("Authorization", "Bearer " + getToken(userId, role));
        }

        UserRole[] requiredRoles = getRequiredRoles(handlerMethod);
        if (requiredRoles.length > 0) {
            UserRole currentRole;
            try {
                currentRole = UserRole.valueOf(role);
            } catch (IllegalArgumentException e) {
                throw new BaseException(ResponseCode.TOKEN_INVALID);
            }
            boolean permitted = Arrays.stream(requiredRoles).anyMatch(requiredRole -> hasRole(currentRole, requiredRole));
            if (!permitted) {
                throw new BaseException(ResponseCode.FORBIDDEN);
            }
        }
        setUser(map);
        return true;
    }

    private boolean hasRole(UserRole currentRole, UserRole requiredRole) {
        if (currentRole == requiredRole) {
            return true;
        }
        return currentRole == UserRole.ADMIN && requiredRole == UserRole.USER;
    }

    private UserRole[] getRequiredRoles(HandlerMethod handlerMethod) {
        RequireRole methodAnnotation = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (methodAnnotation != null) {
            return methodAnnotation.value();
        }
        RequireRole classAnnotation = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        if (classAnnotation != null) {
            return classAnnotation.value();
        }
        return new UserRole[0];
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        removeUser();
    }
}
