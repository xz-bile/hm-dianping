package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

  @Resource 
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public Result queryById(Long id) {
    //缓存穿透
    //Shop shop = querywithPassThrough(id);

    //互斥锁解决缓存击穿问题
    //Shop shop = querywithMutex(id);

    //逻辑设计过期时间
    Shop shop = querywithLogicalExpire(id);
    if(shop==null){
      return Result.fail("店铺不存在");
    }

    return Result.ok(shop);
  }

  private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

  public Shop querywithLogicalExpire(Long id) {
    String key = RedisConstants.CACHE_SHOP_KEY + id;
    // 1. 从 redis 查缓存
    String shopJson = stringRedisTemplate.opsForValue().get(key);

    // 2. 未命中（未预热或不存在），直接返回 null
    if (StrUtil.isBlank(shopJson)) {
      return null;
    }

    // 3. 命中，反序列化为 RedisData 和 Shop
    RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
    Shop shop = JSONUtil.toBean(JSONUtil.toJsonStr(redisData.getData()), Shop.class);
    LocalDateTime expireTime = redisData.getExpireTime();

    // 4. 判断是否未过期
    if (expireTime.isAfter(LocalDateTime.now())) {
      return shop;
    }

    // 5. 已过期，尝试获取互斥锁进行异步重建
    String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
    boolean isLock = tryLocal(lockKey);
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
          this.saveShop2Redis(id, 20L);
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          unlock(lockKey);
        }
      });
    }

    // 6. 返回过期数据
    return shop;
  }

  public Shop querywithMutex(Long id){
    String key="cache:shop:";
    //1,从redis查缓存
    String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);

    //2,判断是否存在
    if(StrUtil.isNotBlank(shopJson)){
      Shop shop=JSONUtil.toBean(shopJson, Shop.class);
      return shop;
    }

    //3,判断是否为空值 （null，空字符串）
    if(shopJson!=null){ 
      return null;
    }

    //4-实现缓存重建
    //4.1获取互斥锁
    String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
    boolean isLock = tryLocal(lockKey);

    //4.2判断是否获取锁成功
    if(!isLock){
      //休眠并重试
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      return querywithMutex(id);
    }

    //4.3获取锁成功，执行缓存重建
    Shop shop = getById(id);

    //5，不存在，返回错误
    if(shop==null){
      //将空值写入redis
      stringRedisTemplate.opsForValue().set(key+id,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
      return null;
    }

    //6,存在，存入redis，返回
    stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);

    //7,释放锁
    unlock(lockKey);
    
    return shop;
  }


  public Shop querywithPassThrough(Long id){
    String key="cache:shop:";
    //1,从redis查缓存
    String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);

    //2,判断是否存在
    if(StrUtil.isNotBlank(shopJson)){
      Shop shop=JSONUtil.toBean(shopJson, Shop.class);
      return shop;
    }

    //3,判断是否为空值 （null，空字符串）
    if(shopJson!=null){ 
      return null;
    }

    //4,不存在，查数据库
    Shop shop = getById(id);


    //5，不存在，返回错误
    if(shop==null){
      //将空值写入redis
      stringRedisTemplate.opsForValue().set(key+id,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);

      return null;
    }

    //6,存在，存入redis，返回
    stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);

    return shop;
  }

  @Override
  @Transactional 
  public Result update(Shop shop) {
    Long id=shop.getId();
    if(id==null){
      return Result.fail("店铺id不能为空");
    }

    //1,更新数据库
    updateById(shop);

    //2,删除缓存
    stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+shop.getId());

    return Result.ok();
  }

  private boolean tryLocal(String key){
    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);;
    return BooleanUtil.isTrue(flag); //true，防止空指针异常。（自动拆箱：引用数据类型-->基本数据）
  }

  private void unlock(String key){
    stringRedisTemplate.delete(key);
  }


  public  void saveShop2Redis(Long id,Long expireSeconds){
    //1,查询店铺数据
    Shop shop=getById(id);

    //2,封装逻辑时间
    RedisData redisData=new RedisData();
    redisData.setData(shop);
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
    
    //3,写入redis
    stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(redisData));
  }

}

