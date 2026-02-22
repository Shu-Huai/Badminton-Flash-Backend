package shuhuai.badmintonflashbackend.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
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
import shuhuai.badmintonflashbackend.service.IUserService;

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
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_ISSUED_AT = "issuedAt";
    private static final String BEARER_PREFIX = "Bearer ";
    private final TokenConfig tokenConfig;
    private final IUserService userService;
    /**
     * 线程本地存储，用于存储用户信息
     */
    private final static ThreadLocal<Map<String, String>> threadLocal = new ThreadLocal<>();

    public TokenValidator(TokenConfig tokenConfig, IUserService userService) {
        this.tokenConfig = tokenConfig;
        this.userService = userService;
    }

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
    public String getToken(Integer userId, UserRole role) {
        if (userId == null || role == null) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        Date issuedAt = new Date();
        return JWT.create()
                .withClaim(CLAIM_USER_ID, userId.toString())
                .withClaim(CLAIM_ROLE, role.name())
                .withIssuedAt(issuedAt)
                .withClaim(CLAIM_ISSUED_AT, issuedAt.getTime())
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
        DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(tokenConfig.getPrivateKey())).build().verify(token);

        String userId = decodedJWT.getClaim(CLAIM_USER_ID).asString();
        String role = decodedJWT.getClaim(CLAIM_ROLE).asString();
        Date issuedAt = decodedJWT.getIssuedAt();
        Long issuedAtMillis = decodedJWT.getClaim(CLAIM_ISSUED_AT).asLong();
        if (issuedAtMillis == null && issuedAt != null) {
            issuedAtMillis = issuedAt.getTime();
        }
        if (userId == null || userId.isBlank() || role == null || role.isBlank() || issuedAtMillis == null) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        try {
            Integer.parseInt(userId);
            UserRole.valueOf(role);
        } catch (Exception e) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        map.put(CLAIM_USER_ID, userId);
        map.put(CLAIM_ROLE, role);
        map.put(CLAIM_ISSUED_AT, String.valueOf(issuedAtMillis));
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
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty() || token.contains(" ")) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        Map<String, String> map;
        try {
            map = parseToken(token);
        } catch (Exception e) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }

        long timeOfUse;
        try {
            long issuedAtMillis = Long.parseLong(map.get(CLAIM_ISSUED_AT));
            timeOfUse = System.currentTimeMillis() - issuedAtMillis;
        } catch (Exception e) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }


        RoleRequirement requirement = getRoleRequirement(handlerMethod);
        if (requirement.roles().length > 0) {
            UserRole currentRole = UserRole.valueOf(map.get(CLAIM_ROLE));
            boolean permitted = Arrays.stream(requirement.roles())
                    .anyMatch(requiredRole -> hasRole(currentRole, requiredRole));
            if (!permitted) {
                throw new BaseException(ResponseCode.FORBIDDEN);
            }
        }
        if (requirement.dbCheck() && requirement.roles().length > 0) {
            Integer userId = Integer.parseInt(map.get(CLAIM_USER_ID));
            UserRole dbRole = userService.getRole(userId);
            boolean dbPermitted = Arrays.stream(requirement.roles())
                    .anyMatch(requiredRole -> hasRole(dbRole, requiredRole));
            if (!dbPermitted) {
                throw new BaseException(ResponseCode.FORBIDDEN);
            }
            map.put(CLAIM_ROLE, dbRole.name());
        }
        if (timeOfUse >= tokenConfig.getYoungToken() && timeOfUse < tokenConfig.getOldToken()) {
            Integer userId = Integer.parseInt(map.get(CLAIM_USER_ID));
            UserRole role = UserRole.valueOf(map.get(CLAIM_ROLE));
            httpServletResponse.setHeader("Authorization", BEARER_PREFIX + getToken(userId, role));
        } else if (timeOfUse >= tokenConfig.getOldToken()) {
            throw new BaseException(ResponseCode.TOKEN_EXPIRED);
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

    private RoleRequirement getRoleRequirement(HandlerMethod handlerMethod) {
        RequireRole methodAnnotation = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (methodAnnotation != null) {
            return new RoleRequirement(methodAnnotation.value(), methodAnnotation.dbCheck());
        }
        RequireRole classAnnotation = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        if (classAnnotation != null) {
            return new RoleRequirement(classAnnotation.value(), classAnnotation.dbCheck());
        }
        return new RoleRequirement(new UserRole[0], false);
    }

    private record RoleRequirement(UserRole[] roles, boolean dbCheck) {
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        removeUser();
    }
}
