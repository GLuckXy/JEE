# AI 模拟面试平台 · 用户与简历模块

课程设计项目中用户权限、简历解析确认、项目骨架和数据库整合部分的源代码，包含配套 Web 页面。

完整启动、接口和模块对接约定见 [交接文档](交接文档.md)。本仓库保留源码、构建配置、SQL 和测试源码，不附测试报告、截图或修改记录。

## 已提供功能

- 学生注册、登录退出、个人资料，管理员基础账号维护及权限校验。
- PDF、DOC、DOCX 私有上传，文本和规则解析，候选来源查看与人工校对。
- 草稿保存、并发修订校验、确认版本、历史查看、逻辑删除与鉴权下载。
- 已确认项目查询、选择和 JSON 资料下载，为训练模块提供项目快照。
- Spring Boot 后端骨架、Vue 前端骨架、MySQL 结构与迁移脚本。

正式模拟面试、项目深挖、题库、AI 评价和报告由对应模块继续接入。当前项目选择不会创建训练会话；规则解析结果仍需本人确认。

## 技术与环境

| 部分 | 技术与要求 |
| --- | --- |
| 后端 | JDK 21、Maven 3.9、Spring Boot 3.5、Spring Security、MyBatis-Plus |
| 数据库 | MySQL 8.0.16+；自动化测试使用 H2 |
| 文件解析 | PDFBox、Apache POI |
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia、Axios，推荐 Node.js 24 |

## 目录

| 路径 | 内容 |
| --- | --- |
| `src/main/` | 后端源码与配置 |
| `src/test/` | 后端测试源码与 H2 配置 |
| `frontend/` | 前端源码、构建配置与浏览器测试源码 |
| `db/mysql/` | 数据库结构与迁移脚本 |
| `scripts/` | 本机配置、启动与开发辅助脚本 |
| `tests/http/` | HTTP 请求示例 |
| [交接文档.md](交接文档.md) | 启动、接口、数据库与跨模块接入说明 |

## 本地启动

先由数据库负责人核对目标环境。空库依次应用 `001_schema.sql`、`002_initial_data.sql`、`003_resume_soft_delete.sql`；已有库核对迁移状态，避免重复执行。应用启动不会自动建表。

在后端运行进程中设置以下环境变量，真实值不要写入源码或共享配置：

| 环境变量 | 用途 |
| --- | --- |
| `DB_URL` | MySQL JDBC 地址，默认本机 `ai_interview` |
| `DB_USERNAME` / `DB_PASSWORD` | 独立业务账号凭证 |
| `APP_JWT_SECRET` | 至少 32 字节的随机签名材料，必填 |
| `APP_STORAGE_ROOT` | 私有文件目录，默认 `./var/private-files` |
| `SERVER_PORT` | 后端端口，默认 `8080` |

在仓库根目录执行：

```powershell
mvn test
mvn package
java -jar target/ai-inter-backend-0.1.0-SNAPSHOT.jar
```

另开终端启动前端：

```powershell
cd frontend
npm ci
npm run dev
```

访问 `http://127.0.0.1:5173/`，自行注册学生账号。没有预置密码，管理员需通过交接文档中的显式引导流程初始化。

前端默认代理后端 `8080`。后端端口不同时，在前端当前终端设置 `API_TARGET` 后启动，例如：

```powershell
$env:API_TARGET = 'http://127.0.0.1:8081'
npm run dev
```

## 构建与验证

前端目录中可运行 `npm run build` 和 `npm run test:merge`。浏览器测试命令及数据写入范围见交接文档，真实接口测试须使用获准的联调环境。

生产环境托管 `frontend/dist/`，并将同源 `/api` 反向代理到后端；前端使用 Hash 路由。不要公开私有文件存储目录，不提交依赖目录、构建产物或本机凭证。
