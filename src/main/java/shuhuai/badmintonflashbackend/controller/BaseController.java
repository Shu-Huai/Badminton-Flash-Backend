package shuhuai.badmintonflashbackend.controller;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.exceptions.PersistenceException;
import org.jspecify.annotations.Nullable;
import org.mybatis.spring.MyBatisSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.response.Response;
import shuhuai.badmintonflashbackend.response.ResponseCode;
import shuhuai.badmintonflashbackend.utils.RequestGetter;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class BaseController implements ResponseBodyAdvice<Object> {
    private static final String APP_PACKAGE = "shuhuai.badmintonflashbackend";

    private Logger resolveSuccessLogger(@Nonnull MethodParameter methodParameter) {
        return LoggerFactory.getLogger(methodParameter.getContainingClass());
    }

    private Logger resolveExceptionLogger(@Nullable Throwable error) {
        if (error != null) {
            for (StackTraceElement element : error.getStackTrace()) {
                String className = element.getClassName();
                if (!className.startsWith(APP_PACKAGE) || className.equals(BaseController.class.getName())) {
                    continue;
                }
                if (!className.contains(".service.") && !className.contains(".controller.")) {
                    continue;
                }
                try {
                    Class<?> clazz = Class.forName(className, false, BaseController.class.getClassLoader());
                    return LoggerFactory.getLogger(clazz);
                } catch (ClassNotFoundException ignored) {
                    return LoggerFactory.getLogger(className);
                }
            }
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
            if (handler instanceof HandlerMethod handlerMethod) {
                return LoggerFactory.getLogger(handlerMethod.getBeanType());
            }
        }
        return log;
    }

    private void logSuccess(@Nonnull MethodParameter methodParameter, String message) {
        resolveSuccessLogger(methodParameter).info("{}：{}", RequestGetter.getRequestUrl(), message);
    }

    private void logError(@Nullable Throwable error, String message) {
        resolveExceptionLogger(error).error("{}：{}", RequestGetter.getRequestUrl(), message);
    }

    private void logWarning(@Nullable Throwable error, String message) {
        resolveExceptionLogger(error).warn("{}：{}", RequestGetter.getRequestUrl(), message);
    }

    /**
     * 处理Spring参数异常
     *
     * @param error Spring参数异常对象
     * @return 异常响应结果
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            BindException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestPartException.class,
            TypeMismatchException.class
    })
    public Response<Object> handleSpringParamsException(Exception error) {
        logError(error, error.getMessage());
        return new Response<>(ResponseCode.PARAM_ERROR, error.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Response<Object> handleServiceException(RuntimeException error) {
        Response<Object> response = new Response<>();
        switch (error) {
            case BaseException baseException -> {
                response.setCode(baseException.getResponseCode());
                logWarning(error, error.getMessage());
            }
            case null, default -> {
                if (error != null) {
                    logError(error, error.getMessage());
                }
                response.setCode(ResponseCode.ERROR);
            }
        }
        return response;
    }

    /**
     * 处理数据库异常
     *
     * @param error 数据库异常对象
     * @return 异常响应结果
     */
    @ExceptionHandler({
            MyBatisSystemException.class,
            PersistenceException.class,
            BindingException.class,
            DataAccessException.class,
            SQLException.class,
            DuplicateKeyException.class
    })
    public Response<Object> handleDatabaseException(Exception error) {
        // 记录异常日志
        logError(error, "数据库/MyBatis异常：" + error.getMessage());
        // 处理数据完整性异常
        if (error instanceof DataIntegrityViolationException
                || error instanceof SQLIntegrityConstraintViolationException) {
            return new Response<>(ResponseCode.PARAM_ERROR);
        }
        // 返回数据库错误响应
        return new Response<>(ResponseCode.DB_ERROR);
    }

    /**
     * 处理未捕获的异常
     *
     * @param error 未捕获的异常对象
     * @return 异常响应结果
     */
    @ExceptionHandler(Exception.class)
    public Response<Object> handleException(Exception error) {
        // 记录异常日志
        logError(error, "未处理异常：" + error.getMessage());
        // 返回服务器错误响应
        return new Response<>(ResponseCode.ERROR, error.getMessage());
    }

    /**
     * 判断是否支持对响应体进行处理
     *
     * @param methodParameter 方法参数
     * @param aClass          消息转换器类
     * @return 始终返回true，表示支持所有响应体处理
     */
    @Override
    public boolean supports(@Nonnull MethodParameter methodParameter, @Nonnull Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    /**
     * 在响应体写入之前进行处理
     * 记录响应的日志信息，根据响应码判断是成功还是失败
     *
     * @param body              响应体
     * @param methodParameter   方法参数
     * @param mediaType         媒体类型
     * @param aClass            消息转换器类
     * @param serverHttpRequest 请求对象
     * @param serverHttpResponse 响应对象
     * @return 处理后的响应体
     */
    @Nullable
    @Override
    public Object beforeBodyWrite(@Nullable Object body, @Nonnull MethodParameter methodParameter, @Nonnull MediaType mediaType,
                                  @Nonnull Class<? extends HttpMessageConverter<?>> aClass, @Nonnull ServerHttpRequest serverHttpRequest,
                                  @Nonnull ServerHttpResponse serverHttpResponse) {
        if (body instanceof Response<?> response) {
            if (response.getCode() == 200) {
                logSuccess(methodParameter, response.getMessage());
            }
        }
        return body;
    }

}
