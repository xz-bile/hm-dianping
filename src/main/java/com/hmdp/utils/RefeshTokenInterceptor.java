package com.hmdp.utils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;

//拦截器
public class RefeshTokenInterceptor implements HandlerInterceptor {

  //配置类注入
  private StringRedisTemplate stringRedisTemplate; 

  public RefeshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  // 拦截器
  @Override 
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    //1,获取请求头中的token
    String token=request.getHeader("authorization");
    if(StrUtil.isBlank(token)){
      return true;
    }

    //2,获取token中的用户
    Map<Object,Object>userMap=stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY+token);
    
    //3,判断用户是否存在
    if(userMap.isEmpty()){
      return true;
    }

    //将查询到的hash对象转成User对象
    UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

    //5,存在，保存用户信息到ThreadLocal
    UserHolder.saveUser(userDTO);

    //刷新token有效期
    stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

    //6，放行
    return true;
  }

  @Override 
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    //移除用户
    UserHolder.removeUser();
  }
  
}
