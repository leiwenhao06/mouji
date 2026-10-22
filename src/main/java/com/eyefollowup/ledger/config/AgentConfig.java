package com.eyefollowup.ledger.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 建议 Agent 的线程池：小并发 + 有界队列，队列满时拒绝，避免拖垮服务。
 */
@Configuration
public class AgentConfig {

    @Bean(name = "agentExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor agentExecutor(@Value("${app.agent.max-concurrent:2}") int concurrency,
                                            @Value("${app.agent.queue-size:20}") int queueSize) {
        AtomicInteger seq = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                concurrency, concurrency, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, queueSize)),
                r -> {
                    Thread t = new Thread(r, "agent-worker-" + seq.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}
