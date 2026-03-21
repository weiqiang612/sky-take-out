package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/17 15:10
 */

@Mapper
public interface UserMapper {

    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    /**
     * 插入新用户，需要主键回填，所以写xml
     * @param user
     */
    void insert(@Param("user") User user);

    /**
     * 根据用户ID查询用户
     * @param userId
     * @return
     */
    @Select("select * from user where openid = #{userId}")
    User getById(Long userId);
}
