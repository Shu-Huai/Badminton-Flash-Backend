package shuhuai.badmintonflashbackend.utils;

import jakarta.annotation.Resource;
import lombok.Getter;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
@Component
public class RedisConnector {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    public RedisConnector(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Boolean setExpire(String key, long time) {
        try {
            if (time > 0) {
                redisTemplate.expire(key, time, TimeUnit.SECONDS);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    public Boolean existObject(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public void deleteObject(String... key) {
        if (key != null && key.length > 0) {
            if (key.length == 1) {
                redisTemplate.delete(key[0]);
            } else {
                redisTemplate.delete((Collection<String>) CollectionUtils.arrayToList(key));
            }
        }
    }

    public Object readObject(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }

    public Boolean writeObject(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean writeObject(String key, Object value, Long time) {
        try {
            if (time > 0) {
                redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
            } else {
                return writeObject(key, value);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long increaseObject(String key, Long number) {
        return number > 0 ? redisTemplate.opsForValue().increment(key, number) : null;
    }

    public Long decreaseObject(String key, Long number) {
        return number > 0 ? redisTemplate.opsForValue().increment(key, -number) : null;
    }

    public Object readMap(String key, String item) {
        return redisTemplate.opsForHash().get(key, item);
    }

    public Map<Object, Object> readMap(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public Boolean writeMap(String key, Map<String, Object> map) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean writeMap(String key, Map<String, Object> map, Long time) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            if (time > 0) {
                setExpire(key, time);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean writeMap(String key, String item, Object value) {
        try {
            redisTemplate.opsForHash().put(key, item, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean writeMap(String key, String item, Object value, Long time) {
        try {
            redisTemplate.opsForHash().put(key, item, value);
            if (time > 0) {
                return setExpire(key, time);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public Void deleteMap(String key, Object... item) {
        redisTemplate.opsForHash().delete(key, item);
        return null;
    }

    public Boolean existMap(String key, String item) {
        return redisTemplate.opsForHash().hasKey(key, item);
    }

    public Double increaseMap(String key, String item, Double number) {
        return redisTemplate.opsForHash().increment(key, item, number);
    }

    public Double decreaseMap(String key, String item, Double number) {
        return redisTemplate.opsForHash().increment(key, item, -number);
    }

    public Set<Object> readSet(String key) {
        try {
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            return null;
        }
    }

    public Boolean existSet(String key, Object value) {
        try {
            return redisTemplate.opsForSet().isMember(key, value);
        } catch (Exception e) {
            return false;
        }
    }

    public Long writeSet(String key, Object... values) {
        try {
            return redisTemplate.opsForSet().add(key, values);
        } catch (Exception e) {
            return 0L;
        }
    }

    public Long writeSet(String key, Long time, Object... values) {
        try {
            Long count = redisTemplate.opsForSet().add(key, values);
            if (time > 0) {
                setExpire(key, time);
            }
            return count;
        } catch (Exception e) {
            return 0L;
        }
    }

    public Long getSetSize(String key) {
        try {
            return redisTemplate.opsForSet().size(key);
        } catch (Exception e) {
            return 0L;
        }
    }

    public Long deleteSet(String key, Object... values) {
        try {
            return redisTemplate.opsForSet().remove(key, values);
        } catch (Exception e) {
            return 0L;
        }
    }

    public List<Object> readList(String key, Long start, Long end) {
        try {
            return redisTemplate.opsForList().range(key, start, end);
        } catch (Exception e) {
            return null;
        }
    }

    public Long getListSize(String key) {
        try {
            return redisTemplate.opsForList().size(key);
        } catch (Exception e) {
            return 0L;
        }
    }

    public Object readList(String key, Long index) {
        try {
            return redisTemplate.opsForList().index(key, index);
        } catch (Exception e) {
            return null;
        }
    }

    public Boolean writeList(String key, Object value) {
        try {
            redisTemplate.opsForList().rightPush(key, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean writeList(String key, Object value, Long time) {
        try {
            redisTemplate.opsForList().rightPush(key, value);
            if (time > 0) {
                setExpire(key, time);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean writeList(String key, List<Object> value) {
        try {
            redisTemplate.opsForList().rightPushAll(key, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean writeList(String key, List<Object> value, Long time) {
        try {
            redisTemplate.opsForList().rightPushAll(key, value);
            if (time > 0) {
                setExpire(key, time);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean writeList(String key, Long index, Object value) {
        try {
            redisTemplate.opsForList().set(key, index, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long deleteList(String key, Long count, Object value) {
        try {
            return redisTemplate.opsForList().remove(key, count, value);
        } catch (Exception e) {
            return 0L;
        }
    }

    public Set<String> getKeys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    public void convertAndSend(String channel, Object message) {
        redisTemplate.convertAndSend(channel, message);
    }

    public void pushList(String listKey, Object... values) {
        BoundListOperations<String, Object> boundValueOperations = redisTemplate.boundListOps(listKey);
        boundValueOperations.rightPushAll(values);
    }

    public void pushList(String listKey, Status.ExpireEnum expireEnum, Object... values) {
        BoundListOperations<String, Object> boundValueOperations = redisTemplate.boundListOps(listKey);
        boundValueOperations.rightPushAll(values);
        boundValueOperations.expire(expireEnum.getTime(), expireEnum.getTimeUnit());
    }

    public List<Object> rangeList(String listKey, long start, long end) {
        BoundListOperations<String, Object> boundValueOperations = redisTemplate.boundListOps(listKey);
        return boundValueOperations.range(start, end);
    }

    public Object popList(String listKey) {
        BoundListOperations<String, Object> boundValueOperations = redisTemplate.boundListOps(listKey);
        return boundValueOperations.rightPop();
    }
}

abstract class Status {
    public enum ExpireEnum {
        UNREAD_MSG(30L, TimeUnit.DAYS);
        private Long time;
        private TimeUnit timeUnit;

        ExpireEnum(Long time, TimeUnit timeUnit) {
            this.time = time;
            this.timeUnit = timeUnit;
        }

        public void setTime(Long time) {
            this.time = time;
        }

        public void setTimeUnit(TimeUnit timeUnit) {
            this.timeUnit = timeUnit;
        }

        public Long getTime() {
            return time;
        }

        public TimeUnit getTimeUnit() {
            return timeUnit;
        }
    }
}