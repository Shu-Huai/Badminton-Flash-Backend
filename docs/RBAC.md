# RBAC 设计说明

## 1. 目标

- 使用轻量 RBAC，不引入 Spring Security。
- 角色仅包含 `USER` 和 `ADMIN`。
- 业务规则统一收敛到 `UserServiceImpl`，Controller 只负责接口编排。

## 2. 角色模型

- `USER`
- `ADMIN`（继承 `USER` 权限）

权限继承规则：

- 当接口要求 `USER` 时，`ADMIN` 也允许访问。

## 3. 账户规则

1. 注册默认创建 `USER` 账户。
2. `USER` 只能查看、修改、删除自己的账户。
3. `ADMIN` 可以查看所有账户，也可以查看自己。
4. `ADMIN` 可以创建 `USER/ADMIN` 账户。
5. `ADMIN` 可以修改 `USER` 账户（学号、密码、角色）。
6. `ADMIN` 不可修改其他 `ADMIN` 账户，但可以修改自己。
7. `ADMIN` 可以删除 `USER` 账户。
8. `ADMIN` 不可删除任何 `ADMIN` 账户（包括自己）。

## 4. 控制器设计

### 4.1 AuthController

路径前缀：`/auth`（匿名可访问）

- `POST /auth/register`
- `POST /auth/login`

### 4.2 UserController

路径前缀：`/user`，类级权限：`@RequireRole(USER)`

- `GET /user/me`
- `PATCH /user/me`
- `DELETE /user/me`

### 4.3 AdminController

路径前缀：`/admin`，沿用现有系统管理接口，并新增账户管理接口。

管理员接口默认使用方法级权限：`@RequireRole(value = ADMIN, dbCheck = true)`，
即先按 token 做 RBAC，再按数据库做二次校验（账号状态 + 当前角色）。

新增账户管理接口：

- `GET /admin/users`
- `GET /admin/users/{id}`
- `POST /admin/users`
- `PATCH /admin/users/{id}`
- `DELETE /admin/users/{id}`

## 5. Service 设计

统一使用一个实现类：`UserServiceImpl`（实现 `IUserService`）。

### 5.1 认证相关

- `register(studentId, password)`
- `login(studentId, password)`
- `getRole(userId)`

### 5.2 用户自助

- `getMe(userId)`
- `updateMe(userId, UserSelfUpdateDTO dto)`
- `deleteMe(userId)`

### 5.3 管理员管理用户

- `listUsers()`
- `getUser(userId)`
- `adminCreateUser(operatorUserId, AdminCreateUserDTO dto)`
- `adminUpdateUser(operatorUserId, targetUserId, AdminUpdateUserDTO dto)`
- `adminDeleteUser(operatorUserId, targetUserId)`

### 5.4 内部守卫方法

- `requireActiveUser(userId)`
- `assertCanAdminUpdate(operator, target)`
- `saveUser(userAccount)`

## 6. DTO / VO

### DTO

- `UserDTO`：登录/注册入参（`studentId`, `password`）
- `UserSelfUpdateDTO`：用户自助修改（`studentId`, `oldPassword`, `newPassword`）
- `AdminUserDTO`：管理员修改、新增用户（`studentId`, `password`, `userRole`）

### VO

- `UserAccountVO`：账户输出模型
  - 包含：`id`, `studentId`, `userRole`, `isActive`, `createTime`, `updateTime`
  - 不包含：`password`

## 7. 鉴权实现

- 注解：`@RequireRole(value = ..., dbCheck = false|true)`
- 拦截器：`TokenValidator`
- Token claim：`userId`, `role`, `issuedAt`
- 角色来源：
  - 默认：token claim `role`
  - 当 `dbCheck=true`：以数据库 `user_account.user_role` 为准复核

白名单接口：

- `/auth/register`
- `/auth/login`

### 7.1 `@RequireRole` 语义

- `value`：接口所需角色（支持数组）。
- `dbCheck`：
  - `false`（默认）：仅依赖 token 角色做权限判断，不查库。
  - `true`：在 token 权限通过后，再查库复核当前账号状态和角色。

### 7.2 角色继承

- `ADMIN` 自动拥有 `USER` 权限。
- 当接口要求 `USER` 时，`USER` 与 `ADMIN` 都可通过。

### 7.3 Token 生命周期（当前实现）

- 系统使用单 token（登录/注册返回 `token`）。
- 配置项：
  - `youngToken`：年轻期阈值（毫秒）。
  - `oldToken`：最终过期阈值（毫秒）。
- 请求时行为：
  - `tokenAge < youngToken`：正常通过，不回写。
  - `youngToken <= tokenAge < oldToken`：通过，并在响应头 `Authorization` 回写新 token。
  - `tokenAge >= oldToken`：返回 `TOKEN_EXPIRED`。
- 前端可选择是否消费响应头回写 token。

## 8. 数据模型约定

`user_account` 关键字段：

- `student_id`（唯一）
- `password`（哈希）
- `user_role`（`USER` / `ADMIN`）
- `is_active`（逻辑删除）

## 9. 错误码约定

- 认证失败：`TOKEN_INVALID` / `TOKEN_EXPIRED`
- 权限不足：`FORBIDDEN`
- 参数错误：`PARAM_ERROR`
- 用户冲突：`USER_DUPLICATED`
- 用户名或密码错误：`USERNAME_OR_PASSWORD_ERROR`

## 10. 当前策略总结

- 普通接口（如 `/user/me`）通常用 `@RequireRole(USER)`，默认不查库，优先性能。
- 管理接口（`/admin/**` 中需要 ADMIN 的接口）使用 `dbCheck=true`，优先权限实时性。
- 该策略在复杂度、性能和“降权/删号生效时效”之间做平衡：
  - 普通接口：接受 token 窗口。
  - 管理接口：通过 DB 复核消除权限窗口。
