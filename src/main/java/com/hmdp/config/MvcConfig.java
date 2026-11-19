package com.hmdp.config;

import javax.annotation.Resource;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefeshTokenInterceptor;

@Configuration 
public class MvcConfig implements  WebMvcConfigurer{

    @Resource 
    private StringRedisTemplate stringRedisTemplate;

    //配置拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
      //登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                  "/user/code",
                  "/user/login",
                  "/blog/hot",
                  "/shop/**",
                  "/shop-type/**",
                  "/upload/**",
                  "/voucher/**"
                ).order(1);
                
        //刷新token拦截器
        registry.addInterceptor(new RefeshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order((0));
    }
}
