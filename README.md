# AI 模拟面试平台 · 面序

一个 Spring Boot 服务、一套 MySQL 表、一个 [面序前端](frontend/README.md) 和一套 JWT 登录态。学生可管理简历和项目、查看典型示范、开展普通训练和专项训练或正式模拟，并在报告中回顾作答与进入复练。管理员维护公共内容和评价规则，不读取学生私有资料。

使用 Spring Boot 3.5、Spring Security、MyBatis-Plus 3.5.12、MySQL 8、PDFBox 和 Apache POI，Java 源码目标版本为 21；前端使用 Vue 3、Element Plus 和 Axios。

初版可把 Vue 与 API 部署在同一个 Spring Boot 进程。开发时仍可在 `frontend` 执行 `npm ci`、`npm run dev`，使用 `http://127.0.0.1:5173/`。跨模块接口、可选服务和回归命令见 [整合运行与验收](docs/integration.md)。

## 初版发布包

在本机准备 JDK 21+、Maven 3.9、PowerShell 7 和 Node.js 20.19+ 或 22.12+。新检出且未安装前端依赖时运行 `npm ci --prefix frontend`，再执行：

```powershell
pwsh -NoProfile -File scripts/build-product.ps1
pwsh -NoProfile -File scripts/start-local.ps1 -Product
```

发布包为 `target/mianxu-product.jar`；运行只需 Java、已迁移的 MySQL 和已有应用凭据，不需同时运行 Vite。启动脚本只监听 `127.0.0.1`。如已有数据库但没有本机加密配置，先通过启动进程环境提供 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 和 `APP_JWT_SECRET`，使用业务账号而非 `root`；也可执行 `java -jar target/mianxu-product.jar --server.address=127.0.0.1`。不要运行空库配置脚本来重配现有库或覆盖凭据。

打开 `http://127.0.0.1:8080/`，管理员进入「内容与评价规则 → 岗位 → 导入测试题库」。请求会新增带「测试」标记的 2 个岗位、4 个分类、8 道题和 2 份示范草稿；测试题可立即作答，示范在「典型示范」页审核后发布。同名测试岗位跳过，管理员可停用测试岗位。学生注册后在「普通训练」选择测试岗位，结束后前往「报告与历史」，在逐题回顾中试用「练习同类题」。服务不会在启动时导入数据。

## 原模块交接

保留[用户与简历模块原交接文档](交接文档.md)。该文档记录的是整合前的接口和运行方式；初版启动及完整迁移顺序以本页和[整合运行与验收](docs/integration.md)为准。

## 项目结构

| 目录 | 内容 |
| --- | --- |
| `src/main/java/` | 主应用、JWT 与简历、内容库、训练与语音、正式模拟、报告及适配器 |
| `src/main/resources/` | Spring Boot 配置 |
| `src/test/` | 后端自动化测试与 H2 测试结构 |
| `frontend/src/` | Vue 页面、公共组件、路由、认证状态与接口封装 |
| `frontend/tests/` | 合并规则测试、模拟接口浏览器测试及真实后端联调脚本 |
| `db/mysql/` | 由数据库负责人审核执行的结构与迁移脚本 |
| `scripts/` | Windows 本机配置、启动与开发辅助脚本 |
| `docs/` | 接口、数据库、运维、验收与交接说明 |

## 环境与运行

需要 JDK 21 或更新版本、Maven 3.9、MySQL 8.0.16 或更新版本。建议团队统一使用 JDK 21 LTS。

1. 由数据库负责人创建空数据库 `ai_interview` 及独立应用账号。
2. **获得数据库负责人确认后**，空库按顺序执行 `db/mysql/001`–`007`。已有用户与简历数据的库先确认 `001`–`003` 已部署，只续跑 `004`–`007`；若 `003` 尚未应用，须先单独应用一次。应用启动不自动迁移、不导入题库样例或账号。
3. 在运行进程环境中提供以下配置，不要将真实值提交到代码或写进命令历史：

| 环境变量 | 含义 |
| --- | --- |
| `DB_URL` | JDBC MySQL URL，默认连接本机 `ai_interview`；生产连接需按环境启用 TLS |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库凭证 |
| `APP_JWT_SECRET` | 至少 32 字节的随机签名材料；必填，无默认值 |
| `APP_STORAGE_ROOT` | 私有存储目录，默认 `./var/private-files`；禁止配置为公开静态目录 |

开发者可通过 IDE 的运行环境配置填写。程序不会生成或修改 `.env`。

```powershell
mvn test
mvn package
java -jar target/ai-inter-backend-0.1.0-SNAPSHOT.jar
```

自动化测试默认使用 H2 内存库，使用独立测试签名材料，私有夹具保存在 `target/test-private-files`。仅显式配置时才连接 MySQL 测试库。首次构建需要下载 Maven 项目依赖，不安装全局依赖。

