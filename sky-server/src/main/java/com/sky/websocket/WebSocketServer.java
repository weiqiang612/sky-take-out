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
    private static final Map<String, Session> SESSION_POOL = new ConcurrentHashMap<>();

    /**
     * 连接建立时调用
     *
     * @param session
     * @param clientId
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("clientId") String clientId) {
        SESSION_POOL.put(clientId, session);
        log.info("客户端建立连接成功：{},当前在线人数：{}", clientId, SESSION_POOL.size());
    }

    /**
     * 连接关闭时调用
     *
     * @param clientId
     */
    @OnClose
    public void onClose(@PathParam("clientId") String clientId) {
        log.info("客户端断开连接：{}", clientId);
        SESSION_POOL.remove(clientId);
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
        Session session = SESSION_POOL.get(clientId);
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
        if (SESSION_POOL.isEmpty()) return;
        Collection<Session> sessions = SESSION_POOL.values();
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
