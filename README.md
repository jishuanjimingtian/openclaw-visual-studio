# 驭爪 (Clawhelm) - 开发指南

> **驭爪 / Clawhelm** 是 OpenClaw 的本地桌面控制台（对话、部署、模型、知识库、监控等）。驭为驾驭，Helm 为舵轮——在本机掌舵 OpenClaw。原名 OpenClaw VS 已弃用。

## 项目结构
```
openclaw-visual-studio/
├── frontend/                 # Electron + Vue 3 前端
│   ├── src/
│   │   ├── main/            # Electron 主进程
│   │   ├── renderer/        # Vue 渲染进程
│   │   ├── preload/         # 预加载脚本
│   │   └── shared/          # 共享类型定义
│   ├── tests/               # 前端测试
│   └── resources/           # 图标等资源
├── backend/                  # Spring Boot 后端服务
│   ├── src/main/java/com/openclaw/vs/
│   │   ├── config/          # 配置类
│   │   ├── controller/      # REST 控制器
│   │   ├── service/         # 业务逻辑
│   │   ├── repository/      # 数据访问层
│   │   ├── model/           # 实体类
│   │   ├── dto/             # 数据传输对象
│   │   └── gateway/         # Gateway 连接管理
│   └── src/test/            # 后端测试
├── docs/                     # 项目文档
└── scripts/                  # 部署脚本
```

## 技术栈
- **前端**: Electron 33 + Vue 3 + TypeScript + Naive UI
- **后端**: Spring Boot 3 + Java 21 + H2/PostgreSQL
- **测试**: Vitest (单元) + Playwright (E2E) + JUnit 5 (后端)
- **构建**: Vite + Maven
- **数据库**: H2 (开发/桌面) + Flyway 迁移

## 快速开始

### 1. 环境准备
```bash
# 安装 Node.js 20+ 和 Java 21
node --version
java --version

# 安装依赖
npm install
```

### 2. 开发模式
```bash
# 启动前端开发服务器 (Electron + Vue)
npm run dev

# 启动后端 Spring Boot（dev profile：H2 文件库 + Swagger）
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. 构建打包（Windows 桌面安装版）

**完整版**（默认，内置 JRE，普通用户免装 Java）：

```bash
npm run package:win
```

**轻量版**（不内置 JRE，安装包更小；目标机器需已安装 Java 17+）：

```bash
npm run package:win:lite
```

安装包输出：
- 完整版：`out/Clawhelm-0.1.0-setup.exe`
- 轻量版：`out-lite/Clawhelm-0.1.0-setup.exe`

用户安装完整版后可从桌面快捷方式「驭爪」直接打开，无需单独安装 Java。

可选环境变量：

| 变量 | 作用 |
|------|------|
| `PACK_CLEAN=1` | 打包前执行 `mvn clean package`（默认增量 `mvn package`） |
| `PACK_SMOKE=1` | 打包前对 `desktop` profile 做后端健康检查 |
| `PACK_LITE=1` | 同 `package:win:lite`，跳过 JRE |

其他平台：
```bash
npm run package:mac
npm run package:linux
```

打包环境要求：Node.js 20+、Maven 3.8+。已配置国内镜像加速 Electron 与 JRE 下载。

### 3.1 自动更新与发版

打包后的桌面客户端会在启动约 4 秒后检查更新；发现新版本时弹窗，用户确认后下载，点「稍后」则仅跳过该版本，下一版本会再次提醒。设置页「关于」中可手动 **检查更新**。

**发布到 GitHub Releases（默认）**

更新源默认指向：[jishuanjimingtian/openclaw-visual-studio](https://github.com/jishuanjimingtian/openclaw-visual-studio) Releases。  
打包时可通过 `UPDATE_GITHUB_OWNER` / `UPDATE_GITHUB_REPO` 覆盖。

1. 在 `package.json` 中 bump `version`。
2. 在 GitHub 仓库 **Settings → Secrets → Actions** 配置 `GH_TOKEN`（对 `jishuanjimingtian/openclaw-visual-studio` 有 `repo` 权限的 PAT）。
3. 本地发布（需已打包）：

```powershell
$env:GH_TOKEN = "<token>"
$env:PACK_PUBLISH = "1"
npm run package:win
```

或推送标签触发 [`.github/workflows/release.yml`](.github/workflows/release.yml)：

```bash
git tag v0.1.0
git push origin v0.1.0
```

Release 资产需包含 `latest.yml`、`Clawhelm-x.y.z-setup.exe` 及 `.blockmap`，供 `electron-updater` 使用。  
客户端检查地址示例：`https://github.com/jishuanjimingtian/openclaw-visual-studio/releases/latest/download/latest.yml`

