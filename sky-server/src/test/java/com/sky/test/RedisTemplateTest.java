package com.sky.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author 袁志刚
 * @version 1.0
 * @Date 2026/3/9 21:33
 */
//@SpringBootTest
public class RedisTemplateTest {

    @Autowired
    private RedisTemplate redisTemplate;

    @Test
    public void testRedis() {
        HashSet<String> strings = new HashSet<>();
        ValueOperations valueOperations = redisTemplate.opsForValue();
        HashOperations hashOperations = redisTemplate.opsForHash();
        ListOperations listOperations = redisTemplate.opsForList();
        SetOperations setOperations = redisTemplate.opsForSet();
        ZSetOperations zSetOperations = redisTemplate.opsForZSet();
    }

    /**
     * 测试字符串类型操作
     */
    @Test
    public void testRedis1() {
        // 演示 set get setex setnx
        ValueOperations valueOperations = redisTemplate.opsForValue();
        valueOperations.set("name", "小明");
        System.out.println(valueOperations.get("name"));
        // 设置时效为2分钟的 age : 22 数据
        valueOperations.set("age", "22", 2, TimeUnit.MINUTES);
        valueOperations.setIfAbsent("lock", "1");
        valueOperations.setIfAbsent("lock", "2");
    }

    // 操作Hash类型的数据
    @Test
    public void testRedis2() {
        // hset hget hdel hkeys hvals
        HashOperations hashOperations = redisTemplate.opsForHash();
//        hashOperations.put("dish:100","name","水煮鱼");
//        hashOperations.put("dish:100","price","10");
//        hashOperations.put("dish:100","category","鱼");
//        hashOperations.put("dish:100","image","好看的鱼");
        HashMap<String, String> map = new HashMap<>();
        map.put("name", "水煮鱼");
        map.put("price", "10");
        map.put("category", "鱼");
        map.put("image", "好看的鱼");
        hashOperations.putAll("dish:100", map);
        System.out.println(hashOperations.get("dish:100", "name"));
//        hashOperations.delete("dish:100","name");
        Set keys = hashOperations.keys("dish:100");
        System.out.println(keys);
        List values = hashOperations.values("dish:100");
        System.out.println(values);
    }

    // 操作List
    @Test
    public void testRedis3() {
        // lpush/rpush lpop/rpop lrange llen
        ListOperations listOperations = redisTemplate.opsForList();
        listOperations.leftPushAll("List", 1, 2, 3, 4, 5);
        List list = listOperations.range("List", 0, -1);
        System.out.println(list);
        Long size = listOperations.size("List");
        for (int i = 0; i < size; i++) {
            System.out.println(listOperations.leftPop("List"));
        }
//        Object value;
//        while ((value = listOperations.leftPop("List")) != null) {
//            System.out.println(value);
//        }
    }

    // 操作set
    @Test
    public void testRedis4() {
        // sadd smembers srem scard sinter、sunion、sdiff
        SetOperations setOperations = redisTemplate.opsForSet();
        setOperations.add("set1","a",1,"c",1.0);
        Set set1 = setOperations.members("set1");
        System.out.println(set1);
        setOperations.remove("set1", "a");
        Set set2 = setOperations.members("set1");
        System.out.println(set2);
        System.out.println(setOperations.size("set1"));

        setOperations.add("set2",1);
        // 交集 1
        System.out.println(setOperations.intersect("set1", "set2"));
        // 并集
        System.out.println(setOperations.union("set1", "set2"));
        // 差集
        System.out.println(setOperations.difference("set1", "set2"));
    }

    // 操作zset
    @Test
    public void testRedis5() {
        // zadd zrange zscore zrank zrem
        ZSetOperations zSetOperations = redisTemplate.opsForZSet();
        HashSet<ZSetOperations.TypedTuple<Object>> tuples = new HashSet<>();
        tuples.add(new DefaultTypedTuple<>("A", 9.5));
        tuples.add(new DefaultTypedTuple<>("B", 8.0));
        tuples.add(new DefaultTypedTuple<>("C", 9.9));
        tuples.add(new DefaultTypedTuple<>("D", 8.0));
        zSetOperations.add("zset1", tuples);
        zSetOperations.add("zset1","E",10);
        Set zset1 = zSetOperations.range("zset1", 0, -1);
        System.out.println(zset1);
        System.out.println(zSetOperations.score("zset1", "E"));
        System.out.println(zSetOperations.rank("zset1", "E"));
        zSetOperations.remove("zset1", "E");
        System.out.println(zSetOperations.range("zset1", 0, -1));
    }


    // 通用命令
    @Test
    public void testRedis6() {
        // keys exists type del
        redisTemplate.keys("*").forEach(System.out::println);
        System.out.println(redisTemplate.hasKey("List"));
        System.out.println(redisTemplate.type("zset1"));
        redisTemplate.delete(redisTemplate.keys("*"));
    }
}
