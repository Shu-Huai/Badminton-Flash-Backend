package shuhuai.badmintonflashbackend.utils;

import org.springframework.util.DigestUtils;

/**
 * 哈希计算工具类
 * 提供字符串哈希值计算功能
 */
public class HashComputer {
    /**
     * 获取字符串的MD5哈希值（大写）
     *
     * @param str 待哈希的字符串
     * @return 字符串的MD5哈希值（大写）
     */
    public static String getHashedString(String str) {
        return DigestUtils.md5DigestAsHex(str.getBytes()).toUpperCase();
    }
}