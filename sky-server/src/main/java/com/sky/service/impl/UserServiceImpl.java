package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/17 14:23
 */

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WeChatProperties weChatProperties;

    // 微信登录地址常量
    public static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";

    /**
     * 调取微信登录接口，获取openid
     * @param code
     * @return
     */
    private String getOpenid(String code) {
        // 1. 携带code向微信登录接口发送get请求
        HashMap<String, String> paramMap = new HashMap<String, String>() {{
            put("appid", weChatProperties.getAppid());
            put("secret", weChatProperties.getSecret());
            put("js_code", code);
            put("grant_type", "authorization_code");
        }};
        String s = HttpClientUtil.doGet(WX_LOGIN, paramMap);
        // 2. 获取响应参数 openid
        JSONObject jsonObject = JSON.parseObject(s);
        return jsonObject.getString("openid");
    }

    /**
     * 用户登录
     *
     * @param userLoginDTO
     * @return
     */
    @Transactional
    @Override
    public User login(UserLoginDTO userLoginDTO) {
        // 调用类内私有方法，获取openid，并检验其合法性
        String openid = getOpenid(userLoginDTO.getCode());
        if (openid == null && "".equals(openid)) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        // 3. 查数据库有没有该用户
        User user = userMapper.getByOpenid(openid);
        // 3.2 如果没有，则创建该用户，交给Controller层生成token返回
        if (user == null) {
            // 后续信息可以通过个人中心去完善
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            // 注意：方法内执行了多条SQL语句，需要用到事务
            userMapper.insert(user);
        }
        // 3.1 如果有，交给Controller层生成token返回
        return user;
    }

}
