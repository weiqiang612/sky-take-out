package com.sky.service;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.result.PageResult;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
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

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    PageResult page(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 新增套餐和套餐对应菜品
     * @param setmealDTO
     */
    void saveWithSetmealDishes(SetmealDTO setmealDTO);

    /**
     * 修改套餐，并修改套餐菜品映射表
     * @param setmealDTO
     */
    void updateWithDishes(SetmealDTO setmealDTO);

    /**
     * 根据套餐ID查询套餐
     * @param id
     * @return
     */
    SetmealVO getSetmealById(Long id);

    /**
     * 修改套餐起售停售状态
     * @param status
     * @param id
     */
    void updateStatus(Integer status, Long id);

    /**
     * 批量删除套餐
     * @Param ids
     */
    void removeBatch(List<Long> ids);
}
