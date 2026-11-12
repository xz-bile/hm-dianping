package com.hmdp.config;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hmdp.utils.LoginInterceptor;

public class MvcConfig implements  WebMvcConfigurer{
    //配置拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                  "/user/code",
                  "/user/login",
                  "/blog/hot",
                  "/shop/**",
                  "/shop-type/**",
                  "/upload/**",
                  "/voucher/**"
                );
    }
}
