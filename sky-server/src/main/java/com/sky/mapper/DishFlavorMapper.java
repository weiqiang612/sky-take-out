package com.sky.mapper;

import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/13 17:14
 */

@Mapper
public interface DishFlavorMapper {

    /**
     * 批处理插入口味
     * @param flavors
     * @return
     */
    int insertBatch(List<DishFlavor> flavors);

    /**
     * 批量删除口味
     * @param ids
     */
    void deleteByIds(List<Long> ids);
}
