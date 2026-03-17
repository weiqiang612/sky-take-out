package com.sky.service;

import com.sky.entity.Setmeal;
import com.sky.vo.DishItemVO;
import io.swagger.models.auth.In;

import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/17 18:10
 */


public interface SetmealService {
    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    List<Setmeal> list(Setmeal setmeal);

    /**
     * 根据套餐ID查询相关菜品
     * @param id
     * @return
     */
    List<DishItemVO> listDishItem(Long id);
}
