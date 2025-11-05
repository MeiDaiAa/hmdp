package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 通过id查询商铺信息，添加缓存
     * @param id 商铺id
     */
    Result getShopInfoById(Long id);

    /**
     * 更新商铺信息, 同时删除缓存
     *
     * @param shop 商铺数据
     * @return
     */
    Result update(Shop shop);
}
