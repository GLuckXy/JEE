# 本地验收与团队联调

从仓库根目录运行以下命令。

| 模式 | 运行配置 | 启动命令 |
| --- | --- | --- |
| 本地验收 | 独立 H2、内置测试题、规则评分 | `pwsh -NoProfile -File scripts/start-test.ps1` |
| 团队联调 | 共享 MySQL、阿里云语音、DeepSeek 正式模拟 | `pwsh -NoProfile -File scripts/start-test.ps1 -Mode TeamTest` |

两种模式都监听 `http://127.0.0.1:8080/`，可加 `-Port 8081` 切换端口。已有对应构建时可加 `-UseExistingBuild`。本地验收的数据随进程退出清空，上传文件保存在当前运行专用的 `var/demo/` 子目录。

## 配置团队测试环境

1. 复制 [团队配置模板](../backend/config/team-test/application.example.yml) 为同目录的 `application.yml`。填入团队渠道收到的 MySQL、阿里云和 DeepSeek 凭据，再填写分配给本机的 JWT 签名材料，至少 32 个 UTF-8 字节。将凭据填在 YAML 字符串值内，单引号字符串内的单引号写成两个单引号。
2. 数据库负责人须先完成共享测试库的迁移，并配置独立的测试账号。库名以 `_test` 结尾。连接串沿用模板的 `?sslMode=VERIFY_IDENTITY`，主机名须与证书匹配，证书颁发机构须受运行应用的 JDK 信任。
3. 运行团队联调命令。配置以明文保存在 `backend/config/team-test/application.yml`，Git 忽略该文件，打包只读取应用资源目录。更新凭据后重启应用。

使用专用测试账号、合成简历和录音。共享 MySQL 保存业务记录与录音引用，简历原件和录音保存在各机器的 `var/team-test/private-files/`。团队验收需准备 FFmpeg。先确认合成简历中的项目，再在正式模拟中检查录音识别、DeepSeek 追问、结束面试和报告。

本机 MySQL 使用 [本机配置模板](../backend/config/local/application.example.yml)。将其复制为同目录的 `application.yml`，填入现有本机 MySQL 应用凭据，再用 `scripts/start-local.ps1` 启动。原来的 `var/local-database.clixml` 可留作旧配置备份。

## IDEA 与 HTTP 冒烟测试

1. 在 IDEA 打开仓库根目录并导入 `backend/pom.xml`。选择 `Mianxu Demo` 或 `Mianxu Team Test` 运行或调试，工作目录保持为仓库根目录。
2. 首次执行 `npm.cmd --prefix frontend ci`。启动 `Mianxu Vite`，访问 `http://127.0.0.1:5173/`。IDEA 未提供 npm 运行配置时，在终端运行 `npm.cmd --prefix frontend run dev`。
3. 在 IDEA 打开 [HTTP 冒烟用例](../backend/tests/http/smoke.http)，依次运行注册、登录、读取资料、权限检查、登出和注销令牌检查。登录请求自动保存响应中的 JWT。对共享库执行前，使用自己专属的测试账号。
