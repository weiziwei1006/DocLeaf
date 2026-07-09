package com.docleaf.generator;

import com.docleaf.model.ApiInfo;
import com.docleaf.model.ApiParamInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI 3.0 JSON 生成器
 * <p>
 * 将 DocLeaf 提取的 API 元数据转换为 OpenAPI 3.0 规范的 JSON 文件。
 * <p>
 * <b>技术选型说明</b>：采用手写 Map + Jackson 序列化方式（而非引入 swagger-core），
 * 理因如下：
 * <ol>
 *   <li>Jackson 已通过 spring-boot-starter-web 传递依赖，零额外引入</li>
 *   <li>完全控制输出结构，不受 swagger-core 版本兼容性影响</li>
 *   <li>更轻量，符合 DocLeaf "零额外依赖" 理念</li>
 * </ol>
 *
 * @author DocLeaf
 */
public class OpenApiGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenApiGenerator.class);

    /** Jackson ObjectMapper（线程安全，复用实例） */
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** 输出文件名 */
    private static final String OUTPUT_FILE_NAME = "openapi.json";

    /**
     * 生成 OpenAPI 3.0 JSON 文件
     *
     * @param controllerApiMap 按 Controller 名归类的接口信息
     * @param outputDir        输出目录
     * @return 生成的文件路径，失败返回 null
     */
    public Path generate(Map<String, List<ApiInfo>> controllerApiMap, Path outputDir) {
        // 1. 构建 JSON 字符串
        String json = buildJson(controllerApiMap);
        if (json == null) {
            return null;
        }

        // 2. 写入文件
        Path outputPath = outputDir.resolve(OUTPUT_FILE_NAME);
        try {
            Files.createDirectories(outputDir);
            Files.write(outputPath, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("OpenAPI 文件写入失败: {}", e.getMessage(), e);
            return null;
        }

        log.info("OpenAPI JSON 已生成: {}", outputPath);
        return outputPath;
    }

    /**
     * 构建 OpenAPI 3.0 JSON 字符串（不写入文件）
     * <p>
     * 用于内嵌到 HTML 预览页面，避免 fetch 跨域问题。
     *
     * @param controllerApiMap 按 Controller 名归类的接口信息
     * @return JSON 字符串，失败返回 null
     */
    public String buildJson(Map<String, List<ApiInfo>> controllerApiMap) {
        Map<String, Object> openapi = buildOpenApiDocument(controllerApiMap);
        try {
            return objectMapper.writeValueAsString(openapi);
        } catch (Exception e) {
            log.error("OpenAPI JSON 序列化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将已构建的 JSON 字符串写入文件
     *
     * @param json      OpenAPI JSON 字符串
     * @param outputDir 输出目录
     * @return 生成的文件路径，失败返回 null
     */
    public Path writeJson(String json, Path outputDir) {
        if (json == null) {
            return null;
        }
        Path outputPath = outputDir.resolve(OUTPUT_FILE_NAME);
        try {
            Files.createDirectories(outputDir);
            Files.write(outputPath, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("OpenAPI 文件写入失败: {}", e.getMessage(), e);
            return null;
        }
        log.info("OpenAPI JSON 已生成: {}", outputPath);
        return outputPath;
    }

    // ========================================================================
    // OpenAPI 文档构建
    // ========================================================================

    /**
     * 构建完整的 OpenAPI 3.0 文档对象
     */
    private Map<String, Object> buildOpenApiDocument(Map<String, List<ApiInfo>> controllerApiMap) {
        Map<String, Object> root = new LinkedHashMap<>();

        // OpenAPI 版本
        root.put("openapi", "3.0.3");

        // info 块
        root.put("info", buildInfoBlock(controllerApiMap));

        // paths 块
        root.put("paths", buildPathsBlock(controllerApiMap));

        // tags 块（按 Controller 分组）
        root.put("tags", buildTagsBlock(controllerApiMap));

        return root;
    }

    /**
     * 构建 info 块
     */
    private Map<String, Object> buildInfoBlock(Map<String, List<ApiInfo>> controllerApiMap) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "DocLeaf API 文档");
        info.put("description", "由 DocLeaf 自动生成 — 零注解 · 零侵入");
        info.put("version", "1.0.0");

        // 统计接口数
        int totalApis = 0;
        for (List<ApiInfo> apis : controllerApiMap.values()) {
            totalApis += apis.size();
        }
        info.put("x-total-apis", totalApis);
        info.put("x-total-controllers", controllerApiMap.size());

        return info;
    }

    /**
     * 构建 tags 块（每个 Controller 一个 tag）
     */
    private List<Map<String, Object>> buildTagsBlock(Map<String, List<ApiInfo>> controllerApiMap) {
        List<Map<String, Object>> tags = new ArrayList<>();
        for (Map.Entry<String, List<ApiInfo>> entry : controllerApiMap.entrySet()) {
            Map<String, Object> tag = new LinkedHashMap<>();
            tag.put("name", entry.getKey());
            // 使用第一个接口的 Controller 描述
            String desc = entry.getValue().get(0).getControllerDescription();
            if (desc != null && !desc.isEmpty()) {
                tag.put("description", desc);
            }
            tags.add(tag);
        }
        return tags;
    }

    /**
     * 构建 paths 块
     * <p>
     * 结构示例：
     * <pre>
     * "/api/users/{id}": {
     *   "get": {
     *     "tags": ["UserController"],
     *     "summary": "根据 ID 获取用户信息。",
     *     "parameters": [...],
     *     "responses": {...}
     *   }
     * }
     * </pre>
     */
    private Map<String, Object> buildPathsBlock(Map<String, List<ApiInfo>> controllerApiMap) {
        Map<String, Object> paths = new LinkedHashMap<>();

        for (Map.Entry<String, List<ApiInfo>> entry : controllerApiMap.entrySet()) {
            String controllerName = entry.getKey();

            for (ApiInfo api : entry.getValue()) {
                // 每个路径生成一个 path item
                for (String path : api.getPaths()) {
                    // 将 Spring 路径变量 {id} 转换为 OpenAPI 格式 {id}（格式相同，无需转换）
                    String openApiPath = convertPath(path);

                    // 获取或创建 path item
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pathItem = (Map<String, Object>) paths.computeIfAbsent(
                            openApiPath, k -> new LinkedHashMap<>());

                    // 为每个 HTTP 方法生成 operation
                    for (String httpMethod : api.getHttpMethods()) {
                        String method = httpMethod.toLowerCase();
                        if ("all".equals(method)) {
                            method = "get"; // ALL 降级为 GET
                        }
                        pathItem.put(method, buildOperation(controllerName, api));
                    }
                }
            }
        }

        return paths;
    }

    /**
     * 构建单个 operation（接口操作）
     */
    private Map<String, Object> buildOperation(String controllerName, ApiInfo api) {
        Map<String, Object> operation = new LinkedHashMap<>();

        // tags
        operation.put("tags", new ArrayList<>(java.util.Collections.singletonList(controllerName)));

        // summary（接口描述）
        operation.put("summary", api.getDescription() != null ? api.getDescription() : api.getMethodName());

        // operationId（方法名）
        operation.put("operationId", api.getMethodName());

        // parameters（路径参数和查询参数）
        List<Map<String, Object>> parameters = buildParameters(api.getParams());
        if (!parameters.isEmpty()) {
            operation.put("parameters", parameters);
        }

        // requestBody（如果有 @RequestBody 参数）
        Map<String, Object> requestBody = buildRequestBody(api.getParams());
        if (requestBody != null) {
            operation.put("requestBody", requestBody);
        }

        // responses
        operation.put("responses", buildResponses(api));

        return operation;
    }

    /**
     * 构建 parameters 数组
     * <p>
     * 包含 PathVariable（in: path）和 RequestParam（in: query）和 RequestHeader（in: header）
     */
    private List<Map<String, Object>> buildParameters(List<ApiParamInfo> params) {
        List<Map<String, Object>> parameters = new ArrayList<>();

        for (ApiParamInfo param : params) {
            String in = resolveParamLocation(param.getSource());
            if (in == null) {
                continue; // RequestBody 不作为 parameter
            }

            Map<String, Object> parameter = new LinkedHashMap<>();
            parameter.put("name", param.getName());
            parameter.put("in", in);
            parameter.put("required", param.isRequired());
            if (param.getDescription() != null && !param.getDescription().isEmpty()) {
                parameter.put("description", param.getDescription());
            }

            // schema
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", javaTypeToOpenApiType(param.getType()));
            if (param.getDefaultValue() != null) {
                schema.put("default", param.getDefaultValue());
            }
            parameter.put("schema", schema);

            parameters.add(parameter);
        }

        return parameters;
    }

    /**
     * 构建 requestBody
     *
     * @return requestBody 对象，如果没有 @RequestBody 参数则返回 null
     */
    private Map<String, Object> buildRequestBody(List<ApiParamInfo> params) {
        for (ApiParamInfo param : params) {
            if ("RequestBody".equals(param.getSource())) {
                Map<String, Object> requestBody = new LinkedHashMap<>();
                requestBody.put("required", param.isRequired());
                if (param.getDescription() != null && !param.getDescription().isEmpty()) {
                    requestBody.put("description", param.getDescription());
                }

                // content
                Map<String, Object> content = new LinkedHashMap<>();
                Map<String, Object> mediaType = new LinkedHashMap<>();

                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("$ref", "#/components/schemas/" + param.getType());
                mediaType.put("schema", schema);

                content.put("application/json", mediaType);
                requestBody.put("content", content);

                return requestBody;
            }
        }
        return null;
    }

    /**
     * 构建 responses
     */
    private Map<String, Object> buildResponses(ApiInfo api) {
        Map<String, Object> responses = new LinkedHashMap<>();

        // 200 响应
        Map<String, Object> response200 = new LinkedHashMap<>();
        String desc = api.getReturnDescription();
        response200.put("description", (desc != null && !desc.isEmpty()) ? desc : "成功响应");

        // 如果有返回值类型，添加 content
        if (api.getReturnType() != null && !api.getReturnType().equals("void") && !api.getReturnType().equals("Void")) {
            Map<String, Object> content = new LinkedHashMap<>();
            Map<String, Object> mediaType = new LinkedHashMap<>();

            Map<String, Object> schema = new LinkedHashMap<>();
            // 对于 ResponseEntity<XXX>，提取内部类型
            String refType = extractInnerType(api.getReturnType());
            if (refType != null) {
                schema.put("$ref", "#/components/schemas/" + refType);
            } else {
                schema.put("type", javaTypeToOpenApiType(api.getReturnType()));
            }
            mediaType.put("schema", schema);
            content.put("application/json", mediaType);
            response200.put("content", content);
        }

        responses.put("200", response200);

        return responses;
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    /**
     * 将 Spring MVC 路径转换为 OpenAPI 路径
     * <p>
     * Spring 的 {id} 与 OpenAPI 的 {id} 格式相同，通常无需转换。
     * 但如果路径包含 Ant 风格通配符（如 /*），需要处理。
     */
    private String convertPath(String springPath) {
        // Spring 和 OpenAPI 路径变量格式一致，直接返回
        return springPath;
    }

    /**
     * 根据参数来源确定 OpenAPI 中的位置（in 字段）
     *
     * @return "path" / "query" / "header" / "cookie"，RequestBody 返回 null
     */
    private String resolveParamLocation(String source) {
        switch (source) {
            case "PathVariable":
                return "path";
            case "RequestParam":
                return "query";
            case "RequestHeader":
                return "header";
            case "CookieValue":
                return "cookie";
            default:
                return null; // RequestBody 和 ModelAttribute 不作为 parameter
        }
    }

    /**
     * 将 Java 类型名转换为 OpenAPI 类型名
     */
    private String javaTypeToOpenApiType(String javaType) {
        if (javaType == null || javaType.isEmpty()) {
            return "object";
        }

        // 处理数组
        if (javaType.endsWith("[]")) {
            return "array";
        }

        // 处理泛型容器
        String baseType = javaType.contains("<") ? javaType.substring(0, javaType.indexOf("<")) : javaType;

        switch (baseType) {
            case "String":
            case "char":
            case "Character":
                return "string";
            case "int":
            case "Integer":
            case "long":
            case "Long":
            case "short":
            case "Short":
                return "integer";
            case "float":
            case "Float":
            case "double":
            case "Double":
            case "BigDecimal":
                return "number";
            case "boolean":
            case "Boolean":
                return "boolean";
            case "List":
            case "Set":
            case "Collection":
            case "ArrayList":
            case "LinkedList":
            case "HashSet":
                return "array";
            case "void":
            case "Void":
                return "object";
            case "LocalDate":
            case "Date":
                return "string";
            case "LocalDateTime":
            case "LocalTime":
                return "string";
            default:
                return "object";
        }
    }

    /**
     * 从泛型类型中提取内部类型名
     * <p>
     * 示例：ResponseEntity&lt;User&gt; → "User"，List&lt;User&gt; → "User"
     *
     * @return 内部类型名，如果不是泛型则返回 null
     */
    private String extractInnerType(String type) {
        if (type == null || !type.contains("<") || !type.contains(">")) {
            // 对于非泛型对象类型，如果不是基本类型，也返回类型名
            if (type != null && !isPrimitiveType(type)) {
                return type;
            }
            return null;
        }

        String inner = type.substring(type.indexOf("<") + 1, type.lastIndexOf(">"));
        // 如果内部还是泛型（如 ResponseEntity<List<User>>），递归提取
        if (inner.contains("<")) {
            return extractInnerType(inner);
        }
        return inner;
    }

    /**
     * 判断是否为基本类型名
     */
    private boolean isPrimitiveType(String type) {
        switch (type) {
            case "String":
            case "int":
            case "Integer":
            case "long":
            case "Long":
            case "short":
            case "Short":
            case "float":
            case "Float":
            case "double":
            case "Double":
            case "boolean":
            case "Boolean":
            case "char":
            case "Character":
            case "void":
            case "Void":
            case "BigDecimal":
                return true;
            default:
                return false;
        }
    }
}
