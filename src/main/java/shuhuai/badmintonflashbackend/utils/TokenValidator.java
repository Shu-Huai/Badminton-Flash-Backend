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
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
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
    public String getAccessToken(Integer userId, String role) {
        return buildToken(userId, role, TOKEN_TYPE_ACCESS, tokenConfig.getAccessTokenTtl());
    }

    public String getRefreshToken(Integer userId) {
        return buildToken(userId, null, TOKEN_TYPE_REFRESH, tokenConfig.getRefreshTokenTtl());
    }

    private String buildToken(Integer userId, String role, String tokenType, Long ttlMillis) {
        if (userId == null || ttlMillis == null || ttlMillis <= 0) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiresAt = new Date(now + ttlMillis);
        var builder = JWT.create()
                .withClaim("userId", userId.toString())
                .withClaim("tokenType", tokenType)
                .withIssuedAt(issuedAt)
                .withExpiresAt(expiresAt);
        if (role != null && !role.isBlank()) {
            builder.withClaim("role", role);
        }
        return builder.sign(Algorithm.HMAC256(tokenConfig.getPrivateKey()));
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
        String tokenType = decodedjwt.getClaim("tokenType").asString();
        Date issuedAt = decodedjwt.getIssuedAt();
        if (userId == null || issuedAt == null) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        if (tokenType == null || tokenType.isBlank()) {
            tokenType = TOKEN_TYPE_ACCESS;
        }
        if (!TOKEN_TYPE_ACCESS.equals(tokenType) && !TOKEN_TYPE_REFRESH.equals(tokenType)) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        if (role == null || role.isBlank()) {
            role = UserRole.USER.name();
        }
        if (TOKEN_TYPE_ACCESS.equals(tokenType)) {
            try {
                UserRole.valueOf(role);
            } catch (IllegalArgumentException e) {
                throw new BaseException(ResponseCode.TOKEN_INVALID);
            }
        }
        map.put("userId", userId);
        map.put("role", role);
        map.put("tokenType", tokenType);
        map.put("issuedAt", String.valueOf(issuedAt.getTime()));
        return map;
    }

    public Integer verifyRefreshToken(String token) {
        Map<String, String> map;
        try {
            map = parseToken(token);
        } catch (TokenExpiredException e) {
            throw new BaseException(ResponseCode.TOKEN_EXPIRED);
        } catch (Exception e) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        if (!TOKEN_TYPE_REFRESH.equals(map.get("tokenType"))) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        try {
            return Integer.parseInt(map.get("userId"));
        } catch (NumberFormatException e) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
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
        if (!TOKEN_TYPE_ACCESS.equals(map.get("tokenType"))) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        try {
            Integer.parseInt(map.get("userId"));
        } catch (NumberFormatException e) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        String role = map.get("role");

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
