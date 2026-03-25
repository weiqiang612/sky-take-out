package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
     * 根据用户表主键id查询用户
     * @param userId
     * @return
     */
    @Select("select * from user where id = #{userId}")
    User getById(Long userId);

    /**
     * 条件查询某一时间段的用户总量
     * @param begin
     * @param end
     * @return
     */
    Integer countUser(LocalDateTime begin, LocalDateTime end);
}