**发布到自建 CDN（可选）**

```bash
$env:UPDATE_GENERIC_URL="https://your-cdn.example.com/clawhelm/"
$env:PACK_PUBLISH="1"
npm run package:win
```

将 `out/` 中的 `latest.yml`、安装包与 blockmap 同步到该 URL 根目录。可通过 `UPDATE_GITHUB_OWNER` / `UPDATE_GITHUB_REPO` 覆盖默认 GitHub 仓库。

开发模式（`npm run dev`）不检查更新。

### 4. 测试
```bash
# 前端单元测试
npm run test

# 前端 E2E 测试
npm run test:e2e

# 后端测试
cd backend && mvn test
```

## 自动化测试策略

### 前端测试
- **单元测试**: Vitest + Vue Test Utils
  - 组件渲染测试
  - 状态管理测试 (Pinia)
  - 路由测试
- **E2E 测试**: Playwright
  - 用户流程测试
  - 跨页面导航
  - 数据持久化

### 后端测试
- **单元测试**: JUnit 5 + Mockito
- **集成测试**: @SpringBootTest
- **API 测试**: MockMvc
- **数据库测试**: H2 内存数据库

### 测试覆盖率
- 前端: `npm run test:coverage` (Vitest + v8)
- 后端: `mvn test jacoco:report` (Jacoco)

## 数据库迁移
使用 Flyway 管理数据库版本：
```sql
-- 新增迁移文件: backend/src/main/resources/db/migration/V2__add_new_table.sql
-- 运行迁移: mvn flyway:migrate
```

## 部署配置

后端使用 Spring Profile 区分环境（配置文件位于 `backend/src/main/resources/`）：

| Profile | 用途 | 数据库 | 说明 |
|---------|------|--------|------|
| `dev` | 本地开发 | H2 文件 `./data/openclaw_vs` | H2 Console、Swagger 开启 |
| `desktop` | 桌面安装包 | H2 文件（用户目录） | Electron 启动时注入路径；关闭 Swagger/H2 Console |
| `test` | 单元/集成测试 | H2 内存 | Maven Surefire 默认启用，Flyway 关闭 |

### 开发环境

- 启动：`cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev`
- 数据库：`./data/openclaw_vs.mv.db`
- 后端端口：8089（context-path `/api/v1`）
- 前端端口：5173

### 桌面生产（打包）

- 打包：`npm run package:win` 或 `npm run package:win:lite`
- 运行时 profile：`desktop`（由 [`frontend/src/main/backendManager.ts`](frontend/src/main/backendManager.ts) 注入）
- 数据目录：`app.getPath('userData')/appdata/`（含 H2、日志、部署工作区）
- 可选：设置 `APP_ENCRYPTION_KEY` 覆盖 API Key 加密密钥

独立 Spring Boot 服务端部署（PostgreSQL 等）尚未作为当前桌面版的交付目标。

## 开发规范

### 代码规范
- 前端: ESLint + Prettier
- 后端: Checkstyle + 统一格式化

### 提交信息
使用 Conventional Commits:
```
feat: 新增会话管理功能
fix: 修复模型配置 API 错误
docs: 更新 README
test: 添加对话服务测试
```

### 分支策略
- `main`: 生产稳定分支
- `develop`: 开发主分支
- `feature/*`: 功能分支
- `hotfix/*`: 紧急修复

## 常见问题

### 1. 前端启动失败
- 检查 Node.js 版本 (≥20)
- 删除 `node_modules` 重新安装
- 检查端口占用 (5173, 8089)

### 2. 后端启动失败
- 检查 Java 版本 (≥21)
- 检查 Maven 依赖
- 查看 H2 数据库文件权限

### 3. Gateway 连接失败
- 确保 OpenClaw Gateway 服务运行 (端口 18789)
- 检查防火墙设置
- 查看后端日志中的连接错误

## 下一步开发计划

### M1 里程碑 (第 1-2 周)
- [x] 项目脚手架搭建
- [x] 基础 UI 框架 (侧边栏 + 仪表盘)
- [x] 后端 REST API 基础
- [ ] 会话管理完整实现
- [ ] 模型配置管理
- [ ] Gateway 连接集成

### M2 里程碑 (第 3-4 周)
- [ ] 工作流可视化编排
- [ ] Skill 市场集成
- [ ] 环境部署引擎
- [ ] 系统监控面板

## 贡献指南
1. Fork 项目仓库
2. 创建功能分支 (`feature/your-feature`)
3. 提交代码并添加测试
4. 创建 Pull Request
5. 等待代码审查

## 许可证
MIT License
