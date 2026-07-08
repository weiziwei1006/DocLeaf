# DocLeaf

> 轻量级 Spring Boot API 文档生成器 — 零注解 · 零侵入

DocLeaf 从 Spring 原生注解中自动提取 API 元数据，无需引入任何 Swagger 注解，启动应用即可生成 Markdown 格式的 API 文档。阶段二新增从 Java 源文件提取 JavaDoc 注释，自动填充接口描述、参数说明和返回值说明。

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

## 🚀 快速开始

### 环境要求

- Java 8+
- Maven 3.6+
- Spring Boot 2.7.x

### 构建 & 运行

```bash
# 编译打包
mvn clean package -DskipTests

# 运行应用（启动后自动生成文档）
java -jar target/docleaf-0.1.0.jar
```

应用启动后，在项目根目录下会自动生成 `API_DOCUMENTATION.md` 文件，控制台输出：

```
✅ DocLeaf 文档已生成：/path/to/API_DOCUMENTATION.md
```

> **注意**：JavaDoc 提取功能需要源文件存在（即在项目目录下运行），JAR 部署时会自动降级为方法名。

### 示例项目

本项目内置了 `UserController` 和 `OrderController` 两个示例 Controller，包含丰富的 JavaDoc 注释，启动后即可看到生成的文档效果。

## ⚙️ 配置

在 `application.properties` 中可配置以下选项：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `docleaf.source-root` | `src/main/java` | 源码根目录（相对于 user.dir），用于 JavaDoc 提取 |
| `docleaf.javadoc.enabled` | `true` | 是否启用 JavaDoc 注释提取 |

## 📁 项目结构

```
DocLeaf/
├── pom.xml
├── API_DOCUMENTATION.md              # 自动生成的 API 文档
└── src/main/java/com/docleaf/
    ├── DocLeafApplication.java       # 主启动类
    ├── DocLeafDocGenerator.java      # 核心文档生成器（集成 JavaDoc 提取）
    ├── model/
    │   ├── ApiInfo.java              # API 接口信息模型
    │   └── ApiParamInfo.java         # API 参数信息模型
    ├── javadoc/
    │   ├── JavaDocExtractor.java     # JavaDoc 注释提取器（阶段二核心）
    │   ├── ClassJavaDoc.java         # 类级 JavaDoc 数据模型
    │   └── MethodJavaDoc.java        # 方法级 JavaDoc 数据模型
    └── demo/                         # 示例 Controller
        ├── UserController.java
        ├── OrderController.java
        └── entity/
            ├── User.java
            └── Order.java
```

## 🔧 工作原理

### 阶段一：API 元数据提取

1. `DocLeafDocGenerator` 实现 `ApplicationRunner`，在 Spring 容器启动完成后自动执行
2. 注入 `RequestMappingHandlerMapping`，调用 `getHandlerMethods()` 获取所有接口映射
3. 遍历 `Map<RequestMappingInfo, HandlerMethod>`，提取路径、HTTP 方法、参数、返回值等信息
4. 按 Controller 分组，生成 Markdown 格式文档并写入 `user.dir/API_DOCUMENTATION.md`

### 阶段二：JavaDoc 注释提取

1. 根据类的全限定名，在源码根目录下定位 `.java` 源文件
2. 使用正则查找所有 `/** ... */` JavaDoc 块
3. 对每个块，向后查找方法声明（跳过注解），通过"方法名 + 参数类型"精确匹配
4. 提取首句描述（以句号、空行或 `@tag` 为结尾截断）
5. 解析 `@param` 和 `@return` 标签
6. 按类缓存解析结果，避免重复 I/O
7. 源文件不存在时优雅降级为方法名

## 📄 许可证

MIT
