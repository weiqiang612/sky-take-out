package com.sky.mapper;

import com.sky.entity.SetmealDish;
import com.sky.vo.DishItemVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/14 14:28
 */

@Mapper
public interface SetmealDishMapper {

    /**
     * 查询多条菜品的关联数量
     * @param ids 菜品ID
     * @return 该菜品关联的套餐的数量
     */
    Integer queryRelatedDishes(List<Long> ids);

    /**
     * 按需修改套餐中菜品数据
     * @param name
     * @param price
     * @Param dishId
     */
    void updateDishData(@Param("name") String name,@Param("price") BigDecimal price,@Param("dishId") Long dishId);

    /**
     * 根据套餐ID查询相关菜品
     * @param setmealId
     * @return
     */
    List<DishItemVO> listSetmealDish(Long setmealId);

    /**
     * 根据菜品ID查询相关套餐
     * @param dishIds
     * @return
     */
    List<Long> getSetmealIdsByDishIds(ArrayList<Long> dishIds);

    /**
     * 将套餐对应的菜品插入套餐菜品对应表
     * @param setmealDishes
     */
    void saveSetmealDishes(List<SetmealDish> setmealDishes);

    /**
     * 根据套餐ID批量删除套餐菜品映射关系
     * @param ids
     */
    void deleteSetmealDishesBySetmealIds(List<Long> ids);

    /**
     * 根据套餐ID查询菜品相关信息，此方法与上面类似方法的区别是，返回值类型不同
     * @param id
     * @return
     */
    List<SetmealDish> listDetailed(Long id);
}
