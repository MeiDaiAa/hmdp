package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
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

import static com.hmdp.utils.RedisConstants.*;

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
        // 0. 判空
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        String key = CACHE_SHOP_KEY + id;
        // 1. 查看缓存中是否有商铺信息
        String s = redisTemplate.opsForValue().get(key);
        // 2.  缓存中有，返回
        if (s != null) {
            // 缓存中为空，返回错误
            if(StrUtil.isBlank(s)) {
                return Result.fail("店铺不存在");
            }
            // 缓存中有，返回
            Shop bean = JSONUtil.toBean(s, Shop.class);
            return Result.ok(bean);
        }
        // 3. 缓存中没有，查询数据库
        Shop byId = getById(id);
        // 4. 数据库中没有，返回错误 (update: 数据库中没有，将空值写入缓存(有效时间2Min)，返回错误)
        if (byId == null) {
            // 将空值写入缓存
            redisTemplate.opsForValue()
                    .set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        // 5. 数据库中有，写入缓存
        redisTemplate.opsForValue()
                .set(key, JSONUtil.toJsonStr(byId), CACHE_SHOP_TTL, TimeUnit.MINUTES);
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
