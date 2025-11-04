package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.entity.User;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送邮箱验证码
     */
    void sendCode(String mail);

    /**
     * 登录功能
     * @param loginForm 登录参数
     * @return token
     */
    String login(LoginFormDTO loginForm);
}
