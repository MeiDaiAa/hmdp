package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import static com.hmdp.utils.RedisConstants.LOCK_PREFIX;

/**
 * 简单分布式锁
 * @author meidaia
 */
public class SimpleRedisLock {
    private final StringRedisTemplate redisTemplate;
    private static final String VALUE_PREFIX = UUID.randomUUID().toString(true);

    public SimpleRedisLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
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
     * 释放锁 1 成功 0 失败
     *
     * @param name 锁名称
     * @return 释放锁结果
     */
    public Long unLock(String name) {
        String key = LOCK_PREFIX + name;
        String value = VALUE_PREFIX + "-" + Thread.currentThread().getId();

        return redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), value);
    }
}
