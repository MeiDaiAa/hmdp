package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static com.hmdp.utils.RedisConstants.LOCK_PREFIX;

public class SimpleRedisLock {
    private StringRedisTemplate redisTemplate;
    private static final String VALUE_PREFIX = UUID.randomUUID().toString();

    public SimpleRedisLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     *  尝试获取锁
     * @param millis 锁的过期时间
     * @param name 锁名称
     * @return 获取锁成功返回true，获取锁失败返回false
     */
    public boolean tryLock(Long millis, String name) {
        String key = LOCK_PREFIX + name;
        String value = VALUE_PREFIX + "-" + Thread.currentThread().getId();

        Boolean ret = redisTemplate.opsForValue().setIfAbsent(key, value, millis, TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(ret);
    }

    /**
     * 释放锁 0 成功, 1 锁不存在, 2 锁不属于当前线程, 3 释放锁失败
     * @param name
     * @return
     */
    public int unLock(String name) {
        String key = LOCK_PREFIX + name;
        String value = VALUE_PREFIX + "-" + Thread.currentThread().getId();

        String currentValue = redisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(currentValue)) {
            return 1;
        }

        if (!currentValue.equals(value)) {
            return 2;
        }
        return redisTemplate.delete(key) ? 0 : 3;
    }


}
