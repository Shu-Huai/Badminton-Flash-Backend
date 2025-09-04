package shuhuai.badmintonflashbackend.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.response.Response;
import shuhuai.badmintonflashbackend.response.ResponseCode;

@Slf4j
@ControllerAdvice
public class BaseController {
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
}