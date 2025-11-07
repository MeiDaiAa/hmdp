package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisClient;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisClient redisClient;


    /**
     * 秒杀优惠券
     * @param voucherId 优惠券id
     * @return 订单id
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
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

        // 4. 获取代理对象，使用代理对象调用创建订单
        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 5. 创建订单
        synchronized (UserHolder.getUser().getId().toString().intern()) {
            return proxy.createOrder(voucherId);
        }
    }

    /**
     * 创建订单, 一人一单
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
