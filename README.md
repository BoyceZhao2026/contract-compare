# 在线合同智能比对系统

一个基于Vue 3 + Spring Boot的在线合同文档比对工具，支持上传两个Word文档并高亮显示差异。

## 技术栈

### 前端
- Vue 3 (Composition API + TypeScript)
- Vite
- Element Plus
- mammoth.js (Word文档解析)
- diff-match-patch (差异算法)
- axios (HTTP客户端)

### 后端
- Java 11
- Spring Boot 2.7.18
- MyBatis Plus 3.5.3.1
- MySQL 5.7+
- Hutool工具库

## 功能特性

- 📁 支持 .docx 格式文件上传（最大50MB）
- 🔍 智能文本差异比对（忽略纯格式变化）
- 🎨 差异高亮显示（删除/新增/修改）
- 📜 左右分栏同步滚动
- 📊 操作日志记录
- 🎯 响应式设计，支持移动端

## 项目结构

```
contract-compare/
├── backend/                    # 后端Spring Boot项目
│   ├── src/main/java/com/contract/diff/
│   │   ├── controller/         # 控制器
│   │   ├── service/           # 服务层
│   │   ├── mapper/            # 数据访问层
│   │   ├── entity/            # 实体类
│   │   ├── common/            # 通用类
│   │   └── config/            # 配置类
│   ├── src/main/resources/
│   │   ├── application.yml    # 应用配置
│   │   └── sql/schema.sql     # 数据库脚本
│   └── pom.xml               # Maven配置
├── frontend/                  # 前端Vue3项目
│   ├── src/
│   │   ├── components/        # Vue组件
│   │   ├── views/            # 页面视图
│   │   ├── types/            # TypeScript类型定义
│   │   └── main.ts           # 应用入口
│   ├── package.json          # NPM配置
│   └── vite.config.ts        # Vite配置
└── README.md                 # 项目说明
```

## 快速开始

### 1. 环境准备

- Java 11+
- Node.js 16+
- MySQL 5.7+
- Maven 3.6+

### 2. 数据库配置

创建数据库并导入表结构：

```sql
-- 执行 backend/src/main/resources/sql/schema.sql
```

修改后端数据库配置 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/contract_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

### 3. 后端启动

```bash
# 进入后端目录
cd backend

# 编译运行
mvn spring-boot:run

# 或者使用IDE运行 ContractDiffApplication.java
```

后端服务将在 http://localhost:8080 启动

### 4. 前端启动

```bash
# 进入前端目录
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端服务将在 http://localhost:5173 启动

### 5. 访问应用

打开浏览器访问 http://localhost:5173 即可使用合同比对功能。

## API接口

### 文件上传
- `POST /api/contract/upload`
- 参数：file (MultipartFile)
- 返回：文件路径和基本信息

### 文件下载
- `GET /api/contract/file/stream?path={filePath}`
- 参数：path (文件相对路径)
- 返回：文件二进制流

### 比对记录
- `POST /api/contract/record`
- 参数：ComparisonRecord (JSON)
- 返回：操作结果

### 健康检查
- `GET /api/contract/health`
- 返回：服务状态

## 配置说明

### 文件存储路径

在 `application.yml` 中配置文件存储根目录：

```yaml
app:
  storage:
    location: /data/files/contract_uploads/  # Linux示例
    # location: D:/data/files/contract_uploads/  # Windows示例
```

确保应用有该目录的读写权限。

### 前端代理

开发环境已配置Vite代理，将 `/api` 请求转发到后端 `http://localhost:8080`。

## 部署注意事项

1. **文件存储目录权限**：确保Spring Boot应用对配置的存储目录有读写权限
2. **路径安全**：后端已实现路径遍历防护，但仍建议定期检查
3. **文件清理**：建议添加定时任务清理超过30天的临时文件
4. **防火墙**：生产环境建议配置防火墙规则，限制文件下载接口的访问频率

## 开发说明

### 前端组件

- `ContractCompare.vue`：主页面组件，包含文件上传和比对逻辑
- `DiffViewer.vue`：差异展示组件，负责解析文档和显示差异

### 后端核心类

- `ContractController`：REST API控制器
- `FileService`：文件存储和读取服务
- `ComparisonRecord`：比对日志实体类

## 许可证

本项目仅供学习和研究使用。