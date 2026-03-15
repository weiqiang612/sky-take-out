package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.exception.ShopStatusInvalid;
import com.sky.service.ShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/15 18:21
 */

@Service
public class ShopServiceImpl implements ShopService {

    public static final String KEY = "SHOP_STATUS";


    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 查询店铺营业状态 1营业 0打烊
     *
     * @return
     */
    @Override
    public Integer queryStatus() {

        // 缓存穿透 缓存击穿 缓存雪崩
        // 这里设置成永久的，没有这些问题
        Integer shopStatus = (Integer) redisTemplate.opsForValue().get(KEY);
        if (shopStatus == null) {
            redisTemplate.opsForValue().set(KEY, StatusConstant.DISABLE);
            return StatusConstant.DISABLE;
        }
        return shopStatus;
    }

    @Override
    public void updateStatus(Integer status) {
        if (!status.equals(StatusConstant.ENABLE) && !status.equals(StatusConstant.DISABLE)) {
            throw new ShopStatusInvalid("商店营业状态参数非法！");
        }
        redisTemplate.opsForValue().set(KEY, status);
    }
}
