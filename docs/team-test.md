# 本机启动与验收

准备 JDK 21+、Maven 3.9、PowerShell 7 和 Node.js 20.19+ 或 22.12+。以下命令从仓库根目录执行。

## 安装前端依赖

首次检出项目或更新 `frontend/package-lock.json` 后，运行：

```powershell
npm.cmd --prefix frontend ci
```

npm 根据锁文件安装 Vue、Vite 等依赖到 `frontend/node_modules`。安装完成后即可启动 **Mianxu Vite** 或构建整站。

## 本机 H2 验收

```powershell
pwsh -NoProfile -File scripts/start-test.ps1
```

打开 `http://127.0.0.1:8080/`。脚本构建并启动包含前端的 JAR，使用内存 H2 和内置题库。可加 `-Port 8081` 指定端口，已有构建时可加 `-UseExistingBuild`。H2 数据随进程退出清空，上传文件保存在当前运行专用的 `var/demo/` 子目录。

## 本机 MySQL 启动

将[本机配置模板](../backend/config/local/application.example.yml)复制为同目录的 `application.yml`，填写已迁移的本机 MySQL 数据库地址、账号、密码和 JWT 签名材料。JDBC URL 中的库名填实际使用的库名。需要阿里云语音或 DeepSeek 时，按[外部服务配置](integration.md#可选外部服务)填写相应配置。

在 IDEA 直接运行 `InterviewApplication.main()`，或按[发布包启动步骤](../README.md#初版发布包)用 `scripts/start-local.ps1 -Product` 启动已构建的整站 JAR。空库的建表和管理员初始化见[项目 README](../README.md#环境与运行)。

## IDEA 与 HTTP 冒烟测试

1. 在 IDEA 打开仓库根目录并导入 `backend/pom.xml`。运行 **Mianxu Demo** 使用 H2，或运行 `InterviewApplication.main()` 使用上面的本机 MySQL 配置。工作目录保持为仓库根目录。
2. 安装前端依赖后启动 **Mianxu Vite**，或运行 `npm.cmd --prefix frontend run dev`。页面地址为 `http://127.0.0.1:5173/`，API 默认转发到 `http://127.0.0.1:8080`。
3. 在 IDEA 打开 [HTTP 冒烟用例](../backend/tests/http/smoke.http)，依次运行注册、登录、读取资料、权限检查、登出和注销令牌检查。登录请求自动保存响应中的 JWT。
