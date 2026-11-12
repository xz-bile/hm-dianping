package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpSession;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
@Slf4j

public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

  @Override
  public Result sendCode(String phone, HttpSession session) {
    // 1，校验手机号
    if (RegexUtils.isPhoneInvalid(phone)) {
      return Result.fail("手机号格式错误");
    }

    // 3,生成验证码
    String code ="123456";

    // 4,保存验证码到session
    session.setAttribute("code", code);

    // 5,发送验证码
    log.debug("发送短信验证码成功，验证码：{}", code);
    return Result.ok();
  }

  @Override
  public Result login(LoginFormDTO loginForm, HttpSession session) {
    // 1,校验验证码和手机号
    String code = loginForm.getCode();
    String phone = loginForm.getPhone();
    if (RegexUtils.isPhoneInvalid(phone)) {
      return Result.fail("手机号格式错误");
    }
    if (code == null || !code.equals(session.getAttribute("code"))) {
      return Result.fail("验证码错误");
    }

    // 2,根据手机号查询用户
    User user = query().eq("phone", phone).one();

    // 3,判断用户是否存在
    if (user == null) {
      // 4,如果不存在，创建新用户并保存到数据库
      user = createUserByPhone(phone);
    }

    // 5,保存用户到session并返回
    session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

    return Result.ok();
  }

  private User createUserByPhone(String phone) {
    User user = User.builder()
        .phone(phone)
        .nickName("user_" + RandomUtil.randomString(10))
        .build();
    return user;
  }
}
