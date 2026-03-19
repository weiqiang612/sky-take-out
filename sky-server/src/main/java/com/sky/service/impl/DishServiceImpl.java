package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.annotation.AutoFill;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.*;
import com.sky.enumeration.OperationType;
import com.sky.exception.CategoryNotExistException;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/13 16:44
 */

@Slf4j
@Service
public class DishServiceImpl implements DishService {
    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 新增菜品和对应口味
     *
     * @param dishDTO
     * @return
     */
    @Transactional
    @Override
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();

        BeanUtils.copyProperties(dishDTO, dish);
        // 创建人、创建时间、更新人、更新时间赋值已被AOP增强
        // 1. 向菜品表插入一条数据
        int rows = dishMapper.save(dish);

        if (rows != 1) {
            throw new RuntimeException("菜品插入失败！"); // 假设你有错误常量
        }

        // 2. 获取insert后返回的 ID
        Long dishId = dish.getId();

        // 如果商家将菜品的口味也填写了的话，需要将信息写到口味表，所以开启事务
        List<DishFlavor> flavors = dishDTO.getFlavors();
        int affectedFlavors = 0;
        if (flavors != null && !flavors.isEmpty()) {
            // 建立逻辑外键关联
            flavors.forEach(f -> f.setDishId(dishId));
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 菜品分页查询
     *
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult page(DishPageQueryDTO dishPageQueryDTO) {
        // 开启分页查询
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());

        Page<DishVO> dishPage = dishMapper.page(dishPageQueryDTO);
        return new PageResult(dishPage.getTotal(), dishPage.getResult());
    }

    /**
     * 批量删除菜品
     *
     * @param ids
     */
    @Transactional
    @Override
    public void deleteBatch(List<Long> ids) {
        /* 要求：
            1. 当该菜品处于在售状态，不允许删除
            2. 可以一次删除一个或多个菜品
            3. 删除菜品后，关联的口味数据也要删除
            4. 被套餐关联的菜品不能删除，需要先在套餐中移除菜品，才可删除
         */
        // 遍历集合，查询菜品状态和是否关联套餐表，逐一删除口味数据和菜品数据 否决

        // 使用SQL原生 in 操作
        // 一次性查询所有菜品状态，如果有起售状态，则不允许删除
        List<Integer> statuses = dishMapper.queryStatusesByIds(ids);
        for (Integer status : statuses) {
            if (status == StatusConstant.ENABLE) {
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        // 一次性查询所有菜品套餐关联状态，如果有关联，则不允许删除
        Integer relatedDishesCount = setmealDishMapper.queryRelatedDishes(ids);
        if (relatedDishesCount != 0) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }
        // 当初添加菜品时，口味非必填项，返回值可以为0
        dishFlavorMapper.deleteByIds(ids);
        int affectedRows = dishMapper.deleteByIds(ids);
        if (affectedRows != ids.size()) {
            throw new DeletionNotAllowedException("部分菜品删除失败，可能已被其他人处理");
        }
    }

    /**
     * 修改菜品
     *
     * @param dishDTO
     */
    @Transactional
    @Override
    public void updateWithFlavor(DishDTO dishDTO) {
        DishVO dishVO = dishMapper.getById(dishDTO.getId());
        boolean needUpdateSetmealDish = !dishVO.getName().equals(dishDTO.getName()) ||
                dishVO.getPrice().compareTo(dishDTO.getPrice()) != 0;
        // 1. 修改菜品表
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.update(dish);
        // 2. 修改菜品口味表（先删后增）
        dishFlavorMapper.deleteByIds(Collections.singletonList(dishDTO.getId()));
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            // 前端并没有传入菜品id数据，需要手动赋值
            flavors.forEach(f -> f.setDishId(dishDTO.getId()));
            dishFlavorMapper.insertBatch(dishDTO.getFlavors());
        }
        // 3. 按需修改套餐菜品关联表
        if (needUpdateSetmealDish) {
            setmealDishMapper.updateDishData(dishDTO.getName(), dishDTO.getPrice(), dishDTO.getId());
        }
    }

    /**
     * 根据菜品ID查询菜品
     *
     * @param id
     * @return
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        // 1. 查询除菜品口味之外的其他数据
        DishVO dishVO = dishMapper.getById(id);
        // 2. 查询菜品口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.getById(id);
        // 3. 将结果封装为DishVO对象返回
        dishVO.setFlavors(dishFlavors);
        return dishVO;
    }

    /**
     * 条件查询菜品和口味
     *
     * @param dish
     * @return
     */
    @Override
    public List<DishVO> listWithFlavors(Dish dish) {
        List<DishVO> dishVOList;
        dishVOList = dishMapper.list(dish);

        // 遍历查询到的菜品，为对应菜品的口味赋值
        for (DishVO dishVO : dishVOList) {
            Long id = dishVO.getId();
            dishVO.setFlavors(dishFlavorMapper.getById(id));
        }
        return dishVOList;
    }

    /**
     * 菜品起售停售功能
     *
     * @param status
     * @param id
     */
    @Override
    public void updateStatus(Integer status, Long id) {
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        dishMapper.update(dish);
        // 停售情况下，如果菜品关联了套餐表，则将套餐也停售
        if (status == StatusConstant.DISABLE) {
            ArrayList<Long> dishIds = new ArrayList<>();
            dishIds.add(id);
            List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(dishIds);
            if (setmealIds != null && setmealIds.size() > 0) {
                for (Long setmealId : setmealIds) {
                    Setmeal setmeal = Setmeal.builder()
                            .id(setmealId)
                            .status(StatusConstant.DISABLE)
                            .build();
                    setmealMapper.update(setmeal);
                }
            }
        }


    }

    /**
     * 根据分类Id查询菜品
     * @param categoryId
     * @return
     */
    @Override
    public List<Dish> listByCategoryId(Long categoryId) {
        return dishMapper.listByCategoryId(categoryId);
    }
}
