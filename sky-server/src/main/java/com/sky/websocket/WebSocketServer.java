package com.sky.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/24 17:49
 */

@Component
@Slf4j
@ServerEndpoint("/ws/{clientId}") // 这个serverEndpoint注解可以类比@RequestMapping
public class WebSocketServer {

    // 用来存储会话
    private static Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    /**
     * 连接建立时调用
     *
     * @param session
     * @param clientId
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("clientId") String clientId) {
        log.info("客户端建立连接成功：{}", clientId);
        sessionMap.put(clientId, session);
    }

    /**
     * 连接关闭时调用
     *
     * @param clientId
     */
    @OnClose
    public void onClose(@PathParam("clientId") String clientId) {
        log.info("客户端断开连接：{}", clientId);
        sessionMap.remove(clientId);
    }

    /**
     * 收到消息时调用
     *
     * @param message
     * @param clientId
     */
    @OnMessage
    public void onMessage(String message, @PathParam("clientId") String clientId) {
        System.out.println("收到来自" + clientId + "的消息" + message);
    }

    /**
     * 服务器向指定客户端推送消息
     */
    public void sendToClient(String clientId, String message) {
        Session session = sessionMap.get(clientId);
        if (session != null) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                log.error("推送到客户端 {} 失败", clientId, e);
            }
        }
    }

    /**
     * 群发
     *
     * @param message
     */
    public void sendToAllClient(String message) {
        if (sessionMap.isEmpty()) return;
        Collection<Session> sessions = sessionMap.values();
        // 服务端向客户端发送消息
        for (Session session : sessions) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
