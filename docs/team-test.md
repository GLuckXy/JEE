# 本地验收与团队联调

从仓库根目录运行以下命令。

| 模式 | 运行配置 | 启动命令 |
| --- | --- | --- |
| 本地验收 | 独立 H2、内置测试题、规则评分 | `pwsh -NoProfile -File scripts/start-test.ps1` |
| 团队联调 | 本机 MySQL 测试库、阿里云语音、DeepSeek 正式模拟 | `pwsh -NoProfile -File scripts/start-test.ps1 -Mode TeamTest` |

两种模式都监听 `http://127.0.0.1:8080/`，可加 `-Port 8081` 切换端口。已有对应构建时可加 `-UseExistingBuild`。本地验收的数据随进程退出清空，上传文件保存在当前运行专用的 `var/demo/` 子目录。

## 配置团队测试环境

1. 启动本机 MySQL 8.0.16+，创建独立空库 `ai_interview_integration_test`，字符集用 `utf8mb4`，排序规则用 `utf8mb4_0900_ai_ci`。每位测试员可以用自己的端口，库名须以 `_test` 结尾。
2. 把团队提供的脱敏 SQL 快照导入这份空库。快照应包含当前版本的表结构和所需样例数据，导出时省略 `CREATE DATABASE`、`USE` 和数据库用户。用本机 MySQL 管理员登录，选中测试库后执行 `SOURCE C:/path/to/team-test-snapshot.sql`。只需统一表结构时，可改为在空库按顺序执行 `backend/db/mysql/001`–`007`。
3. 为该测试库创建本机专用应用账号，授予 `SELECT`、`INSERT`、`UPDATE` 和 `DELETE`。复制 [团队配置模板](../backend/config/team-test/application.example.yml) 为同目录的 `application.yml`。填入本机数据库账号、至少 32 个 UTF-8 字节的本机 JWT 签名材料，以及团队渠道发放的阿里云和 DeepSeek 凭据。数据库 URL 使用模板中的 `127.0.0.1`，按实际情况填写端口和库名。
4. 运行团队联调命令。应用启动时跳过数据库初始化。配置以明文保存在 `backend/config/team-test/application.yml`，由 Git 忽略。改动配置后重启应用。

每位测试员用自己的 MySQL 副本和测试账号。简历原件和录音保存在本机 `var/team-test/private-files/`，导入 SQL 只会恢复数据库中的业务记录。需要验证上传或回放时，在自己电脑上重新上传合成资料。完成正式模拟验收还需准备 FFmpeg。

如果测试库还没有教学题目，由测试管理员在「内容与评价规则」中导入。空库需要先按[管理员初始化说明](../README.md#管理员初始化)创建测试管理员。执行初始化时使用默认 Spring 配置，并通过 `--spring.config.additional-location=file:./backend/config/team-test/application.yml` 指向这份本机测试库。

本机 MySQL 使用 [本机配置模板](../backend/config/local/application.example.yml)。将其复制为同目录的 `application.yml`，填入现有本机 MySQL 应用凭据，再用 `scripts/start-local.ps1` 启动。原来的 `var/local-database.clixml` 可留作旧配置备份。

## IDEA 与 HTTP 冒烟测试

1. 在 IDEA 打开仓库根目录并导入 `backend/pom.xml`。选择 `Mianxu Demo` 或 `Mianxu Team Test` 运行或调试，工作目录保持为仓库根目录。
2. 首次执行 `npm.cmd --prefix frontend ci`。启动 `Mianxu Vite`，访问 `http://127.0.0.1:5173/`。IDEA 未提供 npm 运行配置时，在终端运行 `npm.cmd --prefix frontend run dev`。
3. 在 IDEA 打开 [HTTP 冒烟用例](../backend/tests/http/smoke.http)，依次运行注册、登录、读取资料、权限检查、登出和注销令牌检查。登录请求自动保存响应中的 JWT。
