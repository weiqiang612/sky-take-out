package com.sky.controller.notify;

import com.alibaba.druid.support.json.JSONUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.context.BaseContext;
import com.sky.properties.WeChatProperties;
import com.sky.service.OrdersService;
import com.sky.websocket.WebSocketServer;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * 支付回调相关接口
 */
@RestController
@RequestMapping("/notify")
@Slf4j
public class PayNotifyController {
    @Autowired
    private OrdersService ordersService;
    @Autowired
    private WeChatProperties weChatProperties;


    /**
     * 支付成功回调
     *
     * @param request
     */
    @RequestMapping("/paySuccess")
    public void paySuccessNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        //读取数据
        String body = readData(request);
        log.info("支付成功回调：{}", body);

        // 准备跳过解密，直接拿订单号，用于测试环境
        String outTradeNo;
        JSONObject bodyJson = JSON.parseObject(body);

        // 真实微信回调必带 resource 字段，这里供测试使用
        if (!bodyJson.containsKey("resource")) {
            log.info("检测到模拟支付请求，跳过解密环节...");
            // 直接从 body 拿订单号
            outTradeNo = bodyJson.getString("out_trade_no");
        } else {
            // 3. 走原有的微信解密逻辑（由于你没有证书，这里平时运行会报错，建议注释或保留供研究）
            log.info("检测到微信标准加密请求，尝试解密...");
            //数据解密
            try {
                String plainText = decryptData(body);
                log.info("解密后的文本：{}", plainText);

                JSONObject jsonObject = JSON.parseObject(plainText);
                outTradeNo = jsonObject.getString("out_trade_no");//商户平台订单号
            } catch (Exception e) {
                log.error("解密失败（可能是因为没有真实的 APIV3 密钥）：{}", e.getMessage());
                return; // 终止执行
            }
        }
//            String transactionId = jsonObject.getString("transaction_id");//微信支付交易号
//            log.info("微信支付交易号：{}", transactionId);
        log.info("商户平台订单号：{}", outTradeNo);

        //业务处理，修改订单状态、来单提醒
        if (outTradeNo != null) {
            ordersService.paySuccess(outTradeNo);
        }

        // 给微信响应
        responseToWeixin(response);
    }

    /**
     * 读取数据
     *
     * @param request
     * @return
     * @throws Exception
     */
    private String readData(HttpServletRequest request) throws Exception {
        BufferedReader reader = request.getReader();
        StringBuilder result = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            if (result.length() > 0) {
                result.append("\n");
            }
            result.append(line);
        }
        return result.toString();
    }

    /**
     * 数据解密
     *
     * @param body
     * @return
     * @throws Exception
     */
    private String decryptData(String body) throws Exception {
        JSONObject resultObject = JSON.parseObject(body);
        JSONObject resource = resultObject.getJSONObject("resource");
        String ciphertext = resource.getString("ciphertext");
        String nonce = resource.getString("nonce");
        String associatedData = resource.getString("associated_data");

        AesUtil aesUtil = new AesUtil(weChatProperties.getApiV3Key().getBytes(StandardCharsets.UTF_8));
        //密文解密
        String plainText = aesUtil.decryptToString(associatedData.getBytes(StandardCharsets.UTF_8),
                nonce.getBytes(StandardCharsets.UTF_8),
                ciphertext);

        return plainText;
    }

    /**
     * 给微信响应
     *
     * @param response
     */
    private void responseToWeixin(HttpServletResponse response) throws Exception {
        response.setStatus(200);
        HashMap<Object, Object> map = new HashMap<>();
        map.put("code", "SUCCESS");
        map.put("message", "SUCCESS");
        response.setHeader("Content-type", ContentType.APPLICATION_JSON.toString());
        response.getOutputStream().write(JSONUtils.toJSONString(map).getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }
}
