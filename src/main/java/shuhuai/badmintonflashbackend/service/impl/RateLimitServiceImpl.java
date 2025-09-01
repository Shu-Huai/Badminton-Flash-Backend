package shuhuai.badmintonflashbackend.service.impl;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.CommandExecutor;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.serialization.Mapper;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import io.github.bucket4j.redis.redisson.cas.RedissonBasedProxyManager;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.command.CommandAsyncExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.service.IRateLimitService;

import java.time.Duration;
import java.util.function.Supplier;

@Service
public class RateLimitServiceImpl implements IRateLimitService {
    private final ProxyManager<String> buckets;

    @Autowired
    public RateLimitServiceImpl(RedissonClient redisson) {
        CommandExecutor exec = (CommandExecutor) ((Redisson) redisson).getCommandExecutor();

        this.buckets = Bucket4jRedisson
                .casBasedBuilder((CommandAsyncExecutor) exec)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .keyMapper(Mapper.STRING) // 用 STRING 映射；前缀用 key 里加（见下）
                .build();
    }

    @Override
    public boolean tryConsume(String userKey, long capacity, Duration refillDuration) {
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(limit ->
                        limit.capacity(capacity).refillGreedy(capacity, refillDuration))
                .build();

        // 想要 "limit:" 前缀，直接在 key 上传入即可
        Bucket bucket = buckets.getProxy(RedisKeys.limitKey(userKey), () -> config);
        return bucket.tryConsume(1);
    }
}
