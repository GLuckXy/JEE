# 用户与简历接口说明

## 通用约定

JSON UTF-8。除注册、登录外，所有业务接口必须携带 `Authorization: Bearer <accessToken>`；文件下载也须携带。身份从认证上下文取得，接口不接收 `userId` 作为授权依据。

成功返回 HTTP 200：

```json
{"code":"OK","message":"成功","data":{}}
```

失败返回相应 HTTP 状态，`data` 为 null。未知字段直接拒绝，避免角色、状态、密码哈希等批量绑定。资源不存在及访问他人资源统一返回 404，不暴露是否存在。日期时间字段使用 UTC，JSON 时间值不带偏移后缀，前端按 UTC 解读。

分页参数 `page=1&size=20`，page 范围 1–100000，size 范围 1–100。分页结果为 `total`、`page`、`size`、`records`。

## 认证与用户

| 方法 | 路径 | 请求与权限 |
| --- | --- | --- |
| POST | `/api/auth/register` | 公开；account、password、nickname；只能注册学生 |
| POST | `/api/auth/login` | 公开；account、password |
| POST | `/api/auth/logout` | 已认证；无请求体；撤销当前 JWT 会话 |
| GET | `/api/users/me` | 已认证；本人资料 |
| PUT | `/api/users/me` | 已认证；本人资料白名单，nickname 必填 |

注册示例：

```json
{"account":"student01","password":"ExampleOnly123","nickname":"祥"}
```

此处密码仅是请求格式示例，不存在预设账号。账号为 8–16 位英文、数字或下划线；密码至少 8 字符且有英文和数字，UTF-8 最多 72 字节。注册返回用户资料，不自动签发 JWT。

登录示例：

```json
{"account":"student01","password":"ExampleOnly123"}
```

登录 `data`：`accessToken`、`tokenType=Bearer`、`expiresIn`（秒）、`user`。不提供刷新，过期后重新登录。退出只使当前令牌失效。禁用账号拒绝现有令牌与新登录。

资料修改示例：

```json
{"nickname":"祥","email":"student@example.test","phone":null,"school":"测试学校","major":"计算机"}
```

PUT 为白名单字段完整更新，选填项未提供或为 null 时清空。用户返回值包括 id、account、role、status、nickname、email、phone、school、major、createdAt、updatedAt，不返回密码哈希。

## 简历与文件

| 方法 | 路径 | 请求与结果 |
| --- | --- | --- |
| POST | `/api/resumes` | 学生；multipart/form-data，字段 file；同步保存及解析后返回详情 |
| GET | `/api/resumes` | 学生；本人简历分页 |
| GET | `/api/resumes/{id}` | 学生；本人简历详情 |
| PUT | `/api/resumes/{id}/parsed-data` | 学生；expectedRevision、payload；保存草稿并回到 DRAFT |
| POST | `/api/resumes/{id}/confirm` | 学生；expectedRevision；原子生成确认版本与项目 |
| POST | `/api/resumes/{id}/retry-parse` | 学生；仅 DRAFT；无请求体；重试不覆盖手动保存的草稿 |
| GET | `/api/resumes/{id}/versions` | 学生；本人历史确认版本，版本号降序 |
| GET | `/api/resumes/{id}/projects` | 学生；本人当前已确认版本的项目 |
| GET | `/api/resume-projects/{id}` | 学生；本人当前已确认项目详情 |
| POST | `/api/resumes/{id}/select-project` | 学生；projectId、version；返回可供训练保存的项目快照 |
| GET | `/api/files/{id}` | 学生；本人简历原文件；二进制 attachment、no-store |

文件 MIME 必须与类型匹配：PDF 为 `application/pdf`，DOC 为 `application/msword`，DOCX 为 `application/vnd.openxmlformats-officedocument.wordprocessingml.document`。随机存储名与物理路径不对外提供。

简历详情的 `data` 包括：

- `summary`：id、fileId、originalName、parseStatus、confirmStatus、version、revision、failureReason、createdAt、confirmedAt。
- `rawParseResult`：原文 text、章节提取 extracted、parserVersion；新版 `sections-v2` 增加 evidence（field、source、confidence）和 warnings；旧记录可能没有这两项，仅本人可读。confidence 为规则匹配类型 MATCHED/REVIEW，不是准确率。
- `draft`：当前可编辑内容。
- `confirmedPayload`：最近一次确认内容，未确认过时为 null；是否当前可训练仍以 confirmStatus 为准。
- `allowedActions`：SAVE_DRAFT、CONFIRM，满足条件时包含 DOWNLOAD_FILE、RETRY_PARSE、SELECT_PROJECT。实际校验仍由后端执行。

保存草稿示例：

```json
{
  "expectedRevision":0,
  "payload":{
    "education":[{"organization":"测试大学","title":"计算机本科","period":"2023–2027","description":""}],
    "skills":["Java","MySQL"],
    "internships":[],
    "projects":[{
      "name":"课程管理平台",
      "goal":"完成课程设计",
      "responsibility":"本人负责用户与简历后端",
      "techStack":["Spring Boot","MyBatis-Plus"],
      "difficulty":"",
      "solution":"",
      "result":"",
      "improvement":""
    }]
  }
}
```