## Windows 本机数据库配置

数据库负责人确认建库建表后，可在 PowerShell 7 中执行：

```powershell
pwsh -NoProfile -File scripts/setup-local-database.ps1
pwsh -NoProfile -File scripts/start-local.ps1
```

配置脚本交互读取本机 MySQL `root` 密码，在 `127.0.0.1:3306` 创建
`ai_interview` 数据库和同名独立应用账号，依次执行 `001`–`007`，并检查 21 张应用表与基础校验约束。
应用账号仅拥有该库的 SELECT、INSERT、UPDATE、DELETE 权限。检测到已有同名数据库、账号或本机凭证时会停止，不覆盖现有对象。

随机应用密码与 JWT 签名材料使用 Windows DPAPI 加密，保存在已被 Git 忽略的
`var/local-database.clixml`，只能由同一台机器上的同一 Windows 用户解密；root 密码不保存。
该文件是本机开发凭证，不应提交或分享。配置中途失败时保留凭证与已创建对象，需先核对实际状态，禁止直接重跑或删除。
启动脚本将配置传入 Java 子进程，不修改 `.env` 或系统环境变量。
默认端口为 8080，可用 `-Port 8081` 更改；构建产物需先通过 `mvn package` 生成。
运行中的 JAR 被 Windows 占用时，可用 `mvn package -Dapp.jar.name=ai-inter-backend-local` 生成独立新版，再用启动脚本 `-JarName ai-inter-backend-local.jar` 指定。
配置脚本也支持 `-PasswordDialog` 使用本机密码窗口输入。

已有本机 `ai_interview` 时，不要运行空库配置脚本。数据库负责人确认后，先检查再应用：

```powershell
pwsh -NoProfile -File scripts/upgrade-existing-database.ps1
pwsh -NoProfile -File scripts/upgrade-existing-database.ps1 -Apply
```

升级脚本以本机 MySQL 管理员凭据核对基础表、排序规则和 `003`，仅在 `004`–`007` 对应表全部不存在时新增 14 张表。应用前在 Git 忽略的 `var/migration-backups/` 保存基础表**结构**快照，不复制业务数据、不初始化账号或题库。MySQL DDL 不是整批事务；若检测到部分表已存在，先人工核对，不盲目重跑。密码由本机安全输入读取，不保存；也可加 `-PasswordDialog` 使用本机窗口。

### IDEA 运行与调试

在 IDEA 运行 `InterviewApplication` 只启动后端 API（默认 `8080`），不启动网页。JUnit 和 IDEA 的 HTTP Client 测试也不会保留可供访问的页面。若要调试整站：

1. 运行 `InterviewApplication`，等 Run 控制台出现 `Started InterviewApplication`，不要启用 `test` profile。
2. 在 IDEA Terminal（项目根目录）执行 `npm.cmd --prefix frontend run dev`。仅在依赖未安装时先执行 `npm.cmd --prefix frontend ci`。
3. 保持两个进程运行，打开 `http://127.0.0.1:5173/`。若后端不在 8080，先给前端终端设置 `$env:API_TARGET = 'http://127.0.0.1:实际端口'`。若想直接访问 8080 的网页，运行上面的发布 JAR，而不是 IDEA 的源码运行配置。

PowerShell 启动脚本的环境变量仅传给其 Java 子进程，IDEA 的 Application / Spring Boot 配置不会自动继承。使用同一 Windows 用户在项目目录执行：

```powershell
pwsh -NoProfile -File scripts/copy-idea-environment.ps1
```

脚本解密已有本机配置，将 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`APP_JWT_SECRET`、`SERVER_PORT`、`APP_STORAGE_ROOT` 复制到剪贴板，不输出凭证、不生成新密钥、不修改 `.env`。
进入 IDEA 的 Run → Edit Configurations，选中 `InterviewApplication`，通过 Modify options 显示 Environment variables，在环境变量输入框粘贴整行并保存。
这些值包含真实凭证，运行配置应仅保存在本机，不勾选 Store as project file；粘贴后复制普通文字覆盖剪贴板。不要把配置截图或上传。
如果控制台启动的后端还在运行，先在原终端按 Ctrl+C；也可通过脚本 `-Port 8081` 配置另一个端口。
该方式适用于 Application / Spring Boot 启动；自动化测试仍按原有 test profile 使用 H2。

## 管理员初始化

不提供默认管理员、默认密码、公开提权接口或 SQL 明文密码。首次初始化前，在**运行进程环境**提供 `BOOTSTRAP_ADMIN_ACCOUNT`、`BOOTSTRAP_ADMIN_PASSWORD`，账号规则与注册一致，再显式运行：

```powershell
java -jar target/ai-inter-backend-0.1.0-SNAPSHOT.jar --bootstrap-admin
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
