package shuhuai.badmintonflashbackend.service;

import java.time.Duration;

public interface IRateLimitService {
    boolean tryConsume(String userKey, long capacity, Duration refillDuration);
}
