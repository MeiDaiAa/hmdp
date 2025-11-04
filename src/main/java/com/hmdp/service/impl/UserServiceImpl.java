package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.exception.ServiceException;
import com.hmdp.exception.UserUnsignedException;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IMailService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.rowset.serial.SerialException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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
    private boolean save;

    /**
     * 发送验证码
     *
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

    /**
     * 登录功能
     *
     * @param loginForm 登录参数
     * @return token
     */
    @SneakyThrows
    @Override
    public String login(LoginFormDTO loginForm) {
        // 0. 查看验证码是否正确
        String cacheCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        if (RegexUtils.isCodeInvalid(loginForm.getCode()) || !ObjectUtil.equals(cacheCode, loginForm.getCode())) {
            throw new ServiceException("验证码错误或无效");
        }
        // 1. 查看是否已注册
        User user = query().eq("phone", loginForm.getPhone()).one();
        //  1.1 未注册
        if (user == null) {
            // 1.2 注册用户
            user = saveUser(loginForm);
        }
        // 2. 将用户信息存到Redis中
        //  2.1 生成 token
        String token = RandomUtil.randomString(32);
        //  2.2 redis存储用户信息, 将user转为UserDTO再转为map
        Map<String, Object> userMap = BeanUtil.beanToMap(BeanUtil.copyProperties(user, UserDTO.class),
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
        );
        // 2.3 存储
        redisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        //  2.3.1 设置有效期为30分钟
        redisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 3. 返回token
        return token;
    }

    /**
     * 保存用户
     *
     * @param loginForm 登录参数
     * @return 用户
     */
    private User saveUser(LoginFormDTO loginForm) {
        User user = new User()
                .setPhone(loginForm.getPhone())
                .setPassword(loginForm.getPassword())
                .setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
