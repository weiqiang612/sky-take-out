package com.sky.service;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/15 18:20
 */

public interface ShopService {
    /**
     * 查询店铺营业状态 1营业 0打烊
     * @return
     */
    Integer queryStatus();

    /**
     * 设置店铺营业状态
     * @param status
     */
    void updateStatus(Integer status);
}
