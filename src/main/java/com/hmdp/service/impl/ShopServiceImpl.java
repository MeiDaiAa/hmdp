package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result getShopInfoById(Long id) {
        // 1. 查看缓存中是否有商铺信息
        String s = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2.  缓存中有，返回
        if (s != null) {
            Shop bean = JSONUtil.toBean(s, Shop.class);
            return Result.ok(bean);
        }
        // 3. 缓存中没有，查询数据库
        Shop byId = getById(id);
        // 4. 数据库中没有，返回错误
        if (byId == null) {
            return Result.fail("店铺不存在");
        }
        // 5. 数据库中有，写入缓存
        redisTemplate.opsForValue()
                .set(CACHE_SHOP_KEY + id,
                        JSONUtil.toJsonStr(byId),
                        CACHE_SHOP_TTL,
                        TimeUnit.MINUTES
                );
        // 6. 返回
        return Result.ok(byId);
    }

    @Override
    public Result update(Shop shop) {
        // 0. 判空
        if (shop.getId() == null) {
            return Result.fail("店铺id不能为空");
        }

        // 1. 更新数据库
        updateById(shop);

        // 2. 删除缓存
        redisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }
}
