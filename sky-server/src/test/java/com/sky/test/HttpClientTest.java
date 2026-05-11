package com.sky.test;

import com.google.gson.JsonObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/15 20:31
 */

//@SpringBootTest
public class HttpClientTest {
    /**
     * 测试通过HttpClient发送GET方式请求
     */
    @Test
    public void testGET() throws IOException {
        // 1. 实例化客户端对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
        // 2. 构建请求
        HttpGet httpGet = new HttpGet("http://localhost:8080/user/shop/status");
        // 3. 发送请求
        CloseableHttpResponse response = httpClient.execute(httpGet);
        // 4. 接收响应
        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("响应的状态码是：" + statusCode);

        HttpEntity entity = response.getEntity();
        String string = EntityUtils.toString(entity, "utf-8");
        System.out.println("响应体为：\n" + string);

        // 5. 释放资源
        response.close();
        httpClient.close();
    }

    /**
     * 测试通过HttpClient发送POST方式请求
     */
    @Test
    public void testPOST() throws IOException, JSONException {
        // 1. 构建客户端对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
        // 2. 构建请求
        HttpPost httpPost = new HttpPost("http://localhost:8080/admin/employee/login");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("username","admin");
        jsonObject.put("password","123456");
        StringEntity stringEntity = new StringEntity(jsonObject.toString());

        // 指定请求编码方式
        stringEntity.setContentType("application/json");
        stringEntity.setContentEncoding("UTF-8");
        httpPost.setEntity(stringEntity);

        // 3. 发送请求
        CloseableHttpResponse response = httpClient.execute(httpPost);
        // 4. 接收并处理响应
        System.out.println("响应码为：" + response.getStatusLine().getStatusCode());
        System.out.println("响应体为: " + EntityUtils.toString(response.getEntity()));
        // 5. 释放资源
        response.close();
        httpClient.close();
    }
}
