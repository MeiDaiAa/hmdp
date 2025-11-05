package com.hmdp.utils;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class RedisClient {
    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 设置缓存
     * @param key 缓存的key
     * @param data 缓存的数据
     */
    public void set(String key, Object data) {
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data));
    }

    /**
     * 设置缓存并设置过期时间
     * @param key 缓存的key
     * @param data 缓存的数据
     * @param time 过期时间
     * @param timeUnit 时间单位
     */
    public void setWithExpirce(String key, Object data, long time, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data), time, timeUnit);
    }

    /**
     * 设置逻辑过期缓存
     * @param key 缓存的key
     * @param data 缓存的数据
     * @param time 过期时间
     * @param timeUnit 时间单位
     */
    public void setWithLogicExpire(String key, Object data, long time, TimeUnit timeUnit) {
        RedisData<Object> redisData = new RedisData<>(
                LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)),
                data
        );
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 查询缓存
     * @param key 缓存的key
     * @param type 数据类型
     * @param id 数据id
     * @param dbFallback 数据库查询方法
     * @param time 过期时间
     * @param timeUnit 时间单位
     * @return  数据
     * @param <T> 数据类型
     * @param <ID> 数据id类型
     */
    public <T, ID> T queryWithPassThrough(String key, Class<T> type, ID id, Function<ID, T> dbFallback, Long time, TimeUnit timeUnit) {
        // 1. 查看缓存中是否有商铺信息
        String s = redisTemplate.opsForValue().get(key);
        // 2.  缓存中有，返回
        if (s != null) {
            // 缓存中为空，返回空
            if (StrUtil.isBlank(s)) {
                return null;
            }
            // 缓存中有，返回
            return JSONUtil.toBean(s, type);
        }
        // 3. 缓存中没有，查询数据库
        T byId = dbFallback.apply(id);
        // 4. 数据库中没有，返回错误 (update: 数据库中没有，将空值写入缓存(有效时间默认120Sec)，返回空)
        if (byId == null) {
            // 将空值写入缓存
            redisTemplate.opsForValue()
                    .set(key, "", CACHE_NULL_TTL + RandomUtil.randomLong(20, 50), TimeUnit.SECONDS);
            return null;
        }
        // 5. 数据库中有，写入缓存
        redisTemplate.opsForValue()
                .set(key, JSONUtil.toJsonStr(byId), time, timeUnit);
        // 6. 返回
        return byId;
    }

    /**
     * 基于互斥锁解决缓存击穿问题
     * @param key 缓存的key
     * @param lockKey 锁的key
     * @param type 数据类型
     * @param id 数据id
     * @param dbFallback 数据库查询方法
     * @param time 过期时间
     * @param timeUnit 时间单位
     * @return  数据
     * @param <T>   数据类型
     * @param <ID>   数据id类型
     */
    public <T, ID> T queryWithMutex(String key, String lockKey, Class<T> type, ID id, Function<ID, T> dbFallback, Long time, TimeUnit timeUnit) {
        boolean isLock = false;
        T byId = null;
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
                    return JSONUtil.toBean(s, type);
                }
                // 3. 缓存中没有，尝试获取锁，然后查询数据库
                isLock = tryLock(lockKey);
                if (isLock) {
                    // 获取锁成功，退出循环
                    break;
                }
                // 获取锁失败，休眠一段时间，重新查询获取
                ThreadUtil.sleep(RandomUtil.randomLong(30, 50));
            }
            // 获取到了锁，查询数据库写入缓存
            byId = dbFallback.apply(id);
            // 4. 数据库中没有，将空值写入缓存(有效时间120Sec)，返回空
            if (byId == null) {
                // 将空值写入缓存
                redisTemplate.opsForValue()
                        .set(key, "", CACHE_NULL_TTL + RandomUtil.randomLong(20, 50), TimeUnit.SECONDS);
                return null;
            }
            // 5. 数据库中有，写入缓存
            redisTemplate.opsForValue()
                    .set(key, JSONUtil.toJsonStr(byId), time, timeUnit);
        } finally {
            // 7. 如果获取到了锁，释放锁
            if (isLock) {
                unLock(lockKey);
            }
        }
        // 6. 返回
        return byId;
    }
    // 线程池
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

    public <T, ID> T queryWithLogicalExpire(String key, String lockKey, Class<T> type, ID id, Function<ID, T> dbFallback, Long time, TimeUnit timeUnit) {
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
            EXECUTOR.submit(() -> refreshCacheWithLogicalExpire(key, lockKey, type, id, dbFallback, time, timeUnit));
        }
        // 5. 返回旧数据
        return JSONUtil.toBean((JSONObject) redisData.getData(), type);
    }

    /**
     * 更新商铺信息，使用逻辑过期解决缓存击穿
     *
     * @param id 商铺id
     */
    private<T, ID> void refreshCacheWithLogicalExpire(String key, String lockKey, Class<T> type, ID id, Function<ID, T> dbFallback, Long time, TimeUnit timeUnit) {
        boolean isLock = false;

        try {
            // 1. 获取锁
            isLock = tryLock(lockKey);
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
            T byId = null;
            try {
                byId = dbFallback.apply(id);
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
                            JSONUtil.toJsonStr(new RedisData<>(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)), byId)));
        } finally {
            // 8. 释放锁
            if (isLock) {
                unLock(lockKey);
            }
        }
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

}
