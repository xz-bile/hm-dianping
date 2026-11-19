package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

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
    String key="cache:shop:";
    //1,从redis查缓存
    String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);

    //2,判断是否存在
    if(StrUtil.isNotBlank(shopJson)){
      Shop shop=JSONUtil.toBean(shopJson, Shop.class);
      return Result.ok(shop);
    }

    //3,判断是否为空值 （null，空字符串）
    if(shopJson!=null){ 
      return Result.fail("店铺不存在");
    }
    
    //4,不存在，查数据库
    Shop shop = getById(id);


    //5，不存在，返回错误
    if(shop==null){
      //将空值写入redis
      stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);

      return Result.fail("店铺不存在");
    }

    //6,存在，存入redis，返回
    stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);

    return Result.ok(shop);
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

}
