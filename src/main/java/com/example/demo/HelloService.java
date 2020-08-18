package com.example.demo;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedTimeLimiterMetrics;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.vavr.control.Try;
import java.time.Duration;
import java.util.Arrays;
import java.util.Timer;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author VonXXGhost
 * @date 2020/8/17 下午 7:36
 */
@Service
public class HelloService {

    private TimeLimiter timeLimiter;

    private CircuitBreaker circuitBreaker;

    private RateLimiter rateLimiter;

    @Autowired
    private MeterRegistry meterRegistry;

    @PostConstruct
    public void init() {
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.of(
                TimeLimiterConfig.custom()
                        // 当超时时是否关闭取消线程
                        .cancelRunningFuture(true)
                        // 超时时间
                        .timeoutDuration(Duration.ofMillis(300))
                        .build()
        );
        timeLimiter = timeLimiterRegistry.timeLimiter("Hello-TimeLimiter");

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                // 失败率大于多少时打开熔断器
                .failureRateThreshold(50)
                // 失败判断的窗口大小
                // 现在：连续5个内有50%是失败的就打开熔断
                .slidingWindowSize(5)
                // 半打开状态时的限制通过量，也是半打开时的窗口大小
                .permittedNumberOfCallsInHalfOpenState(5)
                // 熔断器打开持续时间（多少秒之后转为半打开状态）
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .build();
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("Hello-CircuitBreaker");

        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                // 时间区间内允许的最多调用数
                .limitForPeriod(8)
                // 时间区间大小
                .limitRefreshPeriod(Duration.ofSeconds(1))
                // 线程等待时间（0 就是直接拒绝）
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(rateLimiterConfig);
        rateLimiter = rateLimiterRegistry.rateLimiter("Hello-RateLimiter");

        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry)
                .bindTo(meterRegistry);
        TaggedRateLimiterMetrics.ofRateLimiterRegistry(rateLimiterRegistry)
                .bindTo(meterRegistry);
        TaggedTimeLimiterMetrics.ofTimeLimiterRegistry(timeLimiterRegistry)
                .bindTo(meterRegistry);
    }

    private String sayHello(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException ignored) {
        }
        return "hello";
    }

    public String sayHelloLimit(int time) {
        State state = circuitBreaker.getState();
        // 创建单线程的线程池（用于超时控制）
        ExecutorService pool = Executors.newSingleThreadExecutor();
        // 将被保护方法包装为能够返回Future的supplier函数
        Supplier<Future<String>> futureSupplier = () -> pool.submit(() -> sayHello(time));
        Callable<String> restrictedCallable = TimeLimiter.decorateFutureSupplier(timeLimiter, futureSupplier);
        Callable<String> chainedCallable = CircuitBreaker.decorateCallable(circuitBreaker, restrictedCallable);
        chainedCallable = RateLimiter.decorateCallable(rateLimiter, chainedCallable);
        Try<String> result = Try.ofCallable(chainedCallable)
                .recover(CallNotPermittedException.class,
                        throwable -> time + " Fallback:" + state + "->" + circuitBreaker.getState())
                .recover(TimeoutException.class,
                        throwable -> time + " Fallback: Timeout " + state + "->" + circuitBreaker.getState())
                .recover(RequestNotPermitted.class,
                        throwable -> time + " Fallback: RateLimit " + state + "->" + circuitBreaker.getState())
                .recover(throwable -> time + " Fallback: " + throwable.getClass());

        return result.get();
    }
}
