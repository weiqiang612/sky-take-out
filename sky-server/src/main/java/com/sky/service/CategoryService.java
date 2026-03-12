package com.sky.service;

import com.sky.dto.CategoryPageQueryDTO;
import com.sky.result.PageResult;
import org.springframework.stereotype.Service;

/**
 * @author 袁志刚
 * @version 1.0
 * @Date 2026/3/12 17:14
 */

public interface CategoryService {
    /**
     * 分类分页查询
     * @param categoryPageQueryDTO
     * @return
     */
    PageResult page(CategoryPageQueryDTO categoryPageQueryDTO);
}
