package com.hmdp.utils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component 
public class RedisWorker {

  private static final long BEGIN_TIMESTAMP=1795255479L;

  //序列号位数
  private static final int COUNT_BITS=32;

  @Resource 
  private StringRedisTemplate stringRedisTemplate;
  
  public long nextId(String keyPrefix){
    //1,时间戳
    LocalDateTime now=LocalDateTime.now();
    long currentSecond=now.toEpochSecond(ZoneOffset.UTC);
    long timestamp=currentSecond-BEGIN_TIMESTAMP;

    //2,序列号
    //2.1获取当前日期(key要唯一，好辨识)
    String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
    Long count = stringRedisTemplate.opsForValue().increment("icr:"+keyPrefix+":"+date);

    //3,拼接
    return timestamp<<COUNT_BITS|count;
  }

}