四个列表均需提供，可以为空；education/internships 每项使用 organization、title、period、description。项目可在草稿中不完整，确认时至少一个项目且每项 name、responsibility 非空。其他字段不要求补造。

保存草稿后 revision 从 0 变为 1，此时确认请求：

```json
{"expectedRevision":1}
```

确认成功 version 从 0 变为 1、revision 从 1 变为 2。客户端每次使用最新详情中的 revision，冲突时重新读取。重复确认不创建重复版本，需先保存新的草稿。新上传产生新的 resume ID，与原简历不互相覆盖。

选择项目请求：

```json
{"projectId":"<project-id>","version":1}
```

返回 resumeId、projectId、version、snapshot。snapshot 包含项目全部字段；训练模块把快照持久化到自己的会话，不持久依赖可变的当前简历。不存在持久化的“当前已选项目”全局状态。

## 管理员账号接口

| 方法 | 路径 | 请求与边界 |
| --- | --- | --- |
| GET | `/api/admin/users` | ADMIN；账号基础资料分页 |
| GET | `/api/admin/users/{id}` | ADMIN；账号基础详情 |
| PUT | `/api/admin/users/{id}` | ADMIN；仅 nickname，必填；审计记录更新人和时间 |
| PUT | `/api/admin/users/{id}/status` | ADMIN；`{"status":"DISABLED"}` 或 ACTIVE；仅可维护学生状态 |

不提供密码重置或角色变更。管理员不允许使用学生简历、项目和文件接口读取私有内容。

## 内部服务

```java
CurrentUser user = userAccessService.getCurrentUser();
ResumeViews.Version resume = resumeQueryService.getConfirmedResume(resumeId);
ResumeViews.Project project = resumeQueryService.getConfirmedProject(resumeId, projectId);
```

调用必须位于通过本项目安全过滤器认证的请求上下文；不允许客户端或异步任务自行构造 userId 替代认证。查询服务校验 STUDENT、本人归属、当前确认状态及项目所在确认版本，不能跨简历拼接项目。后台任务或微服务拆分需另行定义可信服务身份协议，不直接复用此服务为无鉴权接口。

## 状态与错误

解析状态 UPLOADED → PARSING → SUCCESS/FAILED。确认状态 DRAFT → CONFIRMED，编辑后返回 DRAFT。两个状态独立；FAILED 可经人工补充确认。解析处理中重试返回冲突；超过 5 分钟的 PARSING 可显式重试恢复。

| HTTP | 错误码 | 含义 |
| --- | --- | --- |
| 400 | VALIDATION_FAILED / INVALID_REQUEST | 格式、参数、未知字段或密码规则错误 |
| 401 | AUTH_REQUIRED / INVALID_CREDENTIALS | 无效、过期、撤销令牌或凭证错误 |
| 403 | ACCOUNT_DISABLED / PERMISSION_DENIED | 禁用或角色不足 |
| 400 | ADMIN_OPERATION_FORBIDDEN | 初版不允许修改管理员状态 |
| 409 | ACCOUNT_ALREADY_EXISTS | 重复账号 |
| 404 | USER_NOT_FOUND / RESUME_NOT_FOUND / PROJECT_NOT_FOUND / FILE_NOT_FOUND | 不存在或无权访问 |
| 400 | FILE_TYPE_INVALID | 扩展名、MIME、实际内容不符 |
| 413 | FILE_TOO_LARGE | 上传超限 |
| 503 | FILE_STORAGE_FAILED | 写入失败，进入补偿跟踪 |
| 409 | FILE_NOT_AVAILABLE | 原文件未保存完成，需重传 |
| 409 | RESUME_NOT_CONFIRMED / RESUME_ALREADY_CONFIRMED | 确认状态不允许当前操作 |
| 404 | PROJECT_NOT_CONFIRMED | 项目不属于该简历当前确认版本 |
| 409 | VERSION_CONFLICT / RESUME_PARSE_IN_PROGRESS | 乐观版本冲突或正在解析 |
| 409 | RESUME_BUSY | 文件写入或解析中，暂不能删除 |
| 400 | RESUME_CONFIRMATION_INVALID | 必要项目字段不完整 |
| 500 | INTERNAL_ERROR | 脱敏异常与参考号 |

自动解析失败时上传通常仍返回 200，详情内标记 FAILED 和脱敏原因；原文件可下载、草稿可编辑。这不是确认失败。同步解析失败不会伪装为上传文件丢失。

## 删除简历

`DELETE /api/resumes/{id}`，JSON 请求体 `{"expectedRevision":3}`，修订号来自最新详情或列表。仅学生本人可操作；成功返回 200 和 `data:null`。修订号过期返回 VERSION_CONFLICT，上传/解析中返回 RESUME_BUSY；不存在、已删除、他人简历返回 RESUME_NOT_FOUND。

采用 `deleted_at` 逻辑删除，列表不再展示；详情、版本、草稿、重试解析、确认、项目查询与选择、原文件下载以及内部简历查询均不可访问。原文件及历史数据保留，已保存到训练会话的独立快照保持有效。当前没有恢复或物理清理接口。需先执行 `003_resume_soft_delete.sql` 再启动新版后端。
