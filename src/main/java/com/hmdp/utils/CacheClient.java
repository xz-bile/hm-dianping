package com.hmdp.utils;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.hmdp.entity.Shop;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;

@Component 
@Slf4j 
@Api 
public class CacheClient {
  
  private final StringRedisTemplate stringRedisTemplate;

  public CacheClient(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  public void set(String key, Object value, Long time, TimeUnit unit) {
    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
  }

  public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
    //设置逻辑过期
    RedisData redishData = new RedisData();
    redishData.setData(value);
    redishData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

    //写入redis
    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redishData));
  }

  public <R,ID>R querywithPassThrough(String keyPrefix, ID id , Class<R>type, Function<ID,R>dbFallback,Long time, TimeUnit unit){
    String key=keyPrefix+id;
    //1,从redis查缓存
    String Json = stringRedisTemplate.opsForValue().get(key);

    //2,判断是否存在
    if(StrUtil.isNotBlank(Json)){
      return JSONUtil.toBean(Json, type);
    }

    //3,判断是否为空值 （null，空字符串）
    if(Json!=null){ 
      return null;
    }

    //4,不存在，查数据库
    R r = dbFallback.apply(id);

    //5，不存在，返回错误
    if(r==null){
      //将空值写入redis
      stringRedisTemplate.opsForValue().set(key+id,"",time,unit);
      return null;
    }

    //6,存在，存入redis，返回
    this.set(key, r, time, unit);
  
    return r;
  }

  private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

  public <R,ID>R uerywithLogicalExpire(String keypreFix,ID id,Class<R>type,Function<ID,R>dbfallback,Long time,TimeUnit unit) {

    String key = keypreFix + id; 
    // 1. 从 redis 查缓存
    String Json = stringRedisTemplate.opsForValue().get(key);

    // 2. 未命中（未预热或不存在），直接返回 null
    if (StrUtil.isBlank(Json)) {
      return null;
    }

    // 3. 命中，反序列化为 RedisData 和 Shop
    RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
    R r= JSONUtil.toBean(JSONUtil.toJsonStr(redisData.getData()),type);
    LocalDateTime expireTime = redisData.getExpireTime();

    // 4. 判断是否未过期
    if (expireTime.isAfter(LocalDateTime.now())) {
      return r;
    }

    // 5. 已过期，尝试获取互斥锁进行异步重建
    String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
    boolean isLock = tryLock(lockKey);
    if (isLock) {
      executorService.submit(() -> {
        try {
          // 双重检查：重新查一次最新缓存，看是否已被其他线程重建
          String latestJson = stringRedisTemplate.opsForValue().get(key);
          if (StrUtil.isNotBlank(latestJson)) {
            RedisData latestData = JSONUtil.toBean(latestJson, RedisData.class);
            if (latestData.getExpireTime().isAfter(LocalDateTime.now())) {
              return; // 已被重建且未过期，无需重复查库
            }
          }
          // 重建缓存
          R r1 = dbfallback.apply(id);

          //写入redis
          this.setWithLogicalExpire(key, r1, time, unit);

        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          unlock(lockKey);
        }
      });
    }

    // 6. 返回过期数据
    return r;
  }

  private boolean tryLock(String key){
    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);;
    return BooleanUtil.isTrue(flag); //true，防止空指针异常。（自动拆箱：引用数据类型-->基本数据）
  }

  private void unlock(String key){
    stringRedisTemplate.delete(key);
  }

}
