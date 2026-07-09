# DocLeaf

> 轻量级 Spring Boot API 文档生成器 — 零注解 · 零侵入

DocLeaf 从 Spring 原生注解中自动提取 API 元数据，无需引入任何 Swagger 注解，启动应用即可生成 Markdown、OpenAPI JSON 和交互式 HTML 文档。

## ✨ 特性

### 阶段一（MVP）

- **零注解**：无需添加任何 Swagger/OpenAPI 注解，直接复用 `@GetMapping`、`@RequestParam` 等 Spring 原生注解
- **零侵入**：作为一个 `ApplicationRunner` 组件，在 Spring 容器启动后自动执行，不改变应用行为
- **零额外依赖**：仅依赖 `spring-boot-starter-web`
- **自动识别**：支持 `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping` 等快捷注解
- **参数解析**：自动区分 `@PathVariable`、`@RequestParam`、`@RequestBody`，标注是否必填
- **泛型支持**：正确解析 `ResponseEntity<User>`、`List<Order>` 等泛型返回类型
- **Markdown 输出**：按 Controller 分组，生成结构清晰的 API 文档表格

### 阶段二（JavaDoc 提取）

- **源文件解析**：自动定位 `.java` 源文件，提取方法上方紧邻的 JavaDoc 注释
- **首句提取**：以句号、空行或 `@tag` 为结尾截取首句作为接口描述
- **参数说明**：解析 `@param` 标签，自动填充参数描述
- **返回值说明**：解析 `@return` 标签，补充返回值描述
- **类级描述**：提取 Controller 类的 JavaDoc 作为分组描述
- **重载支持**：通过"方法名 + 参数类型列表"精确定位，避免重载混淆
- **性能优化**：按类缓存解析结果，避免重复 I/O
- **优雅降级**：源文件不存在时（如 JAR 部署）自动回退为方法名

### 阶段三（OpenAPI + HTML 预览）

- **OpenAPI 3.0 JSON**：生成标准 OpenAPI 规范文件，可导入 Postman、Apifox 等工具
- **交互式 HTML 预览**：生成自包含的 HTML 文件，左侧菜单 + 右侧详情，纯原生实现
- **多格式输出**：通过配置自由选择输出 Markdown / OpenAPI / HTML
- **可配置输出目录**：支持自定义文档输出位置

## 🚀 快速开始

### 环境要求

- Java 8+
- Maven 3.6+
- Spring Boot 2.5+

### 构建 & 运行

```bash
# 编译打包
mvn clean package -DskipTests

# 运行应用（启动后自动生成文档）
java -jar target/docleaf-0.1.0.jar
```

应用启动后，在项目根目录下会自动生成三种格式的文档：

```
✅ DocLeaf 文档已生成：/path/to/API_DOCUMENTATION.md
✅ DocLeaf 文档已生成：/path/to/openapi.json
✅ DocLeaf 文档已生成：/path/to/docleaf-index.html
```

### 查看 HTML 预览

HTML 文件已内嵌完整的 OpenAPI JSON 数据，**双击 `outdoc/docleaf-index.html` 即可直接在浏览器中打开**，无需任何 HTTP 服务器。

> **注意**：JavaDoc 提取功能需要源文件存在（即在项目目录下运行），JAR 部署时会自动降级为方法名。

## ⚙️ 配置

在 `application.properties` 中可配置以下选项：

| 配置项                       | 默认值                     | 说明                                    |
| ------------------------- | ----------------------- | ------------------------------------- |
| `docleaf.source-root`     | `src/main/java`         | 源码根目录（相对于 user.dir），用于 JavaDoc 提取     |
| `docleaf.javadoc.enabled` | `true`                  | 是否启用 JavaDoc 注释提取                     |
| `docleaf.output.dir`      | `outdoc`                | 输出目录（相对于 user.dir）                    |
| `docleaf.output.formats`  | `markdown,openapi,html` | 输出格式（逗号分隔，可选：markdown, openapi, html） |

### 配置示例

```properties
# 只输出 OpenAPI 和 HTML
docleaf.output.formats=openapi,html

# 输出到 docs 目录
docleaf.output.dir=docs
```

## 📁 项目结构

```
DocLeaf/
├── pom.xml
├── API_DOCUMENTATION.md              # Markdown 格式文档（自动生成）
├── openapi.json                      # OpenAPI 3.0 JSON（自动生成）
├── docleaf-index.html                # HTML 预览页面（自动生成）
└── src/main/java/com/docleaf/
    ├── DocLeafApplication.java       # 主启动类
    ├── DocLeafDocGenerator.java      # 核心文档生成器（集成三种输出）
    ├── config/
    │   └── DocLeafProperties.java    # 配置属性类
    ├── model/
    │   ├── ApiInfo.java              # API 接口信息模型
    │   └── ApiParamInfo.java         # API 参数信息模型
    ├── javadoc/
    │   ├── JavaDocExtractor.java     # JavaDoc 注释提取器
    │   ├── ClassJavaDoc.java         # 类级 JavaDoc 数据模型
    │   └── MethodJavaDoc.java        # 方法级 JavaDoc 数据模型
    ├── generator/
    │   ├── OpenApiGenerator.java     # OpenAPI 3.0 JSON 生成器
    │   └── HtmlPreviewGenerator.java # HTML 预览页面生成器
    └── demo/                         # 示例 Controller
        ├── UserController.java
        ├── OrderController.java
        └── entity/
            ├── User.java
            └── Order.java
```

## 🔧 技术选型说明

### OpenAPI 生成：手写 Map + Jackson 序列化

选择手写 Map + Jackson 序列化而非引入 swagger-core，理由如下：

1. **零额外依赖**：Jackson 已通过 `spring-boot-starter-web` 传递依赖
2. **完全控制**：不受 swagger-core 版本兼容性影响，完全控制输出结构
3. **更轻量**：符合 DocLeaf "零额外依赖" 核心理念

### HTML 预览：纯原生实现

选择纯原生 CSS + JavaScript 而非 Swagger UI CDN，理由如下：

1. **离线可用**：不依赖外部 CDN，适合内网环境
2. **极简**：约 200 行代码，易于理解和定制
3. **自包含**：单个 HTML 文件，便于分享和部署

## 📄 许可证

MIT
