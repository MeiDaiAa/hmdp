package com.hmdp.service.impl;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
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

import java.util.Random;
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

//        Shop shop = queryWithPassThrough(id);
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 查询数据，防止缓存穿透
     * @param id 商铺id
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 查看缓存中是否有商铺信息
        String s = redisTemplate.opsForValue().get(key);
        // 2.  缓存中有，返回
        if (s != null) {
            // 缓存中为空，返回空
            if(StrUtil.isBlank(s)) {
                return null;
            }
            // 缓存中有，返回
            return JSONUtil.toBean(s, Shop.class);
        }
        // 3. 缓存中没有，查询数据库
        Shop byId = getById(id);
        // 4. 数据库中没有，返回错误 (update: 数据库中没有，将空值写入缓存(有效时间2Min)，返回空)
        if (byId == null) {
            // 将空值写入缓存
            redisTemplate.opsForValue()
                    .set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 5. 数据库中有，写入缓存
        redisTemplate.opsForValue()
                .set(key, JSONUtil.toJsonStr(byId), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 6. 返回
        return byId;
    }

    /**
     * 查询数据，redis中没有数据，查询数据库时添加锁，防止缓存击穿
     * @param id 商铺id
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;

        Shop byId = null;
        boolean isLock = false;
        try {
            while (true) {
                // 1. 查看缓存中是否有商铺信息
                String s = redisTemplate.opsForValue().get(key);
                // 2.  缓存中有，返回
                if (s != null) {
                    // 缓存中为空，返回空
                    if(StrUtil.isBlank(s)) {
                        return null;
                    }
                    // 缓存中有，返回
                    return JSONUtil.toBean(s, Shop.class);
                }
                // 3. 缓存中没有，尝试获取锁，然后查询数据库
                isLock = tryLock(LOCK_SHOP_KEY + id);
                if (isLock) {
                    // 获取锁成功，退出循环
                    break;
                }
                // 获取锁失败，休眠一段时间，重新查询获取
                ThreadUtil.sleep(RandomUtil.randomLong(30, 50));
            }
            // 获取到了锁，查询数据库写入缓存
            byId = getById(id);
            // 4. 数据库中没有，将空值写入缓存(有效时间2Min)，返回空
            if (byId == null) {
                // 将空值写入缓存
                redisTemplate.opsForValue()
                        .set(key, "", CACHE_NULL_TTL + RandomUtil.randomLong(1, 5), TimeUnit.MINUTES);
                return null;
            }
            // 5. 数据库中有，写入缓存
            redisTemplate.opsForValue()
                    .set(key, JSONUtil.toJsonStr(byId), CACHE_SHOP_TTL + RandomUtil.randomLong(1, 5), TimeUnit.MINUTES);
        } finally {
            // 7. 如果获取到了锁，释放锁
            if (isLock) {
                unLock(LOCK_SHOP_KEY + id);
            }
        }
        // 6. 返回
        return byId;
    }

    /**
     * 尝试获取锁
     * @param key 锁的key
     * @return 获取锁成功返回true，获取锁失败返回false
     */
    private boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key 锁的key
     */
    private void unLock(String key) {
        redisTemplate.delete(key);
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
