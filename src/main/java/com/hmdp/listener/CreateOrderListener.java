package com.hmdp.listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.hmdp.utils.MQConstants.*;

/**
 * 创建订单 监听器
 * @author meidaia
 */
@Slf4j
@Component
public class CreateOrderListener {
    @Autowired
    private IVoucherOrderService voucherOrderService;
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(name = ORDER_CREATE_QUEUE, durable = "true"),
        exchange = @Exchange(name = ORDER_CREATE_EXCHANGE),
            key = ORDER_CREATE_KEY))
    public void createOrder(String msg) {
        VoucherOrder order = JSONUtil.toBean(msg, VoucherOrder.class);
        // 创建订单
        CreateOrderListener proxy = (CreateOrderListener)AopContext.currentProxy();
        proxy.createOrder(order);
        log.info("创建用户 {} 成功，订单：{}", order.getUserId(), order.getId());
    }

    /**
     * 创建订单, 一人一单
     * @param voucherOrder 订单对象
     */
    @Transactional
    public void createOrder(VoucherOrder voucherOrder) {
        // 一人一单，获取用户id
        Integer count = voucherOrderService.query()
                .eq("user_id", voucherOrder.getUserId())
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();
        if (count > 0) {
            log.error("用户 {} 创建订单失败，该用户已经购买过该优惠券", voucherOrder.getUserId());
            return;
        }

        // 4. 扣减库存
        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!update) {
            log.error("用户 {} 创建订单失败，库存不足", voucherOrder.getUserId());
            return;
        }
        // 5. 保存订单
        voucherOrderService.save(voucherOrder);
    }
}
