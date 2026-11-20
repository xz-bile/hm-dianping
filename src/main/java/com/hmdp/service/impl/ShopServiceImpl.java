package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONConfig;

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

    @Resource 
    private CacheClient cacheClient;

    // 自定义 JSON 配置，解决 Hutool 默认将 LocalDateTime 序列化为时间戳导致的解析异常问题
    private static final JSONConfig JSON_CONFIG = JSONConfig.create().setDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public Result queryById(Long id) {
        // 方案一：缓存穿透（已封装在 CacheClient 中）
        Shop shop = cacheClient.querywithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);

        // 方案二：互斥锁解决缓存击穿（本地实现）
        // Shop shop = queryWithMutex(id);

        // 方案三：逻辑过期解决缓存击穿（本地实现）
        // Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }

    @Override
    @Transactional 
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        // 1. 更新数据库
        updateById(shop);

        // 2. 删除缓存（保证双写一致性）
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);

        return Result.ok();
    }

    /**
     * 将店铺数据预热到 Redis，并设置逻辑过期时间
     */
    public void saveShop2Redis(Long id, Long expireSeconds) {
        // 1. 查询店铺数据
        Shop shop = getById(id);
        if (shop == null) {
            return;
        }

        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        
        // 3. 写入 redis (使用 JSON_CONFIG 格式化时间)
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData, JSON_CONFIG));
    }
}