# DocLeaf

> 轻量级 Spring Boot API 文档生成器 — 零注解 · 零侵入

DocLeaf 从 Spring 原生注解中自动提取 API 元数据，无需引入任何 Swagger 注解，启动应用即可生成 Markdown 格式的 API 文档。

## ✨ 特性

- **零注解**：无需添加任何 Swagger/OpenAPI 注解，直接复用 `@GetMapping`、`@RequestParam` 等 Spring 原生注解
- **零侵入**：作为一个 `ApplicationRunner` 组件，在 Spring 容器启动后自动执行，不改变应用行为
- **零额外依赖**：仅依赖 `spring-boot-starter-web`
- **自动识别**：支持 `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping` 等快捷注解
- **参数解析**：自动区分 `@PathVariable`、`@RequestParam`、`@RequestBody`，标注是否必填
- **泛型支持**：正确解析 `ResponseEntity<User>`、`List<Order>` 等泛型返回类型
- **Markdown 输出**：按 Controller 分组，生成结构清晰的 API 文档表格

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

### 示例项目

本项目内置了 `UserController` 和 `OrderController` 两个示例 Controller，启动后即可看到生成的文档效果。

## 📁 项目结构

```
DocLeaf/
├── pom.xml
├── API_DOCUMENTATION.md          # 自动生成的 API 文档
└── src/main/java/com/docleaf/
    ├── DocLeafApplication.java   # 主启动类
    ├── DocLeafDocGenerator.java  # 核心文档生成器
    ├── model/
    │   ├── ApiInfo.java          # API 接口信息模型
    │   └── ApiParamInfo.java     # API 参数信息模型
    └── demo/                     # 示例 Controller
        ├── UserController.java
        ├── OrderController.java
        └── entity/
            ├── User.java
            └── Order.java
```

## 🔧 工作原理

1. `DocLeafDocGenerator` 实现 `ApplicationRunner`，在 Spring 容器启动完成后自动执行
2. 注入 `RequestMappingHandlerMapping`，调用 `getHandlerMethods()` 获取所有接口映射
3. 遍历 `Map<RequestMappingInfo, HandlerMethod>`，提取路径、HTTP 方法、参数、返回值等信息
4. 按 Controller 分组，生成 Markdown 格式文档并写入 `user.dir/API_DOCUMENTATION.md`

## 📄 许可证

MIT
