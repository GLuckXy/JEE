# 数据库与跨模块整合

正式数据库目标为 MySQL 8.0.16+。使用 MyBatis-Plus BaseMapper 和参数化 QueryWrapper/UpdateWrapper，不使用 JPA 或手写 JDBC。分页排序片段仅拼接经严格范围校验的整数。

## 脚本

`backend/db/mysql/001_schema.sql` 创建七张表、唯一约束、外键、CHECK 与查询索引，仅面向空 schema。重复执行会报表已存在。`002_initial_data.sql` 说明初始化策略。管理员通过显式引导创建。

`003_resume_soft_delete.sql` 为 resume 添加可空 `deleted_at` 和本人有效简历列表索引。已有数据库只应用 003；空库依次应用 001/002/003。执行需要 ALTER/INDEX 权限，应用业务账号没有此权限。迁移不删除记录或原文件；执行后再启动新版后端。该脚本只执行一次，部分失败时先检查字段和索引状态，不直接重跑。

MySQL 模式配置 `spring.sql.init.mode=never`。`backend/src/main/resources/schema-h2.sql` 用于 H2 测试与本地验收，使用兼容 H2 的表选项和字段类型。

## 本机 MySQL 配置与验证（2026-11-14）

经用户确认，已在本机 MySQL 8.0.36 的 `127.0.0.1:3306` 创建 `ai_interview` 数据库，使用 `utf8mb4_0900_ai_ci`，按顺序执行两份 SQL。已验证七张表、九个 CHECK 约束的元数据及应用账号登录。

独立账号 `ai_interview`@`localhost` 仅授予该库 SELECT、INSERT、UPDATE、DELETE 权限；root 不用于后端连接。随机应用密码和 JWT 签名材料通过 Windows DPAPI 加密保存在 Git 已忽略的 `var/local-database.clixml`，root 密码未保存。连接参数将数据库会话时区固定为 UTC。

使用 `scripts/start-local.ps1` 已成功启动后端并建立真实 MySQL JDBC 连接。向登录接口提交不存在的探测账号返回 HTTP 401 / `INVALID_CREDENTIALS`，验证了业务查询链路，未创建探测用户或管理员。现有 H2 自动化测试 26 项全部通过；尚未执行真实 MySQL 的完整注册、文件上传、事务及并发回归，也未通过非法写入验证 CHECK 的拒绝行为。

## 表与关联

| 表 | 说明 |
| --- | --- |
| sys_user | 账号 8–16 位且唯一，BCrypt、角色、状态、个人资料、更新人和时间 |
| auth_session | 主键为 JWT jti；用户关联，失效与撤销时间，不保存完整令牌 |
| file_object | 本人私有文件，业务关联、随机存储键、类型、大小、补偿状态 |
| resume | 当前原始解析结果、草稿、解析/确认状态、最近确认版本和编辑修订号 |
| resume_version | 每次确认的不可变 JSON 载荷，resume_id + version 唯一 |
| resume_project | 项目按确认版本保存，复合外键确保 version_id 与 resume_id 一致 |
| user_audit_log | 管理员账号维护审计，不记录变更后的个人信息或密钥 |
| operation_log | 业务操作日志：操作人、动作、目标与 UTC 时间；管理员分页查询，不记录密钥或个人资料载荷 |

主键使用服务端生成的 UUID 字符串，统一 VARCHAR(36)。时间使用 UTC，所有数据库连接与外部模块应采用相同约定。账号在应用层统一转小写，正式数据库同时采用大小写不敏感的排序规则。

resume 不重复保存 confirmed_payload，改由 resume_version 保存并通过 resume_id + version 定位当前确认载荷，避免维护两份权威数据。项目不重复存用户 ID，归属经 resume 检查。文件采用 biz_type=RESUME、biz_id=resume.id 的业务关联；简历通过外键指向原文件，所有授权读取同时校验反向关联。

`version` 是确认版本号，`revision` 是客户端提交校验号，两者用途不同。确认通过 SELECT FOR UPDATE 串行处理同一简历，并在同一事务插入版本、项目和更新当前状态；任一步失败全部回滚。历史项目不会删除或原地修改。

## 其他模块约定

当前未提供其他模块数据库设计，因此本交付不创建面试、题库或报告表。

- 业务模块使用安全上下文中的用户 ID 和角色，不接受前端 userId 作为鉴权依据。
- 训练模块关联本模块用户 ID、简历 ID、项目 ID、确认版本号，并自行保存完整项目快照。
- 新确认版本会生成新项目 ID；旧 ID 不再通过当前项目选择接口提供。已有训练使用自己的快照。
- 文件模块如后续统一，保留 owner_id、biz_type、biz_id 的授权语义，不公开 storage_key。
- 用户删除、外键级联、保留时长与历史快照匿名化未确认，初版无删除接口和自动清理任务。

数据库负责人整合时应审阅表名、统一 ID 类型、时区和权限规范，再将该脚本并入团队迁移顺序。实际 MySQL 联调结果应单独记录，不以 H2 测试替代。
