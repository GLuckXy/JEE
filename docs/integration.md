# 整合运行与验收

## 数据库与首次演示

- 新库按顺序执行 `db/mysql/001_schema.sql` 至 `007_report.sql`。已有基础表的库先确认 `003_resume_soft_delete.sql` 已执行；之后只执行 `004`–`007`。数据库默认字符集和排序规则使用 `utf8mb4` / `utf8mb4_0900_ai_ci`，与 `001` 中用户主键的外键规则一致。严禁执行来源模块的独立建库脚本，也不要把独立演示账号或旧报告表导入现有库。
- 首次启动不会自动导入题库。管理员可在 `#/admin/content` 创建自己的内容，或显式确认导入[教学样例](content-samples.json)。导入只新增带「测试」标记的岗位，启用其分类和题目，示范保留草稿；遇到同名岗位跳过，不修补或覆盖已有记录。这套测试题每个分类都有两道题，可验证同类复练。评价规则不配置时使用默认五维权重；管理员可按训练模式发布规则，新规则只作用于之后生成的报告。
- 学生使用同一个入口登录：先在 `#/demos` 观看示范，在 `#/training` 选题、作答并结束，然后从 `#/reports` 查看报告和发起专项复练。在 `#/resumes` 确认本人的简历项目后可进入 `#/formal`。正式模拟只接受录音，不提供文字替代；每次提交的首份录音不会被覆盖。

## 可选外部服务

只通过服务进程的环境提供配置，不把实际密钥放进仓库、聊天或前端。

| 环境变量 | 用途 |
| --- | --- |
| `TRAINING_SPEECH_PROVIDER=isi` | 选择阿里云语音服务，未选择时普通文字训练仍可用 |
| `TRAINING_ISI_ACCESS_KEY_ID`、`TRAINING_ISI_ACCESS_KEY_SECRET`、`TRAINING_ISI_APP_KEY` | 已有的阿里云语音凭据 |
| `TRAINING_ISI_REGION`、`TRAINING_FFMPEG_PATH` | 默认 `cn-shanghai`；FFmpeg 默认从 `PATH` 查找，也可传绝对路径 |
| `TRAINING_AUDIO_DIR` | 可选，普通训练音频默认在私有存储目录的 `training-audio` 下 |
| `INTERVIEW_AI_KEY`、`INTERVIEW_AI_URL`、`INTERVIEW_AI_MODEL` | 正式模拟真实 DeepSeek；默认接口路径 `/chat/completions`，默认模型 `deepseek-chat` |
| `APP_EVALUATION_AI_ENABLED`、`APP_EVALUATION_AI_BASE_URL`、`APP_EVALUATION_AI_API_KEY`、`APP_EVALUATION_AI_MODEL` | 可选的报告 AI 评价，不配置或调用失败时使用规则评分 |

正式模拟需要语音转写和真实模型同时可用；任一未配置时页面提示，服务调用失败时可重试。启用外部服务后，语音转写将处理录音，项目问答只传岗位描述、已确认的项目业务内容和当前项目回答，不传简历文件或账号信息。启用外部报告评分会额外提交相应的题目、参考要点和作答文本。

## 模块接口

| 入口 | 行为 |
| --- | --- |
| `/api/content`、`/api/admin/content` | 已登录用户浏览启用的岗位、题目与已发布示范；管理员管理公共内容 |
| `POST /api/admin/content/teaching-content` | 管理员显式安装内置测试题库，无请求体；返回新增的岗位、分类、题目、示范草稿和跳过岗位数 |
| `/api/training`、`/api/voice` | 普通/专项训练及私有音频；选题使用数值 `roleId`（内容岗位 ID）、`questionIds`，只接受服务器校验的启用题 |
| `/api/formal-interviews` | 正式模拟选项、开始/停止录音、上传、重试、结束；创建只收 `roleId` 和 `projectId` |
| `/api/reports/history`、`/api/reports/sessions/{id}` | 分页历史与报告详情，仅学生本人；结束后后台生成，可 `POST /api/reports/sessions/{id}/generate` 补生成或重试 |
| `POST /api/reports/sessions/{id}/retrain` | 请求体为该报告的 `{ "answerId": "…" }`；创建同类别专项训练，返回 `targetSessionId`，原题停用也不影响既有报告 |
| `/api/admin/evaluation-rules` | 规则新建、编辑、启停和发布；管理员无权查看学生报告或录音 |

JSON 接口沿用 `code`、`message`、`data`。音频和其他私有文件以 Bearer JWT 请求二进制资源，禁止作为公共静态文件发布。已结束报告的评分和作答快照不会随题库改动而改变；只有录音、无转写的答题保留状态但不计分。

## 回归命令

```powershell
mvn test
```

后端跨模块回归使用 H2；它以测试替身验证语音转写和项目提问，包括限时、转写重试、录音保留、项目追问、报告与权限。若要在预先迁移好的独立 MySQL 库复跑，向测试进程提供 `VERIFY_PLATFORM_MYSQL_URL`、`VERIFY_PLATFORM_MYSQL_USER`、`VERIFY_PLATFORM_MYSQL_PASSWORD`，指定本机 `ai_interview_integration_test`，再运行 `mvn -Dtest=PlatformIntegrationTest test`。不要在已有业务库运行。

已安装前端项目依赖和 Microsoft Edge 时，从项目根目录运行下面的浏览器验收。它启动独立 H2 后端和 Vite，测试管理员导入和发布、学生注册、训练、反馈、报告、复练及权限；不修改现有数据库。

```powershell
$env:VERIFY_PLATFORM_BROWSER = 'true'
mvn -Dtest=PlatformBrowserVerificationTest test
```

实际阿里云与 DeepSeek 需要在已配置的环境单独联调。自动化测试和本机隔离数据库验收不代表已验证真实云端服务。

## 上轮整合验收

- Maven 打包通过：52 项测试通过，原有 5 项依赖历史 MySQL 验收数据的可选测试未运行；新加入的完整浏览器流程通过。
- MySQL 8.0.36 的独立 `ai_interview_integration_test` 已通过 `001`–`007` 和 3 项跨模块回归。现有业务库未执行迁移。
- Vue 生产构建、5 项前端单测和 12 项已有页面状态检查通过。真实阿里云和 DeepSeek 本轮未验收。

## 初版发布验收

- 已对本机现有 `ai_interview` 核对 `003` 并续跑 `004`–`007`；迁移前保存基础表结构，新增 14 张表，未写入原有业务表。测试数据未写入该库。
- `scripts/build-product.ps1` 产出 `target/mianxu-product.jar`：52 项后端测试通过、6 项可选测试在默认构建中跳过；前端生产构建与 5 项单测通过。
- 发布 JAR 在独立 MySQL 8.0.36 的 `ai_interview_integration_test` 通过单地址浏览器回归：管理员导入测试题库、重复导入不新增、发布示范、学生注册与训练、暂停恢复、报告、同类复练和权限校验。实际阿里云与 DeepSeek 仍需单独配置和验收。
