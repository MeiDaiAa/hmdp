package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IMailService;
import com.hmdp.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author meidaia
 * @since 2025-11-4
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private IMailService mailService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    /**
     * 发送验证码
     * @param mail 手机号
     */
    @Override
    public void sendCode(String mail) {
        // 1. 生成验证码
        String code = RandomUtil.randomString(6);
        // 2. 保存验证码到Redis, 5分钟过期
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + mail, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 3. 发送验证码
        mailService.sendMail(mail, "登录验证码", "您的验证码是：" + code + " ，5分钟内有效。");
    }
}
