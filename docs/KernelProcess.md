# 核心流程

## 概述

我在做一个校园羽毛球约场系统，后端用 Spring Boot 3。

需求：每天 13:00 开放当天场地预约，可能有秒杀级并发。

技术栈：Spring Boot 3、Redis、Redisson、RabbitMQ、MySQL、MyBatis-Plus、Bucket4j、Fastjson、Lombok、Knife4j。

## 设计理念（抓住三件事）

1. **唯一资源 ID**：把“可抢的对象”抽象成 `time_slot`（用 `slotId` 做唯一键）。 
2. **快慢分层**：入口用 Redis/Redisson 完成“去重 + 扣库存”，**只做极快逻辑**；慢操作（落库等）放到 MQ 异步。 
3. **幂等 & 不超卖**：前端再怎么点，也只能拿到一次名额；总名额绝不超过 `permits`。

## 核心组件与职责

- **Redis / Redisson** 
  - `Semaphore`：库存（permits = 该时段可预约份数；一般=1）。 
  - `RSet`：用户去重（同一用户同一时段只允许一次）。 
  - （可选）`RBucket`：开闸标志 `gate`。 
- **RabbitMQ** 
  - 解耦与削峰：抢到名额立即入队，后续落库异步处理。 
- **MySQL + MyBatis-Plus** 
  - 落库与最终一致性；依赖你已有的唯一约束 `reservation(slot_id)`。 
- **Bucket4j** 
  - API 限流（保护入口、抗流量洪峰与恶意重放）。 
- **Spring Boot 3** 
  - 控制器与定时任务（预热、开闸等）。

## Redis Key 建议（按天过期）

- `bf:sem:{slotId}`：`RSemaphore`，许可数=可预约份数。 
- `bf:dedup:{slotId}`：`RSet<userId>`，去重集合（防同人重复抢）。 
- `bf:gate:{yyyy-MM-dd}`：`RBucket<String>`，值 `"1"` 表示已开闸。

> 这些 key 都设置 TTL 到当天 23:59:59，隔天自动清理。

## 端到端流程（时序）
```
[T-5 min, 12:55] 预热任务
1) 查询当日可用 time_slot（is_active=1, slot_date=today）
2) 为每个 slot 初始化 RSemaphore 许可（1 或容量值）
3) 清空/新建去重集合 RSet，并设置 TTL
4) gate 置为 "0"

[13:00] 开闸
5) 将 bf:gate:{today} 置 "1"

[13:00+ 用户发起 /reserve(userId, slotId)]
6) (可选) 先读 gate；若非 "1" 返回“未开闸”
7) Bucket4j 做总量 & 单用户限流
8) 读 DB 快速校验 slot（当日 & active）——可被本地缓存/Redis 缓存加速
9) 去重：bf:dedup:{slotId}.add(userId)
    - 若返回 false：已参加 → 直接返回“已参与”
10) 抢库存：bf:sem:{slotId}.tryAcquire()
    - false：回滚去重（remove userId），返回“已抢光”
11) 入队：发送 SeckillOrderMsg(userId, slotId, traceId)
    - 立即向用户返回“抢到啦，稍后确认”

[异步消费者]
12) 幂等落库：
    - insert reservation(user_id, slot_id, status='CONFIRMED')
    - 若唯一键冲突（slot_id 已有）：说明别的消费者/重试先一步成功 → 幂等完成（直接 ACK）
13) 失败补偿（极端情况）
    - 若因临时 DB 故障失败：NACK 进入重试/DLQ
    - 只有当**最终无法**持久化且确定要释放名额时，才 sem.release()（慎用；通常依赖重试即可）
```

> 关键点：入队是在拿到 permit 后进行；消费者侧以 DB 唯一约束保证幂等，无需复杂分布式锁。

## “快/慢分层”是啥？

把一次请求拆成两段，各自用最合适的技术：

- **快路径（Fast Path）**：必须在毫秒级完成、对并发极其友好的那部分。

  放在 **Redis/Redisson** 完成：开闸校验、限流、用户去重、库存扣减、入队。

  目标：**抗洪峰 + 不超卖 + 不阻塞**。
- **慢路径（Slow Path）**：不要求毫秒完成、但要**可靠**和**可追溯**的那部分。

  放在 **RabbitMQ → MySQL**：落库、状态机更新、日志、通知、后续业务。
  
  目标：**最终一致性 + 幂等 + 易扩展**。

一个简化的时序：

```
Client
  │ POST /reserve
  ▼
Fast Path（Redis）
  - gate(13:00) 已开？
  - Bucket4j 限流
  - RSet 去重(userId, slotId)
  - RSemaphore.tryAcquire 扣库存
  - MQ 入队（立刻给用户“抢到啦”）
  ▼
Slow Path（MQ Consumer）
  - DB insert reservation（唯一约束兜幂等）
  - 状态/通知/日志
```

