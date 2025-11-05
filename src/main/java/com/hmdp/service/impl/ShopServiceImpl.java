package com.hmdp.service.impl;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.*;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedisClient redisClient;

    @Override
    public Result getShopInfoById(Long id) {
        // 0. 判空
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

//        Shop shop = queryWithPassThrough(id);
//        Shop shop = queryWithMutex(id);
//        Shop shop = queryWithLogicalExpire(id);

/*        Shop shop = redisClient.queryWithPassThrough(
                CACHE_SHOP_KEY + id,
                Shop.class,
                id,
                this::getById,
                CACHE_SHOP_TTL + RandomUtil.randomLong(1, 5),
                TimeUnit.MINUTES
        );*/

        Shop shop = redisClient.queryWithMutex(
                CACHE_SHOP_KEY + id,
                LOCK_SHOP_KEY + id,
                Shop.class,
                id,
                this::getById,
                CACHE_SHOP_TTL + RandomUtil.randomLong(1, 5),
                TimeUnit.MINUTES
                );

/*        Shop shop = redisClient.queryWithLogicalExpire(
                CACHE_SHOP_KEY + id,
                LOCK_SHOP_KEY + id,
                Shop.class,
                id,
                this::getById,
                CACHE_SHOP_TTL + RandomUtil.randomLong(1, 5),
                TimeUnit.MINUTES
        );*/
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 查询数据，防止缓存穿透
     *
     * @param id 商铺id
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 查看缓存中是否有商铺信息
        String s = redisTemplate.opsForValue().get(key);
        // 2.  缓存中有，返回
        if (s != null) {
            // 缓存中为空，返回空
            if (StrUtil.isBlank(s)) {
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
     *
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
                    if (StrUtil.isBlank(s)) {
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
     *
     * @param key 锁的key
     * @return 获取锁成功返回true，获取锁失败返回false
     */
    private boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key 锁的key
     */
    private void unLock(String key) {
        redisTemplate.delete(key);
    }

    // 线程池
//    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            // 核心线程数
            4,
            // 最大线程数
            8,
            // 空闲线程存活时间
            60L,
            TimeUnit.SECONDS,
            // 队列长度，防止任务无限积压
            new ArrayBlockingQueue<>(100),
            // 拒绝策略：任务满直接丢弃
            new ThreadPoolExecutor.DiscardPolicy()
    );

    /**
     * 查询数据，使用逻辑过期解决缓存击穿
     *
     * @param id 商铺id
     * @return 商铺信息
     */
    private Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 查看缓存中是否有商铺信息
        String s = redisTemplate.opsForValue().get(key);
        // 2. 缓存中没有，直接返回空
        if (StrUtil.isBlank(s)) {
            return null;
        }
        // 3. 缓存中有，查看是否过期
        RedisData redisData = JSONUtil.toBean(s, RedisData.class);
        if (LocalDateTime.now().isAfter(redisData.getExpireTime())) {
            // 4. 过期，创建新线程，更新数据库，
            EXECUTOR.submit(() -> refreshCacheWithLogicalExpire(id));
        }
        // 5. 返回旧数据
        return JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
    }

    /**
     * 更新商铺信息，使用逻辑过期解决缓存击穿
     *
     * @param id 商铺id
     */
    private void refreshCacheWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        boolean isLock = false;

        try {
            // 1. 获取锁
            isLock = tryLock(LOCK_SHOP_KEY + id);
            // 2. 获取锁失败，返回
            if (!isLock) {
                return;
            }
            // 3. 查询redis中商铺信息是否过期
            String s = redisTemplate.opsForValue().get(key);
            RedisData bean = JSONUtil.toBean(s, RedisData.class);
            if (LocalDateTime.now().isBefore(bean.getExpireTime())) {
                // 4. 未过期，不用更新缓存，退出方法
                return;
            }

            // 获取锁成功，更新数据库
            // 5. 查询数据库
            Shop byId = null;
            try {
                byId = getById(id);
            } catch (Exception e) {
                log.error("缓存重建失败 id={}", id, e);
            }
            // 6. 为空，表示数据库中不存在，直接删除缓存
            if (byId == null) {
                redisTemplate.delete(key);
                return;
            }
            // 7. 存在，写入缓存
            redisTemplate.opsForValue()
                    .set(key,
                            JSONUtil.toJsonStr(new RedisData<>(LocalDateTime.now().plusSeconds(TimeUnit.MINUTES.toSeconds(CACHE_SHOP_TTL)), byId)));
        } finally {
            // 8. 释放锁
            if (isLock) {
                unLock(LOCK_SHOP_KEY + id);
            }
        }
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
