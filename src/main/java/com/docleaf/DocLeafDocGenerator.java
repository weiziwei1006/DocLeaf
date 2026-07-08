package com.docleaf;

import com.docleaf.model.ApiInfo;
import com.docleaf.model.ApiParamInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DocLeaf 文档生成器 —— 核心组件
 * <p>
 * 核心理念：零注解、零侵入 —— 直接从 Spring 原生注解中提取 API 元数据，自动生成 API 文档。
 * <p>
 * 工作原理：
 * <ol>
 *   <li>在 Spring 容器启动完成后自动执行（实现 {@link ApplicationRunner}）</li>
 *   <li>通过注入的 {@link RequestMappingHandlerMapping} 获取所有接口映射</li>
 *   <li>遍历 {@code Map<RequestMappingInfo, HandlerMethod>}，提取路径、HTTP 方法、参数、返回值等信息</li>
 *   <li>按 Controller 分组，生成 Markdown 格式的 API 文档</li>
 *   <li>文档生成完成后在控制台输出提示信息，不阻塞应用启动</li>
 * </ol>
 *
 * @author DocLeaf
 */
@Component
public class DocLeafDocGenerator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocLeafDocGenerator.class);

    /** 输出文件名 */
    private static final String OUTPUT_FILE_NAME = "API_DOCUMENTATION.md";

    /** 参数名发现器，用于获取方法参数的真实名称（依赖 -parameters 编译参数或 ASM 字节码分析） */
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /** Spring MVC 的请求映射处理器，包含所有 @RequestMapping 映射信息 */
    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    // ========================================================================
    // 主流程
    // ========================================================================

    @Override
    public void run(ApplicationArguments args) {
        log.info("DocLeaf 开始扫描 API 接口...");

        // 1. 获取所有 Handler 方法映射
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        if (handlerMethods.isEmpty()) {
            log.warn("未发现任何 API 接口，跳过文档生成。");
            return;
        }

        // 2. 遍历并提取信息，按 Controller 类名归类（使用 LinkedHashMap 保持插入顺序）
        Map<String, List<ApiInfo>> controllerApiMap = new LinkedHashMap<>();
        int totalApiCount = 0;

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();

            // 跳过 Spring 框架内置接口（如 BasicErrorController）
            Class<?> beanType = handlerMethod.getBeanType();
            if (isFrameworkInternal(beanType)) {
                continue;
            }

            // 提取接口信息
            ApiInfo apiInfo = extractApiInfo(mappingInfo, handlerMethod);
            if (apiInfo == null) {
                continue;
            }

            // 按 Controller 类名归类
            String controllerName = apiInfo.getControllerName();
            controllerApiMap.computeIfAbsent(controllerName, k -> new ArrayList<>()).add(apiInfo);
            totalApiCount++;
        }

        if (controllerApiMap.isEmpty()) {
            log.warn("未发现用户自定义的 API 接口，跳过文档生成。");
            return;
        }

        // 3. 生成 Markdown 内容
        String markdown = generateMarkdown(controllerApiMap, totalApiCount);

        // 4. 写入文件（输出到项目根目录 user.dir）
        String outputDir = System.getProperty("user.dir");
        Path outputPath = Paths.get(outputDir, OUTPUT_FILE_NAME);
        try {
            Files.write(outputPath, markdown.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("文档写入失败: {}", e.getMessage(), e);
            return;
        }

        // 5. 控制台输出成功提示（使用 UTF-8 编码，避免 Windows GBK 控制台乱码）
        try {
            java.io.PrintStream utf8Out = new java.io.PrintStream(System.out, true, "UTF-8");
            utf8Out.println("✅ DocLeaf 文档已生成：" + outputPath.toAbsolutePath().toString());
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("DocLeaf 文档已生成：" + outputPath.toAbsolutePath().toString());
        }
        log.info("DocLeaf 文档生成完成！共扫描 {} 个 Controller，{} 个接口。", controllerApiMap.size(), totalApiCount);
    }

    // ========================================================================
    // 核心提取逻辑
    // ========================================================================

    /**
     * 从 RequestMappingInfo 和 HandlerMethod 中提取完整的接口信息
     */
    private ApiInfo extractApiInfo(RequestMappingInfo mappingInfo, HandlerMethod handlerMethod) {
        ApiInfo apiInfo = new ApiInfo();

        // Controller 类名（如 UserController）
        apiInfo.setControllerName(handlerMethod.getBeanType().getSimpleName());

        // 方法名（作为接口描述的默认值）
        apiInfo.setMethodName(handlerMethod.getMethod().getName());

        // 请求路径（如 /api/users/{id}）
        apiInfo.setPaths(extractPaths(mappingInfo));

        // HTTP 方法（GET/POST/PUT/DELETE 等）
        apiInfo.setHttpMethods(extractHttpMethods(mappingInfo));

        // 参数列表（区分 @PathVariable、@RequestParam、@RequestBody）
        apiInfo.setParams(extractParameters(handlerMethod));

        // 返回值类型（如 User 或 ResponseEntity<User>）
        apiInfo.setReturnType(extractReturnType(handlerMethod));

        return apiInfo;
    }

    /**
     * 提取请求路径
     * <p>
     * 兼容 Spring Boot 2.6+ 的 PathPatternsCondition（默认）和旧版 PatternsCondition。
     * 无论用户使用 @RequestMapping 还是 @GetMapping 等快捷注解，路径信息都会被正确提取。
     */
    private Set<String> extractPaths(RequestMappingInfo mappingInfo) {
        // Spring Boot 2.6+ 默认使用 PathPatternsRequestCondition
        if (mappingInfo.getPathPatternsCondition() != null) {
            return mappingInfo.getPathPatternsCondition().getPatternValues();
        }
        // 兼容旧版 PatternsRequestCondition（当 spring.mvc.pathmatch.matching-strategy=ant_path_matcher 时）
        if (mappingInfo.getPatternsCondition() != null) {
            return mappingInfo.getPatternsCondition().getPatterns();
        }
        return Collections.emptySet();
    }

    /**
     * 提取支持的 HTTP 方法
     * <p>
     * 无论用户使用 @RequestMapping(method=GET) 还是 @GetMapping，方法信息都会被正确提取。
     * 如果没有指定 HTTP 方法，说明支持所有方法，标记为 "ALL"。
     */
    private Set<String> extractHttpMethods(RequestMappingInfo mappingInfo) {
        Set<String> methods = new LinkedHashSet<>();
        for (org.springframework.web.bind.annotation.RequestMethod rm : mappingInfo.getMethodsCondition().getMethods()) {
            methods.add(rm.name());
        }
        if (methods.isEmpty()) {
            methods.add("ALL");
        }
        return methods;
    }

    /**
     * 提取方法参数列表
     * <p>
     * 识别以下参数注解并分类：
     * <ul>
     *   <li>{@link PathVariable @PathVariable} — 路径参数</li>
     *   <li>{@link RequestParam @RequestParam} — 查询参数</li>
     *   <li>{@link RequestBody @RequestBody} — 请求体</li>
     *   <li>{@link RequestHeader @RequestHeader} — 请求头</li>
     *   <li>{@link CookieValue @CookieValue} — Cookie</li>
     * </ul>
     * 同时标注每个参数是否必填（required）。
     */
    private List<ApiParamInfo> extractParameters(HandlerMethod handlerMethod) {
        List<ApiParamInfo> params = new ArrayList<>();
        MethodParameter[] methodParameters = handlerMethod.getMethodParameters();

        for (MethodParameter methodParam : methodParameters) {
            // 跳过框架内置参数（如 HttpServletRequest、Model、BindingResult 等）
            if (shouldSkipParameter(methodParam)) {
                continue;
            }

            ApiParamInfo paramInfo = new ApiParamInfo();

            // 参数来源类型（PathVariable / RequestParam / RequestBody 等）
            paramInfo.setSource(resolveParamSource(methodParam));

            // 参数名
            paramInfo.setName(resolveParamName(methodParam));

            // 参数的 Java 类型
            paramInfo.setType(resolveType(methodParam));

            // 是否必填
            paramInfo.setRequired(resolveRequired(methodParam));

            // 默认值
            paramInfo.setDefaultValue(resolveDefaultValue(methodParam));

            params.add(paramInfo);
        }

        return params;
    }

    /**
     * 提取返回值类型
     * <p>
     * 使用 Spring 的 {@link ResolvableType} 正确解析泛型类型，
     * 支持 ResponseEntity&lt;User&gt;、List&lt;User&gt; 等嵌套泛型。
     */
    private String extractReturnType(HandlerMethod handlerMethod) {
        ResolvableType resolvableType = ResolvableType.forMethodReturnType(handlerMethod.getMethod());
        return formatResolvableType(resolvableType);
    }

    // ========================================================================
    // 参数解析辅助方法
    // ========================================================================

    /**
     * 判断是否为 Spring 框架内置的 Controller（如 BasicErrorController），需跳过
     */
    private boolean isFrameworkInternal(Class<?> beanType) {
        Package pkg = beanType.getPackage();
        if (pkg == null) {
            return false;
        }
        String packageName = pkg.getName();
        return packageName.startsWith("org.springframework");
    }

    /**
     * 判断参数是否需要跳过（如 HttpServletRequest、HttpServletResponse、Model 等框架内部参数）
     */
    private boolean shouldSkipParameter(MethodParameter methodParam) {
        Class<?> paramType = methodParam.getParameterType();
        // 跳过 Servlet API 相关参数
        return javax.servlet.ServletRequest.class.isAssignableFrom(paramType)
                || javax.servlet.ServletResponse.class.isAssignableFrom(paramType)
                || javax.servlet.http.HttpSession.class.isAssignableFrom(paramType)
                || java.security.Principal.class.isAssignableFrom(paramType)
                || java.util.Locale.class.isAssignableFrom(paramType)
                || java.io.InputStream.class.isAssignableFrom(paramType)
                || java.io.OutputStream.class.isAssignableFrom(paramType)
                || java.io.Reader.class.isAssignableFrom(paramType)
                || java.io.Writer.class.isAssignableFrom(paramType)
                || org.springframework.ui.Model.class.isAssignableFrom(paramType)
                || org.springframework.ui.ModelMap.class.isAssignableFrom(paramType)
                || org.springframework.validation.BindingResult.class.isAssignableFrom(paramType)
                || org.springframework.validation.Errors.class.isAssignableFrom(paramType)
                || org.springframework.web.servlet.mvc.support.RedirectAttributes.class.isAssignableFrom(paramType)
                || paramType.getSimpleName().equals("HttpServletRequest")
                || paramType.getSimpleName().equals("HttpServletResponse");
    }

    /**
     * 解析参数来源类型
     */
    private String resolveParamSource(MethodParameter methodParam) {
        if (methodParam.hasParameterAnnotation(PathVariable.class)) {
            return "PathVariable";
        }
        if (methodParam.hasParameterAnnotation(RequestBody.class)) {
            return "RequestBody";
        }
        if (methodParam.hasParameterAnnotation(RequestParam.class)) {
            return "RequestParam";
        }
        if (methodParam.hasParameterAnnotation(RequestHeader.class)) {
            return "RequestHeader";
        }
        if (methodParam.hasParameterAnnotation(CookieValue.class)) {
            return "CookieValue";
        }
        // 无注解时：简单类型默认为 RequestParam，复杂类型默认为 ModelAttribute
        Class<?> paramType = methodParam.getParameterType();
        if (isSimpleType(paramType)) {
            return "RequestParam";
        }
        return "ModelAttribute";
    }

    /**
     * 解析参数名
     * <p>
     * 优先从注解的 value/name 属性获取，其次使用参数名发现器获取真实参数名。
     */
    private String resolveParamName(MethodParameter methodParam) {
        // 1. 尝试从 @PathVariable 获取
        PathVariable pathVariable = methodParam.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            String name = getAnnotationName(pathVariable.value(), pathVariable.name());
            if (name != null) return name;
        }

        // 2. 尝试从 @RequestParam 获取
        RequestParam requestParam = methodParam.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            String name = getAnnotationName(requestParam.value(), requestParam.name());
            if (name != null) return name;
        }

        // 3. 尝试从 @RequestHeader 获取
        RequestHeader requestHeader = methodParam.getParameterAnnotation(RequestHeader.class);
        if (requestHeader != null) {
            String name = getAnnotationName(requestHeader.value(), requestHeader.name());
            if (name != null) return name;
        }

        // 4. 尝试从 @CookieValue 获取
        CookieValue cookieValue = methodParam.getParameterAnnotation(CookieValue.class);
        if (cookieValue != null) {
            String name = getAnnotationName(cookieValue.value(), cookieValue.name());
            if (name != null) return name;
        }

        // 5. @RequestBody 的参数名统一用 "body"
        if (methodParam.hasParameterAnnotation(RequestBody.class)) {
            return "body";
        }

        // 6. 使用参数名发现器获取真实参数名（依赖 -parameters 编译参数）
        methodParam.initParameterNameDiscovery(parameterNameDiscoverer);
        String paramName = methodParam.getParameterName();
        if (paramName != null) {
            return paramName;
        }

        // 7. 兜底：使用参数类型的小写作为参数名
        return methodParam.getParameterType().getSimpleName().toLowerCase();
    }

    /**
     * 解析参数的 Java 类型（支持泛型）
     */
    private String resolveType(MethodParameter methodParam) {
        ResolvableType resolvableType = ResolvableType.forMethodParameter(methodParam);
        return formatResolvableType(resolvableType);
    }

    /**
     * 解析参数是否必填
     */
    private boolean resolveRequired(MethodParameter methodParam) {
        PathVariable pathVariable = methodParam.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            return pathVariable.required();
        }
        RequestParam requestParam = methodParam.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            // 如果有默认值，则非必填
            if (!ValueConstants.DEFAULT_NONE.equals(requestParam.defaultValue())) {
                return false;
            }
            return requestParam.required();
        }
        RequestBody requestBody = methodParam.getParameterAnnotation(RequestBody.class);
        if (requestBody != null) {
            return requestBody.required();
        }
        RequestHeader requestHeader = methodParam.getParameterAnnotation(RequestHeader.class);
        if (requestHeader != null) {
            if (!ValueConstants.DEFAULT_NONE.equals(requestHeader.defaultValue())) {
                return false;
            }
            return requestHeader.required();
        }
        CookieValue cookieValue = methodParam.getParameterAnnotation(CookieValue.class);
        if (cookieValue != null) {
            if (!ValueConstants.DEFAULT_NONE.equals(cookieValue.defaultValue())) {
                return false;
            }
            return cookieValue.required();
        }
        // 无注解的简单类型默认必填
        return isSimpleType(methodParam.getParameterType());
    }

    /**
     * 解析参数默认值
     */
    private String resolveDefaultValue(MethodParameter methodParam) {
        RequestParam requestParam = methodParam.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !ValueConstants.DEFAULT_NONE.equals(requestParam.defaultValue())) {
            return requestParam.defaultValue();
        }
        RequestHeader requestHeader = methodParam.getParameterAnnotation(RequestHeader.class);
        if (requestHeader != null && !ValueConstants.DEFAULT_NONE.equals(requestHeader.defaultValue())) {
            return requestHeader.defaultValue();
        }
        CookieValue cookieValue = methodParam.getParameterAnnotation(CookieValue.class);
        if (cookieValue != null && !ValueConstants.DEFAULT_NONE.equals(cookieValue.defaultValue())) {
            return cookieValue.defaultValue();
        }
        return null;
    }

    // ========================================================================
    // 类型格式化辅助方法
    // ========================================================================

    /**
     * 将 ResolvableType 格式化为可读的类型字符串
     * <p>
     * 示例：
     * <ul>
     *   <li>{@code User} → "User"</li>
     *   <li>{@code ResponseEntity<User>} → "ResponseEntity&lt;User&gt;"</li>
     *   <li>{@code List<User>} → "List&lt;User&gt;"</li>
     * </ul>
     */
    private String formatResolvableType(ResolvableType type) {
        Class<?> resolved = type.resolve();
        if (resolved == null) {
            return "Object";
        }

        // void.class.getSimpleName() 自然返回 "void"，无需特殊处理
        // Void.class.getSimpleName() 返回 "Void"，用于 ResponseEntity<Void> 等泛型场景
        String simpleName = resolved.getSimpleName();

        // 处理泛型参数
        ResolvableType[] generics = type.getGenerics();
        if (generics.length > 0) {
            List<String> genericNames = new ArrayList<>();
            for (ResolvableType generic : generics) {
                genericNames.add(formatResolvableType(generic));
            }
            return simpleName + "<" + String.join(", ", genericNames) + ">";
        }

        return simpleName;
    }

    /**
     * 判断是否为简单类型（基本类型、包装类、String、日期等）
     */
    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || type == Boolean.class
                || type == Character.class
                || Number.class.isAssignableFrom(type)
                || type == java.util.Date.class
                || type == java.time.LocalDate.class
                || type == java.time.LocalDateTime.class
                || type == java.time.LocalTime.class
                || type.isEnum();
    }

    /**
     * 从注解的 value 和 name 属性中获取参数名
     */
    private String getAnnotationName(String value, String name) {
        if (value != null && !value.isEmpty()) {
            return value;
        }
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return null;
    }

    // ========================================================================
    // Markdown 文档生成
    // ========================================================================

    /**
     * 生成完整的 Markdown 文档内容
     *
     * @param controllerApiMap 按 Controller 名归类的接口信息
     * @param totalApiCount    接口总数
     * @return Markdown 格式的文档字符串
     */
    private String generateMarkdown(Map<String, List<ApiInfo>> controllerApiMap, int totalApiCount) {
        StringBuilder sb = new StringBuilder();

        // ---------- 文档头部 ----------
        sb.append("# API 接口文档\n\n");
        sb.append("> 由 **DocLeaf** 自动生成 ｜ 零注解 · 零侵入\n\n");
        sb.append("> 生成时间：")
          .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
          .append("\n\n");
        sb.append("---\n\n");

        // ---------- 统计概览 ----------
        sb.append("## 概览\n\n");
        sb.append("| 统计项 | 数量 |\n");
        sb.append("|--------|------|\n");
        sb.append("| Controller 数量 | ").append(controllerApiMap.size()).append(" |\n");
        sb.append("| 接口总数 | ").append(totalApiCount).append(" |\n\n");
        sb.append("---\n\n");

        // ---------- 目录 ----------
        sb.append("## 目录\n\n");
        int idx = 1;
        for (String controllerName : controllerApiMap.keySet()) {
            sb.append(String.format("%d. [%s](#%s)\n", idx++, controllerName,
                    controllerName.toLowerCase()));
        }
        sb.append("\n---\n\n");

        // ---------- 各 Controller 接口详情 ----------
        for (Map.Entry<String, List<ApiInfo>> entry : controllerApiMap.entrySet()) {
            String controllerName = entry.getKey();
            List<ApiInfo> apiList = entry.getValue();

            // 二级标题：Controller 类名
            sb.append("## ").append(controllerName).append("\n\n");

            // 接口表格
            sb.append("| # | 接口描述 | 请求方法 | 请求路径 | 参数详情 | 返回值 |\n");
            sb.append("|---|---------|---------|---------|---------|-------|\n");

            int index = 1;
            for (ApiInfo api : apiList) {
                sb.append("| ").append(index++).append(" ");
                sb.append("| ").append(api.getMethodName()).append(" ");
                sb.append("| ").append(String.join(" / ", api.getHttpMethods())).append(" ");
                sb.append("| `").append(String.join("`, `", api.getPaths())).append("` ");
                sb.append("| ").append(formatParams(api.getParams())).append(" ");
                sb.append("| `").append(api.getReturnType()).append("` |\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 格式化参数列表为 Markdown 表格单元格内容
     * <p>
     * 多个参数使用 {@code <br>} 换行分隔，每个参数格式为：
     * {@code `参数名` (来源, 类型, 必填/可选[, 默认值])}
     */
    private String formatParams(List<ApiParamInfo> params) {
        if (params == null || params.isEmpty()) {
            return "无";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append("<br>");
            }
            ApiParamInfo p = params.get(i);
            sb.append("`").append(p.getName()).append("`");
            sb.append(" (").append(p.getSource());
            sb.append(", ").append(p.getType());
            sb.append(p.isRequired() ? ", 必填" : ", 可选");
            if (p.getDefaultValue() != null) {
                sb.append(", 默认: `").append(p.getDefaultValue()).append("`");
            }
            sb.append(")");
        }
        return sb.toString();
    }
}
