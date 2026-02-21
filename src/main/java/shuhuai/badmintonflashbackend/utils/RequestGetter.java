package shuhuai.badmintonflashbackend.utils;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 请求获取工具类
 * 提供获取HTTP请求相关对象的静态方法
 */
@SuppressWarnings("unused")
public class RequestGetter {
    /**
     * 获取HttpServletRequest对象
     *
     * @return HttpServletRequest对象
     */
    public static HttpServletRequest getRequest() {
        return getRequestAttributes().getRequest();
    }

    /**
     * 获取HttpServletResponse对象
     *
     * @return HttpServletResponse对象
     */
    public static HttpServletResponse getResponse() {
        return getRequestAttributes().getResponse();
    }

    /**
     * 获取完整的请求URL
     *
     * @return 完整的请求URL
     */
    public static String getRequestUrl() {
        HttpServletRequest request = RequestGetter.getRequest();
        String url = request.getScheme() + "://" + request.getServerName() + (request.getServerPort() == 80 ? "" : ":" + request.getServerPort()) + request.getContextPath()
                + request.getRequestURI() + (request.getQueryString() == null ? "" : (request.getQueryString().isEmpty() ? "" : "?" + request.getQueryString()));
        return java.net.URLDecoder.decode(url, StandardCharsets.UTF_8);
    }

    /**
     * 获取HttpSession对象
     *
     * @return HttpSession对象
     */
    public static HttpSession getSession() {
        return getRequest().getSession();
    }

    /**
     * 获取ServletRequestAttributes对象
     *
     * @return ServletRequestAttributes对象
     */
    public static ServletRequestAttributes getRequestAttributes() {
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes());
    }

    /**
     * 获取ServletContext对象
     *
     * @return ServletContext对象
     */
    public static ServletContext getServletContext() {
        return Objects.requireNonNull(ContextLoader.getCurrentWebApplicationContext()).getServletContext();
    }
}