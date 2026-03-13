package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.entity.Dish;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/13 16:43
 */

public interface DishService {
    /**
     * 添加菜品和对应口味
     * @param dishDTO
     * @return
     */
    void saveWithFlavor(DishDTO dishDTO);
}
