# AI 模拟面试平台 · 面序

一个 Spring Boot 服务、一套 MySQL 表、一个 [面序前端](frontend/README.md) 和一套 JWT 登录态。学生可管理简历和项目、查看典型示范、开展普通训练和专项训练或正式模拟，并在报告中回顾作答与进入复练。管理员维护公共内容和评价规则，不读取学生私有资料。

使用 Spring Boot 3.5、Spring Security、MyBatis-Plus 3.5.12、MySQL 8、PDFBox 和 Apache POI，Java 源码目标版本为 21；前端使用 Vue 3、Element Plus 和 Axios。

## 本机快速启动

在 Windows 上准备 JDK 21+、Maven 3.9、PowerShell 7 和 Node.js 20.19+ 或 22.12+。首次检出项目后，在仓库根目录安装前端依赖：

```powershell
npm.cmd --prefix frontend ci
```

然后运行本机验收脚本：

```powershell
pwsh -NoProfile -File scripts/start-test.ps1
```

打开 `http://127.0.0.1:8080/`，注册测试账号后可上传简历、做文字训练、查看报告和复练。本机验收使用内存 H2 和内置题库。使用 MySQL 时，按[本机数据库配置](#windows-本机数据库配置)填写凭据。 [本机启动与验收](docs/team-test.md)

## 初版发布包

在本机准备 JDK 21+、Maven 3.9、PowerShell 7 和 Node.js 20.19+ 或 22.12+。首次构建前在仓库根目录运行 `npm.cmd --prefix frontend ci`，再执行：

```powershell
pwsh -NoProfile -File scripts/build-product.ps1
pwsh -NoProfile -File scripts/start-local.ps1 -Product
```

发布包为 `backend/target/mianxu-product.jar`。启动脚本监听 `127.0.0.1`，从 `backend/config/local/application.yml` 或启动环境加载已迁移的 MySQL 数据库凭据。

打开 `http://127.0.0.1:8080/`，管理员进入「内容与评价规则 → 岗位 → 导入测试题库」。请求会新增带「测试」标记的 2 个岗位、4 个分类、8 道题和 2 份示范草稿；测试题可立即作答，示范在「典型示范」页审核后发布。同名测试岗位跳过，管理员可停用测试岗位。学生注册后在「普通训练」选择测试岗位，结束后前往「报告与历史」，在逐题回顾中试用「练习同类题」。服务不会在启动时导入数据。

## 原模块交接

保留[用户与简历模块原交接文档](交接文档.md)。该文档记录的是整合前的接口和运行方式；初版启动及完整迁移顺序以本页和[整合运行与验收](docs/integration.md)为准。

## 项目结构

| 目录 | 内容 |
| --- | --- |
| `backend/src/main/java/com/aiinterview/` | 统一包名下的认证、用户、简历、题库、训练、语音、正式模拟和报告 |
| `backend/src/main/resources/` | Spring Boot 配置、H2 结构和教学样例 |
| `backend/src/test/`、`backend/tests/http/` | 后端自动化测试和 IDEA HTTP 冒烟用例 |
| `backend/config/` | 本机配置模板及 Git 忽略的本机配置位置 |
| `frontend/src/` | Vue 页面、公共组件、路由、认证状态与接口封装 |
| `frontend/tests/` | 合并规则测试、模拟接口浏览器测试及真实后端联调脚本 |
| `backend/db/mysql/`、`backend/scripts/` | MySQL 迁移及后端工具 |
| `scripts/` | 整站构建与启动 |
| `docs/` | 接口、数据库、运维、验收与交接说明 |

## 环境与运行

需要 JDK 21 或更新版本、Maven 3.9、MySQL 8.0.16 或更新版本。建议团队统一使用 JDK 21 LTS。

1. 由数据库负责人创建空数据库及独立应用账号。库名通过 JDBC URL 指定，默认示例为 `ai_interview`。
2. **获得数据库负责人确认后**，空库按顺序执行 `backend/db/mysql/001`–`008`。已有用户与简历数据的库先确认 `001`–`003` 已部署，再执行尚未应用的迁移。MySQL 模式启动时跳过迁移和教学样例初始化。
3. 将 `backend/config/local/application.example.yml` 复制为同目录的 `application.yml` 并填写凭据。也可以通过运行进程环境提供以下配置。

| 环境变量 | 含义 |
| --- | --- |
| `DB_URL` | JDBC MySQL URL，默认连接本机 `ai_interview`；生产连接需按环境启用 TLS |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库凭证 |
| `APP_JWT_SECRET` | 至少 32 字节的随机签名材料；必填，无默认值 |
| `APP_STORAGE_ROOT` | 私有存储目录，默认 `./var/private-files`；禁止配置为公开静态目录 |

下面的命令从仓库根目录执行。

```powershell
mvn -f backend/pom.xml test
mvn -f backend/pom.xml package
java -jar backend/target/ai-inter-backend-0.1.0-SNAPSHOT.jar
```

自动化测试默认使用 H2 内存库和独立签名材料，私有夹具保存在 `backend/target/test-private-files`。仅显式配置时才连接 MySQL 测试库。

## Windows 本机数据库配置

数据库负责人确认建库建表后，可在 PowerShell 7 中执行：

```powershell
pwsh -NoProfile -File backend/scripts/setup-local-database.ps1
pwsh -NoProfile -File scripts/start-local.ps1
```

配置脚本交互读取本机 MySQL `root` 密码，在 `127.0.0.1:3306` 创建
`ai_interview` 数据库和同名独立应用账号，依次执行 `001`–`008`，并检查 22 张应用表与基础校验约束。
应用账号仅拥有该库的 SELECT、INSERT、UPDATE、DELETE 权限。检测到已有同名数据库、账号或本机凭证时会停止，不覆盖现有对象。

配置脚本将生成的应用密码和 JWT 签名材料写到 Git 忽略的 `backend/config/local/application.yml`。配置中途失败时先核对已创建的数据库对象和本机配置。
默认端口是 8080，可用 `-Port 8081` 更改。运行中的 JAR 被占用时，可用 `mvn -f backend/pom.xml package -Dapp.jar.name=ai-inter-backend-local` 生成独立新版，再用启动脚本 `-JarName ai-inter-backend-local.jar` 指定。
配置脚本也支持 `-PasswordDialog` 使用本机密码窗口输入。

已有本机 `ai_interview` 时，不要运行空库配置脚本。数据库负责人确认后，先检查再应用：

```powershell
pwsh -NoProfile -File backend/scripts/upgrade-existing-database.ps1
pwsh -NoProfile -File backend/scripts/upgrade-existing-database.ps1 -Apply
```

升级脚本以本机 MySQL 管理员凭据核对基础表、排序规则和 `003`，仅在 `004`–`008` 对应表全部不存在时新增 15 张表。应用前在 Git 忽略的 `backend/var/migration-backups/` 保存基础表**结构**快照。若检测到部分表已存在，先人工核对。也可加 `-PasswordDialog` 使用本机窗口输入密码。

### IDEA 运行与调试

1. 在 IDEA 打开仓库根目录，右键 `backend/pom.xml`，选择 **Add as Maven Project**。
2. 在仓库根目录执行 `npm.cmd --prefix frontend ci`，按锁文件安装到 `frontend/node_modules`。首次检出或锁文件更新后执行这一步。
3. 后端可运行 **Mianxu Demo** 使用内存 H2，也可从仓库根目录直接运行 `InterviewApplication.main()`，读取 `backend/config/local/application.yml` 连接本机 MySQL。
4. 启动 **Mianxu Vite**，或在仓库根目录执行 `npm.cmd --prefix frontend run dev`。打开 `http://127.0.0.1:5173/`。 [详细步骤](docs/team-test.md#idea-与-http-冒烟测试)

## 管理员初始化

不提供默认管理员、默认密码、公开提权接口或 SQL 明文密码。首次初始化前，在**运行进程环境**提供 `BOOTSTRAP_ADMIN_ACCOUNT`、`BOOTSTRAP_ADMIN_PASSWORD`，账号规则与注册一致，再显式运行：

```powershell
java -jar backend/target/ai-inter-backend-0.1.0-SNAPSHOT.jar --bootstrap-admin
```

命令初始化新管理员并继续启动应用。已有账号会拒绝创建，不会提升已有学生账号权限。初始化后，后续启动去掉该参数，不再向运行进程提供初始化凭证。初版只允许管理员维护学生账号状态；不支持修改管理员状态、密码重置或角色变更。

## 默认业务规则

- 账号为 8–16 位英文、数字或下划线；统一转为小写保存与查询，大小写不敏感。
- 密码至少 8 字符，包含英文和数字，UTF-8 最多 72 字节，使用 BCrypt 哈希。
- JWT 默认有效期 2 小时，无刷新令牌；退出撤销当前会话，账号禁用后现有令牌下次请求即失效。
- 单份简历默认最大 10 MiB，支持 PDF、DOC、DOCX；必须提交对应 MIME。
- 解析成功仅表示文本已提取，所有结果仍需人工核对；无 OCR，不调用外部 AI。
- 确认至少包含一个项目，每项的名称和职责必填；其余经历和项目字段允许留空。
- 修改草稿后回到待确认，重新确认产生新版本；历史确认载荷和项目不覆盖。
- 项目选择返回当前确认版本的快照，训练模块负责将其保存到自己的会话。
- 管理员账号权限不包含学生私有简历、项目或原文件。

## 可配置参数

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP 端口 |
| `APP_TOKEN_TTL` | `PT2H` | ISO-8601 令牌时长 |
| `APP_ALLOWED_EXTENSIONS` | `pdf,doc,docx` | 扩展名白名单；实际类型仍交叉校验 |
| `APP_MAX_UPLOAD_BYTES` | `10485760` | 应用级上传字节限制 |
| `APP_MAX_FILE_SIZE` | `10MB` | Servlet 单文件限制 |
| `APP_MAX_REQUEST_SIZE` | `11MB` | Servlet 请求限制，须大于单文件限制 |
| `APP_MAX_TEXT_CHARS` | `200000` | 文本字符上限；PDF 最多 100 页 |
| `API_DOCS_ENABLED` | `false` | 启用 `/v3/api-docs` 和 `/swagger-ui/index.html`；仅建议开发环境启用 |

修改上传上限时应同时调整三项大小配置。DOCX 展开后的内容最多 40 MiB、2000 个 ZIP 项，POI 还会执行自身的压缩比安全检查。

## 联调与验收

- [接口说明与示例](docs/api.md)
- [数据库和模块整合约定](docs/database.md)
- [存储补偿、解析及运维说明](docs/operations.md)
- [跨模块验收与回归](docs/integration.md)

初版采用同步解析，适合课程项目及低并发开发联调。生产扩展可替换 `ResumeParser` 与私有存储组件，并接入异步任务、OCR 和统一文件服务。解析器仅做章节与词项提取，不保证自动识别出完整项目结构。
