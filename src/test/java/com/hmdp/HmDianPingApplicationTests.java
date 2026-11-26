package com.hmdp;

import com.hmdp.utils.RedisWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class HmDianPingApplicationTests {

    @Resource 
    private StringRedisTemplate stringRedisTemplate;

    @Resource 
    private RedisWorker redisWorker;

    @Test
    public void testWorker() throws InterruptedException {
        ExecutorService es = Executors.newFixedThreadPool(300);
        CountDownLatch latch = new CountDownLatch(300);
        // 用原子类统计成功和失败的次数
        AtomicInteger successCount = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                for (int i = 0; i < 100; i++) {
                    long id = redisWorker.nextId("order"); 
                    successCount.incrementAndGet(); 
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown(); 
            }
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        // 等待所有线程完成（最多等30秒）
        latch.await(30, TimeUnit.SECONDS); 
        
        long end = System.currentTimeMillis();
        System.out.println("=================================");
        System.out.println("成功生成 ID 数量：" + successCount.get() + " (期望30000)");
        System.out.println("总耗时：" + (end - begin) + " ms");
        
        // 查一下 Redis 里的结果
        Set<String> keys = stringRedisTemplate.keys("icr:*");
        System.out.println("生成的 Redis Key：" + keys);
        for (String key : keys) {
            System.out.println(key + " 当前自增值：" + stringRedisTemplate.opsForValue().get(key));
        }
        System.out.println("=================================");

        es.shutdown();
    }


    
}