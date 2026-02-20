账户与权限设计
目标
•
统一账号管理与 RBAC，满足 USER/ADMIN 双角色。
•
业务规则收敛到 UserServiceImpl，Controller 只负责路由与参数转发。
角色模型
1.
USER
2.
ADMIN（继承 USER 权限）
权限规则
1.
注册默认创建 USER 账号。
2.
USER 可查看自己的账号信息。
3.
USER 可修改自己的账号信息（学号、密码）。
4.
USER 可删除自己的账号。
5.
USER 不可查看他人账号，不可 list 全部账号。
6.
ADMIN 可查看自己与他人账号。
7.
ADMIN 可 list 全部账号。
8.
ADMIN 可创建 USER/ADMIN 账号。
9.
ADMIN 可修改 USER 账号（学号、密码、角色）。
10.
ADMIN 不可修改其他 ADMIN 账号，但可修改自己。
11.
ADMIN 可删除 USER 账号。
12.
ADMIN 不可删除任何 ADMIN 账号（包括自己）。
RBAC 实现约定
1.
注解驱动：@RequireRole(...)。
2.
校验入口：TokenValidator。
3.
角色继承：当接口要求 USER 时，ADMIN 也应通过。
4.
token 中携带 userId 和 role，角色来源于 user_account.user_role。
控制器设计
1.
AuthController（匿名可访问）
•
POST /auth/register
•
POST /auth/login
2.
UserController（类级 @RequireRole(USER)）
•
GET /user/me
•
PATCH /user/me
•
DELETE /user/me
3.
AdminController（现有管理控制器，保留原系统配置/场次接口）
•
新增用户管理接口（类级或方法级 @RequireRole(ADMIN)）：
◦
GET /admin/users
◦
GET /admin/users/{id}
◦
POST /admin/users
◦
PATCH /admin/users/{id}
◦
DELETE /admin/users/{id}
服务层设计（单类）
•
统一放在 UserServiceImpl（实现 IUserService），按分组组织方法：
1.
认证
•
register(studentId, password)（强制 role=USER）
•
login(studentId, password)
•
getRole(userId)
2.
用户自助
•
getMe(userId)
•
updateMe(userId, dto)
•
deleteMe(userId)
3.
管理员用户管理
•
listUsers()
•
getUser(userId)
•
adminCreateUser(dto)
•
adminUpdateUser(operatorId, targetId, dto)
•
adminDeleteUser(operatorId, targetId)
4.
私有守卫方法（建议）
•
requireActiveUser(id)
•
assertCanAdminUpdate(operator, target)
•
assertCanAdminDelete(target)
•
applyPasswordIfPresent(entity, rawPassword)
数据模型
•
user_account
◦
id
◦
student_id（唯一）
◦
password（hash）
◦
user_role（USER/ADMIN）
◦
is_active（逻辑删除）
◦
create_time / update_time
DTO / VO 建议
1.
DTO
•
AuthRegisterDTO：studentId, password
•
AuthLoginDTO：studentId, password
•
UserSelfUpdateDTO：studentId?, oldPassword?, newPassword?
•
AdminCreateUserDTO：`Reconnecting... 1/5
设计目标
•
保持轻量 RBAC，不引入 Spring Security。
•
角色只有 USER、ADMIN，权限规则清晰可控。
•
Controller 职责单一，业务规则集中在 UserServiceImpl。
角色与继承
1.
USER
2.
ADMIN（继承 USER 权限）
•
规则：当接口要求 USER 时，ADMIN 也允许访问。
账户业务规则
1.
注册默认创建 USER。
2.
USER 只能查看/修改/删除自己账户。
3.
ADMIN 可创建 USER/ADMIN 账户。
4.
ADMIN 可查看所有账户，也可查看自己。
5.
ADMIN 可修改 USER 账户（学号、密码、角色）。
6.
ADMIN 不可修改其他 ADMIN，但可修改自己。
7.
ADMIN 可删除 USER 账户。
8.
ADMIN 不可删除任何 ADMIN（包括自己）。
Controller 设计
1.
AuthController（匿名访问）
•
POST /auth/register
•
POST /auth/login
2.
UserController（类级 @RequireRole(USER)）
•
GET /user/me
•
PATCH /user/me
•
DELETE /user/me
3.
AdminController（类级 @RequireRole(USER) 或按方法标注）
•
现有系统配置/场次管理接口保持不变
•
新增账户管理接口（方法级 @RequireRole(ADMIN)）：
◦
GET /admin/users
◦
GET /admin/users/{id}
◦
POST /admin/users
◦
PATCH /admin/users/{id}
◦
DELETE /admin/users/{id}
Service 设计（单类）
•
统一在 UserServiceImpl，按方法分组，不拆多个实现类。
1.
认证相关
•
register(studentId, password)
•
login(studentId, password)
•
getRole(userId)
2.
用户自助
•
getMe(userId)
•
updateMe(userId, dto)
•
deleteMe(userId)
3.
管理员管理
•
listUsers()
•
getUser(userId)
•
adminCreateUser(dto)
•
adminUpdateUser(operatorId, targetId, dto)
•
adminDeleteUser(operatorId, targetId)
4.
私有守卫方法（建议）
•
requireActiveUser(userId)
•
assertCanAdminUpdate(operator, target)
•
assertCanAdminDelete(target)
•
applyPasswordIfPresent(entity, rawPassword)
数据模型
•
user_account
◦
id
◦
student_id（唯一）
◦
password（哈希存储）
◦
user_role（USER/ADMIN）
◦
is_active（逻辑删除）
◦
create_time/update_time
•
初始化数据可包含默认 root 管理员。
DTO / VO 设计
1.
DTO
•
AuthRegisterDTO { studentId, password }
•
AuthLoginDTO { studentId, password }
•
UserSelfUpdateDTO { studentId?, oldPassword?, newPassword? }
•
AdminCreateUserDTO { studentId, password, userRole }
•
AdminUpdateUserDTO { studentId?, password?, userRole? }
2.
VO
•
UserAccountVO { id, studentId, userRole, isActive, createTime, updateTime }
•
不返回 password。
鉴权实现
1.
JWT claim 带 userId、role。
2.
TokenValidator 在拦截器中：
•
验证 token
•
读取 @RequireRole
•
执行角色匹配（含 ADMIN -> USER 继承）
3.
匿名白名单：
•
登录、注册、文档等接口。
错误码建议（沿用现有）
•
未登录/Token 失效：TOKEN_INVALID / TOKEN_EXPIRED
•
权限不足：FORBIDDEN
•
参数错误：PARAM_ERROR
•
用户冲突：USER_DUPLICATED
•
用户名或密码错误：USERNAME_OR_PASSWORD_ERROR
落地顺序
1.
先稳定 AuthController（登录/注册迁移）。
2.
再收敛 UserController 到 /me 三个接口。
3.
最后在 AdminController 增加 /admin/users*。
4.
同步补 DTO/VO 与 IUserService 方法签名。
5.
逐条验证规则边界（尤其 admin 修改/删除 admin 的限制）。