### 和“开始前把可选场地缓存到 Redis”什么关系？

- **预热缓存**（把当天可抢的 `time_slot` 放到 Redis）只是快路径里的**一个手段**，它减少请求时查库的次数。
- 但**快/慢分层 > 仅仅缓存**：
    - 快路径还包括：**开闸标志、用户去重、库存扣减、限流、入队**；
    - 慢路径承担：**落库、幂等、重试、补偿、审计**。

可以这么理解：

- 预热=把“数据”放近一点；
- 快/慢分层=把“职责”分开：快的都在内存/Redis里完成并发控制，慢的交给队列+数据库保证可靠性。

## 已完成

```java
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Court {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String courtName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Boolean isActive;
}
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Reservation {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    private Integer slotId;
    private ReservationStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Boolean isActive;
}
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlot {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer courtId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Boolean isActive;
}
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String studentId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Boolean isActive;
}
@Data
@SuppressWarnings({"unused"})
public class Response<Type> implements Serializable {
    private int code;
    private ResponseCode enumCode;
    private String message;
    private Type data;

    public Response() {
        code = ResponseCode.SUCCESS.getCode();
        enumCode = ResponseCode.SUCCESS;
        message = ResponseCode.SUCCESS.getMsg();
        data = null;
    }

    public Response(ResponseCode code) {
        this.code = code.getCode();
        this.enumCode = code;
        this.message = code.getMsg();
    }

    //
    public Response(Throwable error) {
        this.message = error.getMessage();
    }

    public Response(ResponseCode code, Type data) {
        this.code = code.getCode();
        enumCode = code;
        message = code.getMsg();
        this.data = data;
    }

    public Response(Type data) {
        code = ResponseCode.SUCCESS.getCode();
        enumCode = ResponseCode.SUCCESS;
        message = ResponseCode.SUCCESS.getMsg();
        this.data = data;
    }

    public void setCode(ResponseCode code) {
        this.code = code.getCode();
        this.enumCode = code;
        this.message = code.getMsg();
        this.data = null;
    }
}
@Getter
public enum ResponseCode {
    SUCCESS(200, "操作成功"),

    UNAUTHORIZED(401, "需要身份认证"),

    USERNAME_OR_PASSWORD_ERROR(401, "用户名或密码错误"),

    USERNAME_EXISTED(2041, "用户名已存在"),

    TOKEN_EXPIRED(401, "token已过期"),

    TOKEN_INVALID(401, "token无效"),

    FORBIDDEN(403, "权限不足"),

    FAILED(1001, "操作失败"),

    VALIDATE_FAILED(1002, "参数校验失败"),

    VSPHERE_LINK_ERROR(2001, "vsphere连接操作失败"),

    ERROR(5000, "未知错误"),

    PARAM_ERROR(5010, "参数错误"),

    SQL_ERROR(5001, "服务器错误");

    private final int code;
    private final String msg;

    ResponseCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
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
  @Getter
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
  }
}
```
```sql
create table if not exists court
(
    id          int auto_increment
        primary key,
    court_name  varchar(255)                         null,
    create_time timestamp  default CURRENT_TIMESTAMP not null,
    update_time timestamp  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    is_active   tinyint(1) default 1                 not null
);

create table if not exists time_slot
(
    id          int auto_increment
        primary key,
    slot_date   date                                 not null,
    start_time  time                                 not null,
    end_time    time                                 not null,
    court_id    int                                  not null,
    create_time timestamp  default CURRENT_TIMESTAMP not null,
    update_time timestamp  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    is_active   tinyint(1) default 1                 not null,
    constraint time_slot_pk_2
        unique (slot_date, start_time, end_time, court_id),
    constraint time_slot_court_id_fk
        foreign key (court_id) references court (id)
);

create table if not exists user_account
(
    id          int auto_increment
        primary key,
    student_id  varchar(31)                          not null,
    create_time timestamp  default CURRENT_TIMESTAMP not null,
    update_time timestamp  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    is_active   tinyint(1) default 1                 not null,
    constraint user_account_pk
        unique (student_id)
);

create table if not exists reservation
(
    id          int auto_increment
        primary key,
    user_id     int                                  not null,
    slot_id     int                                  not null,
    status      varchar(127)                         not null,
    create_time timestamp  default CURRENT_TIMESTAMP not null,
    update_time timestamp  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    is_active   tinyint(1) default 1                 not null,
    constraint reservation_pk
        unique (slot_id),
    constraint reservation___fk
        foreign key (slot_id) references time_slot (id),
    constraint reservation_user_id_fk
        foreign key (user_id) references user_account (id)
);
```