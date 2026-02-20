package shuhuai.badmintonflashbackend.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.exceptions.PersistenceException;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.response.Response;
import shuhuai.badmintonflashbackend.response.ResponseCode;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

@Slf4j
@ControllerAdvice
public class BaseController {
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
        return new Response<>(ResponseCode.PARAM_ERROR, error.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Response<Object> handleServiceException(RuntimeException error) {
        Response<Object> response = new Response<>();
        switch (error) {
            case BaseException baseException -> response.setCode(baseException.getResponseCode());
            case null, default -> {
                if (error != null) {
                    log.error(error.getMessage());
                }
                response.setCode(ResponseCode.ERROR);
            }
        }
        return response;
    }

    /**
     * 处理数据库异常
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
        log.error("数据库/MyBatis异常", error);
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
     * @param error 未捕获的异常对象
     * @return 异常响应结果
     */
    @ExceptionHandler(Exception.class)
    public Response<Object> handleException(Exception error) {
        // 记录异常日志
        log.error("未处理异常", error);
        // 返回服务器错误响应
        return new Response<>(ResponseCode.ERROR, error.getMessage());
    }
}
