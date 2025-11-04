package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.exception.UserUnsignedException;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate redisTemplate;
    public RefreshTokenInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取session中的token
        String authorization = request.getHeader("authorization");
        // 2. 判断token是否存在,不存在直接放行
        if (StrUtil.isBlank(authorization)) {
            return true;
        }
        // 3. 获取redis中存储的对象
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(LOGIN_USER_KEY + authorization);
        //  3.1 判断对象是否存在,不存在直接放行
        if (entries.isEmpty()) {
            return true;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);
        // 4. 存在,将userDTO保存到ThreadLocal
        UserHolder.saveUser(userDTO);
        // 5. 刷新token有效期
        redisTemplate.expire(LOGIN_USER_KEY + authorization, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }
}
