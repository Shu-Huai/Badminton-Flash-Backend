# Badminton Flash Backend

校园羽毛球约场后端服务。

- 技术栈：Spring Boot 3、MyBatis-Plus、MySQL、Redis/Redisson、RabbitMQ、Bucket4j、JWT、Knife4j
- 核心能力：秒杀式预约、异步落库、支付与退款、自动超时取消、RBAC 权限控制

## 目录

- `docs/KernelProcess.md`：预约/支付内核流程（含异常、取消、退款）
- `docs/RBAC.md`：权限设计
- `docs/Scheduler.md`：定时任务说明

## 环境要求

- JDK `23`
- Maven `3.9+`
- MySQL `8+`
- Redis `6+`
- RabbitMQ `3.12+`

## 本地启动

1. 准备基础服务：MySQL、Redis、RabbitMQ。
2. 修改配置：`src/main/resources/application-dev.yml`。
3. 启动应用：

```bash
mvn spring-boot:run
```

4. 默认端口：`25001`。
5. OpenAPI 文档：`/doc.html`。

## 配置说明

核心配置在：`src/main/resources/application.yml`、`src/main/resources/application-dev.yml`。

必改项：

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.data.redis.host`
- `spring.rabbitmq.host`
- `token.privateKey`
- `jasypt.encryptor.password`

说明：

- 项目默认启用 `dev` profile。
- `spring.sql.init.mode=always`，启动会执行 `schema.sql` 与 `data.sql`。
- `data.sql` 会初始化系统配置，并尝试插入管理员账号 `student_id=root`（密码为数据库中的 MD5 值，不提供明文）。

## 核心业务流程

1. 定时预热当天可抢 `time_slot` 到 Redis。
2. 到开闸时间后放开 `gate`。
3. 用户请求先经过限流、去重、库存扣减，再投递 MQ。
4. 消费者异步落库为 `PENDING_PAYMENT`。
5. 支付成功后变更为 `CONFIRMED`。
6. 支持主动取消、超时取消、退款，均包含资源回补。

详细流程见 `docs/KernelProcess.md`。

## 认证与权限

- 登录注册接口免鉴权：`/auth/login`、`/auth/register`
- 其他接口需要请求头：`Authorization: Bearer <token>`
- 角色：`USER`、`ADMIN`
- `@RequireRole` 支持基于 token 与数据库角色双重校验

## 主要接口

认证：

- `POST /auth/register`
- `POST /auth/login`

用户：

- `GET /user/me`
- `PATCH /user/me`
- `DELETE /user/me`

浏览：

- `GET /browse/session`
- `GET /browse/court`
- `GET /browse/slot`
- `GET /browse/reservation`

预约：

- `POST /reserve/`
- `GET /reserve/result/{traceId}`
- `DELETE /reserve/{reservationId}`

支付：

- `POST /pay/wechat/{reservationId}`
- `POST /pay/wechat/mock-success/{outTradeNo}`（管理员）
- `POST /pay/refund/{reservationId}`
- `GET /pay/reservation/{reservationId}`

管理：

- `PATCH /admin/system`
- `GET /admin/system`
- `POST /admin/session`
- `POST /admin/warmup/{sessionId}`
- `POST /admin/open/{sessionId}`
- `POST /admin/slot-gen/{sessionId}`
- `GET /admin/users`

## 请求示例

注册：

```bash
curl -X POST 'http://localhost:25001/auth/register' \
  -H 'Content-Type: application/json' \
  -d '{"studentId":"20260001","password":"123456"}'
```

登录：

```bash
curl -X POST 'http://localhost:25001/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"studentId":"20260001","password":"123456"}'
```

预约：

```bash
curl -X POST 'http://localhost:25001/reserve/' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <token>' \
  -d '{"slotId":1,"sessionId":1}'
```

## 定时任务

- slot 预热
- 自动开闸
- 超时未支付取消
- 球场配置启动对账

详细说明见 `docs/Scheduler.md`。

## 常见问题

- 启动报数据库连接失败：检查 `application-dev.yml` 数据源配置与数据库权限。
- Redis/RabbitMQ 连接失败：检查网络、端口和账号。
- 全部接口 `TOKEN_INVALID`：确认请求头格式必须为 `Authorization: Bearer <token>`。
- 预约返回 `UNGATED`：当前场次未开闸。
- 预约返回 `OUT_OF_STOCK`：当前 `slotId` 库存已被抢空。

## 打包

```bash
mvn clean package
java -jar target/Badminton-Flash-Backend-1.0.jar
```
