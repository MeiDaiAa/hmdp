package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisClient;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.PathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisClient redisClient;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    SimpleRedisLock simpleRedisLock;

    // 加载秒杀优惠券脚本
    private static final DefaultRedisScript<Long> seckillScript;
    static {
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setResultType(Long.class);
        seckillScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("seckill.lua")));
    }

    @PostConstruct
    public void init() {
        simpleRedisLock = new SimpleRedisLock(redisTemplate);
    }

    /**
     * 秒杀优惠券
     *
     * @param voucherId 优惠券id
     * @return 订单id
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 通过redis查询用户是否可以购买
        Long execute = redisTemplate.execute(seckillScript,
                Collections.emptyList(),
                voucherId.toString(),
                UserHolder.getUser().getId().toString()
        );
        if (execute != 0) {
            return execute == 1 ? Result.fail("库存不足") : Result.fail("请勿重复下单");
        }

        long orderId = redisClient.getUniqueId("voucherOrder");
        // TODO 发送消息到MQ
        log.info("订单生成成功：{}, 用户：{}", orderId, UserHolder.getUser().getId());

        return Result.ok(orderId);
    }

    public Result seckillVoucher2(Long voucherId) {
        // 秒杀优惠券
        // 1. 查询优惠卷信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        // 2. 查看时间是否过期
        if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
            return Result.fail("优惠券尚未开始");
        }
        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
            return Result.fail("优惠券已过期");
        }
        // 3. 查看库存是否充足
        Integer stock = seckillVoucher.getStock();
        if (stock <= 0) {
            return Result.fail("优惠券已售罄");
        }

/*        // 4. 获取代理对象，使用代理对象调用创建订单
        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 5. 创建订单
        synchronized (UserHolder.getUser().getId().toString().intern()) {
            return proxy.createOrder(voucherId);
        }*/

        // 4. 尝试获取锁
//        boolean ret = simpleRedisLock.tryLock(2000000L, "voucher");

        // 4. 通过redisson获取锁
        RLock rLock = redissonClient.getLock("lock:vocher:" + UserHolder.getUser().getId());
        boolean ret = rLock.tryLock();
        if (!ret) {
            return Result.fail("请勿重复下单");
        }
        try {
            // 5. 获取代理对象，使用代理对象调用创建订单
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            // 6. 创建订单
            return proxy.createOrder(voucherId);
        } finally {
            // 7. 释放锁
//            long tryUnlock = simpleRedisLock.unLock("voucher");
            rLock.unlock();
//            if (tryUnlock != 1) {
//                log.error("释放锁失败");
//            }
        }

    }

    /**
     * 创建订单, 一人一单
     *
     * @param voucherId 优惠券id
     * @return 订单id
     */
    @Override
    @Transactional
    public Result createOrder(Long voucherId) {
        // 一人一单，获取用户id
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).count();
        if (count > 0) {
            return Result.fail("用户已购买");
        }

        // 4. 扣减库存
        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!update) {
            return Result.fail("优惠券已售罄");
        }
        // 5. 创建订单
        long order = redisClient.getUniqueId("voucherOrder");
        VoucherOrder voucherOrder = new VoucherOrder()
                .setId(order)
                .setUserId(UserHolder.getUser().getId())
                .setVoucherId(voucherId);
        // 6. 保存订单
        save(voucherOrder);

        return Result.ok(order);
    }
}
