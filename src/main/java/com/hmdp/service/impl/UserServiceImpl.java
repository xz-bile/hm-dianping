package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1，校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        // 2，生成验证码
        String code = "123456"; 

        // 3，保存验证码到 Redis (并设置有效期)
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + phone,
                code,
                RedisConstants.LOGIN_CODE_TTL,
                TimeUnit.MINUTES
        );

        // 4，发送验证码（这里模拟发送，打印日志）
        log.debug("发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1, 获取手机号和验证码
        String phone = loginForm.getPhone();
        
        String inputCode = loginForm.getCode();

        // 2, 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        // 3, 从 Redis 中获取真实的验证码
        String redisCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);

        // 🚨 修复2：将用户输入的验证码与 Redis 中的验证码进行比对（不再使用 Session）
        if (inputCode == null || redisCode == null || !inputCode.equals(redisCode)) {
            return Result.fail("验证码错误");
        }

        // 4, 根据手机号查询用户
        User user = query().eq("phone", phone).one();

        // 5, 判断用户是否存在
        if (user == null) {
            // 如果不存在，创建新用户
            user = createUserByPhone(phone);
        }

        // 6, 保存用户到 Redis 并返回 Token
        // 6.1 生成 token 作为登录令牌 (去掉中划线)
        String token = UUID.randomUUID().toString(true);

        // 6.2 将 User 对象转为 UserDTO (隐藏敏感信息)
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        // 6.3 将 UserDTO 对象转为 Hash 结构存储
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        // 字段值编辑器：将非 String 类型的值转为 String
                        .setFieldValueEditor((fieldName, fieldValue) -> {
                            if (fieldValue == null) {
                                return null;
                            }
                            return fieldValue.toString();
                        })
        );

        // 6.4 存储到 Redis Hash 结构
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        // 6.5 设置 token 的过期时间
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 6.6 返回 token
        return Result.ok(token);
    }

    private User createUserByPhone(String phone) {
        User user = User.builder()
                .phone(phone)
                .nickName("user_" + RandomUtil.randomString(10))
                .build();
        save(user);
        return user;
    }
}