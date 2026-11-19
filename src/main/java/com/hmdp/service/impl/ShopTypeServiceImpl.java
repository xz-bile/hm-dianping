package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 🚨 建议把方法名改掉，避免和 MyBatis-Plus 的 list() 冲突
    @Override
    public List<ShopType> queryTypelist() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY; //在 RedisConstants 中，如 "cache:shop:type"

        // 1. 查询 Redis 中是否存在缓存 (获取 List<String>)
        List<String> jsonList = stringRedisTemplate.opsForList().range(key, 0, -1);

        // 2. 如果存在，将 JSON 字符串反序列化为对象列表并直接返回
        if (jsonList != null && !jsonList.isEmpty()) {
            // Hutool 的 JSONUtil 可以直接将 List<String> 转为 List<对象>
            return JSONUtil.toList(jsonList.toString(), ShopType.class);
        }

        // 3. 如果不存在，查询数据库（必须按 sort 字段升序排列）
        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        // 4. 如果数据库中也没有，直接返回空列表
        if (shopTypes == null || shopTypes.isEmpty()) {
            return new ArrayList<>();
        }

        // 5. 存在，将对象列表转换为 JSON 字符串列表
        List<String> shopTypeJsonList = shopTypes.stream()
                .map(JSONUtil::toJsonStr) // 把每个 ShopType 转成 JSON 字符串
                .collect(Collectors.toList());

        // 6. 将 JSON 字符串列表存入 Redis List
        // 🚨 注意这里用 rightPushAll，保证按照我们在数据库查出来的顺序存入（左进右出，或者右进左出保持顺序一致）
        stringRedisTemplate.opsForList().rightPushAll(key, shopTypeJsonList);

        // 7. 设置缓存过期时间（例如 30 分钟），防止缓存长期堆积
        stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

        // 8. 返回数据
        return shopTypes;
    }
}