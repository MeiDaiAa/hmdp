package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryTypeList() {
        // 1. 查询缓存中的商铺信息
        List<String> range = redisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);

        if (range != null && !range.isEmpty()) {
            // 2. 缓存中有，返回
            return Result.ok(range.stream()
                    .map(item -> JSONUtil.toBean(item, ShopType.class))
                    .collect(Collectors.toList())
            );
        }

        // 3. 缓存中没有，查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        // 4. 数据库中没有，返回错误
        if (shopTypes.isEmpty()) {
            return Result.fail("店铺类型不存在");
        }
        // 5. 写入缓存
        redisTemplate.opsForList()
                .rightPushAll(CACHE_SHOP_TYPE_KEY,
                        shopTypes.stream()
                                .map(JSONUtil::toJsonStr)
                                .collect(Collectors.toList())
                );
        redisTemplate.expire(CACHE_SHOP_TYPE_KEY, CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

        return Result.ok(shopTypes);
    }
}
