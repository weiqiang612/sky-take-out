package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     * 新增菜品
     * @param dish
     * @return
     */
    @AutoFill(OperationType.INSERT)
    int save(Dish dish);

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    Page<DishVO> page(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 批量查询菜品状态
     * @param ids
     * @return
     */
    List<Integer> queryStatusesByIds(List<Long> ids);

    /**
     * 批量删除菜品
     * @param ids
     * @return
     */
    int deleteByIds(List<Long> ids);

    /**
     * 查询返回除口味之外的数据
     * @param id
     * @return
     */
    DishVO getById(Long id);

    /**
     * 更新菜品表
     * @param dish
     */
    @AutoFill(OperationType.UPDATE)
    void update(Dish dish);

    /**
     * 条件查询菜品
     * @param dish
     * @return
     */
    List<DishVO> list(Dish dish);

    /**
     * 根据分类ID查询菜品
     * @param categoryId
     * @return
     */
    List<Dish> listByCategoryId(Long categoryId);
}
