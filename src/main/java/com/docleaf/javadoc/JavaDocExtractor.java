package com.docleaf.javadoc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavaDoc 注释提取器 —— 阶段二核心组件
 * <p>
 * 通过读取磁盘上的 {@code .java} 源文件，利用正则和行扫描提取 JavaDoc 注释块中的内容，
 * 作为 API 接口的描述信息。
 * <p>
 * 设计要点：
 * <ol>
 *   <li><b>源文件定位</b>：根据类的全限定名，在配置的源码根目录下查找 {@code .java} 文件</li>
 *   <li><b>方法匹配</b>：通过"方法名 + 参数类型列表"精确定位，支持重载场景</li>
 *   <li><b>注释提取</b>：找到方法声明上方紧邻的 JavaDoc 注释块</li>
 *   <li><b>首句提取</b>：以句号（{@code 。}/{@code .}）、空行或 {@code @tag} 为结尾截取首句</li>
 *   <li><b>参数描述</b>：解析 {@code @param} 标签，为参数补充描述</li>
 *   <li><b>缓存机制</b>：按类缓存解析结果，避免重复 I/O</li>
 *   <li><b>优雅降级</b>：源文件不存在时（如 JAR 部署）自动回退为方法名</li>
 * </ol>
 *
 * @author DocLeaf
 */
public class JavaDocExtractor {

    private static final Logger log = LoggerFactory.getLogger(JavaDocExtractor.class);

    // ======================== 正则预编译 ========================

    /** 匹配 JavaDoc 块（多行注释）的正则 */
    private static final Pattern JAVADOC_PATTERN = Pattern.compile("/\\*\\*[\\s\\S]*?\\*/");

    /** 匹配方法声明中的方法名+参数列表：word(  */
    private static final Pattern METHOD_NAME_PATTERN = Pattern.compile("(\\w+)\\s*\\(");

    /** 匹配类声明：class/interface/enum ClassName  */
    private static final Pattern CLASS_DECL_PATTERN =
            Pattern.compile("\\b(?:public\\s+|private\\s+|protected\\s+)?(?:abstract\\s+|final\\s+)?(?:class|interface|enum)\\s+(\\w+)");

    /** 匹配 @param 标签 */
    private static final Pattern PARAM_TAG_PATTERN =
            Pattern.compile("@param\\s+(\\S+)\\s+(.*?)(?=\\s+@|\\s*$)", Pattern.DOTALL);

    /** 匹配 @return 标签 */
    private static final Pattern RETURN_TAG_PATTERN =
            Pattern.compile("@return\\s+(.*?)(?=\\s+@|\\s*$)", Pattern.DOTALL);

    /** 匹配注解：@Annotation(...) 或 @Annotation */
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("@\\w+(\\([^)]*\\))?");

    // ======================== 成员变量 ========================

    /** 源码根目录（如 src/main/java） */
    private final Path sourceRoot;

    /** 类级别 JavaDoc 缓存：Class → ClassJavaDoc（避免重复读取同一文件） */
    private final Map<Class<?>, ClassJavaDoc> cache = new ConcurrentHashMap<>();

    /** 空结果标记（避免对找不到源文件的类反复尝试 I/O） */
    private final Map<Class<?>, Boolean> missingCache = new ConcurrentHashMap<>();

    // ======================== 构造方法 ========================

    /**
     * 构造 JavaDoc 提取器
     *
     * @param sourceRoot 源码根目录路径（如 "src/main/java"）
     */
    public JavaDocExtractor(String sourceRoot) {
        this.sourceRoot = Paths.get(sourceRoot);
        log.debug("JavaDocExtractor 初始化，源码根目录：{}", this.sourceRoot.toAbsolutePath());
    }

    // ======================== 公共 API ========================

    /**
     * 获取方法的 JavaDoc 描述（首句摘要）
     * <p>
     * 优先通过"方法名 + 参数类型"精确匹配；若未命中，回退为方法名匹配；
     * 若仍未命中，返回方法名本身作为描述。
     *
     * @param clazz  方法所属的类
     * @param method 反射获取的方法对象
     * @return JavaDoc 首句描述，或方法名（兜底）
     */
    public String getMethodDescription(Class<?> clazz, Method method) {
        MethodJavaDoc methodDoc = getMethodJavaDoc(clazz, method);
        if (methodDoc != null && methodDoc.getDescription() != null && !methodDoc.getDescription().isEmpty()) {
            return methodDoc.getDescription();
        }
        return method.getName();
    }

    /**
     * 获取方法参数的描述映射（来自 @param 标签）
     *
     * @param clazz  方法所属的类
     * @param method 反射获取的方法对象
     * @return 参数名 → 描述文本的映射，无则为空 Map
     */
    public Map<String, String> getParamDescriptions(Class<?> clazz, Method method) {
        MethodJavaDoc methodDoc = getMethodJavaDoc(clazz, method);
        if (methodDoc != null) {
            return methodDoc.getParamDescriptions();
        }
        return Collections.emptyMap();
    }

    /**
     * 获取方法的返回值描述（来自 @return 标签）
     *
     * @param clazz  方法所属的类
     * @param method 反射获取的方法对象
     * @return 返回值描述，无则为 null
     */
    public String getReturnDescription(Class<?> clazz, Method method) {
        MethodJavaDoc methodDoc = getMethodJavaDoc(clazz, method);
        if (methodDoc != null) {
            return methodDoc.getReturnDescription();
        }
        return null;
    }

    /**
     * 获取类的 JavaDoc 描述（首句摘要）
     *
     * @param clazz 目标类
     * @return 类描述，无则为 null
     */
    public String getClassDescription(Class<?> clazz) {
        ClassJavaDoc classDoc = loadClassJavaDoc(clazz);
        return classDoc != null ? classDoc.getClassDescription() : null;
    }

    // ======================== 内部：加载与缓存 ========================

    /**
     * 获取方法的完整 JavaDoc 信息
     */
    private MethodJavaDoc getMethodJavaDoc(Class<?> clazz, Method method) {
        ClassJavaDoc classDoc = loadClassJavaDoc(clazz);
        if (classDoc == null) {
            return null;
        }

        // 1. 优先通过 "方法名(参数类型1,参数类型2)" 精确匹配
        String signatureKey = buildSignatureKey(method);
        MethodJavaDoc doc = classDoc.getMethodDocsBySignature().get(signatureKey);
        if (doc != null) {
            return doc;
        }

        // 2. 回退：通过方法名匹配（适用于无重载或参数类型解析失败的情况）
        return classDoc.getMethodDocsByName().get(method.getName());
    }

    /**
     * 加载类的 JavaDoc 信息（带缓存）
     */
    private ClassJavaDoc loadClassJavaDoc(Class<?> clazz) {
        // 检查缺失缓存
        if (missingCache.containsKey(clazz)) {
            return null;
        }

        ClassJavaDoc cached = cache.get(clazz);
        if (cached != null) {
            return cached;
        }

        // 定位源文件
        Path sourceFile = resolveSourceFile(clazz);
        if (sourceFile == null || !Files.exists(sourceFile)) {
            missingCache.put(clazz, true);
            log.debug("未找到类 [{}] 的源文件，JavaDoc 提取将回退为方法名。", clazz.getName());
            return null;
        }

        // 读取并解析
        ClassJavaDoc classDoc;
        try {
            String content = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8);
            classDoc = parseSourceFile(content, clazz);
        } catch (IOException e) {
            log.warn("读取源文件失败 [{}]：{}", sourceFile, e.getMessage());
            missingCache.put(clazz, true);
            return null;
        }

        cache.put(clazz, classDoc);
        log.debug("已加载类 [{}] 的 JavaDoc：{} 个方法", clazz.getSimpleName(),
                classDoc.getMethodDocsBySignature().size());
        return classDoc;
    }

    /**
     * 根据类的全限定名定位源文件
     * <p>
     * 对于内部类，使用其声明类（外层类）的源文件。
     *
     * @param clazz 目标类
     * @return 源文件路径，找不到则返回 null
     */
    private Path resolveSourceFile(Class<?> clazz) {
        // 处理内部类：向上查找顶层声明类
        Class<?> targetClass = clazz;
        while (targetClass.getDeclaringClass() != null) {
            targetClass = targetClass.getDeclaringClass();
        }

        String packageName = targetClass.getPackage() != null
                ? targetClass.getPackage().getName() : "";
        String packagePath = packageName.replace('.', '/');
        String fileName = targetClass.getSimpleName() + ".java";

        return sourceRoot.resolve(packagePath).resolve(fileName);
    }

    // ======================== 内部：源文件解析 ========================

    /**
     * 解析源文件内容，提取类级和方法级 JavaDoc
     *
     * @param content 源文件全文
     * @param clazz   目标类
     * @return 类级 JavaDoc 信息
     */
    private ClassJavaDoc parseSourceFile(String content, Class<?> clazz) {
        ClassJavaDoc classDoc = new ClassJavaDoc();

        // 1. 提取所有 JavaDoc 块及其结束位置
        List<JavadocBlock> javadocBlocks = findAllJavadocBlocks(content);

        if (javadocBlocks.isEmpty()) {
            return classDoc;
        }

        // 2. 匹配类级 JavaDoc
        String className = clazz.getSimpleName();
        // 对于内部类，在源文件中查找内部类的声明
        Class<?> searchClass = clazz;
        while (searchClass.getDeclaringClass() != null) {
            searchClass = searchClass.getDeclaringClass();
        }
        // 如果目标类是内部类，用内部类名查找；否则用顶层类名
        String searchClassName = clazz.getSimpleName();

        for (JavadocBlock block : javadocBlocks) {
            String textAfter = content.substring(block.end).trim();
            // 跳过注解
            textAfter = stripLeadingAnnotations(textAfter);
            Matcher classMatcher = CLASS_DECL_PATTERN.matcher(textAfter);
            if (classMatcher.find() && classMatcher.group(1).equals(searchClassName)) {
                classDoc.setClassDescription(extractFirstSentence(block.content));
                break;
            }
        }

        // 3. 匹配方法级 JavaDoc
        for (JavadocBlock block : javadocBlocks) {
            // 获取 JavaDoc 块之后的文本，查找方法声明
            String textAfter = content.substring(block.end);
            MethodDeclaration methodDecl = findMethodDeclaration(textAfter);
            if (methodDecl != null) {
                MethodJavaDoc methodDoc = parseMethodJavaDoc(block.content);
                // 用签名和名称两种 key 存储
                classDoc.getMethodDocsBySignature().put(methodDecl.signatureKey, methodDoc);
                // 方法名 key：如果已存在（重载），不覆盖（保留第一个）
                if (!classDoc.getMethodDocsByName().containsKey(methodDecl.methodName)) {
                    classDoc.getMethodDocsByName().put(methodDecl.methodName, methodDoc);
                }
            }
        }

        return classDoc;
    }

    /**
     * 查找文本中第一个方法声明
     * <p>
     * 从 JavaDoc 块结束位置开始，跳过注解和空白行，
     * 找到方法声明并解析出方法名、参数类型列表和签名 key。
     *
     * @param textAfter JavaDoc 块之后的文本
     * @return 方法声明信息，找不到返回 null
     */
    private MethodDeclaration findMethodDeclaration(String textAfter) {
        // 去除前导注解和空白
        String stripped = stripLeadingAnnotations(textAfter).trim();
        if (stripped.isEmpty()) {
            return null;
        }

        // 查找方法名 + ( 模式
        Matcher matcher = METHOD_NAME_PATTERN.matcher(stripped);
        if (!matcher.find()) {
            return null;
        }

        String methodName = matcher.group(1);
        int parenStart = matcher.start(1) + methodName.length();
        // 找到匹配的右括号
        int parenEnd = findMatchingParen(stripped, stripped.indexOf('(', parenStart));
        if (parenEnd < 0) {
            return null;
        }

        // 提取参数列表
        String paramStr = stripped.substring(stripped.indexOf('(', parenStart) + 1, parenEnd);
        List<String> paramTypes = parseParamTypes(paramStr);

        // 构建签名 key
        String signatureKey = methodName + "(" + String.join(",", paramTypes) + ")";

        // 验证：方法声明后面应该有 { 或 throws 或 ; （排除方法调用）
        String afterParen = stripped.substring(parenEnd + 1).trim();
        // 跳过 throws 子句
        if (afterParen.startsWith("throws")) {
            // 查找 { 或 ;
        }
        // 必须包含 { 或 ; 才是方法声明（而非方法调用）
        // 但可能 { 在后续行中，所以放宽检查：如果参数列表后面紧跟 { 、; 、throws 就认为是声明
        // 如果紧跟的是 = 或其他，则不是声明
        if (afterParen.startsWith("{") || afterParen.startsWith(";") || afterParen.startsWith("throws")
                || afterParen.startsWith("default")) {
            return new MethodDeclaration(methodName, paramTypes, signatureKey);
        }

        // 有时 { 可能在下一行，放宽检查：如果参数列表后有换行，且下一行是 {，也认为是方法声明
        // 但为了安全，我们只接受明确的方法声明
        return null;
    }

    /**
     * 解析参数类型列表
     * <p>
     * 输入示例：{@code @PathVariable Long id, @RequestBody User user}
     * 输出：{@code ["Long", "User"]}
     *
     * @param paramStr 参数列表原始字符串
     * @return 参数简单类型名列表
     */
    private List<String> parseParamTypes(String paramStr) {
        List<String> types = new ArrayList<>();
        if (paramStr == null || paramStr.trim().isEmpty()) {
            return types;
        }

        // 按逗号分割（注意泛型中的逗号）
        List<String> params = splitRespectingGenerics(paramStr);

        for (String param : params) {
            String type = extractSimpleTypeName(param);
            if (type != null && !type.isEmpty()) {
                types.add(type);
            }
        }

        return types;
    }

    /**
     * 从单个参数声明中提取简单类型名
     * <p>
     * 示例：
     * <ul>
     *   <li>{@code @PathVariable Long id} → "Long"</li>
     *   <li>{@code @RequestBody User user} → "User"</li>
     *   <li>{@code List<User> users} → "List"</li>
     *   <li>{@code Long[] ids} → "Long[]"</li>
     *   <li>{@code String... args} → "String[]"</li>
     * </ul>
     */
    private String extractSimpleTypeName(String param) {
        // 1. 去除注解
        String stripped = ANNOTATION_PATTERN.matcher(param).replaceAll("").trim();
        // 2. 去除 final 关键字
        stripped = stripped.replaceFirst("^final\\s+", "");
        // 3. 去除 vararg 的 ...
        boolean isVararg = stripped.contains("...");
        stripped = stripped.replace("...", "[]");

        if (stripped.isEmpty()) {
            return "";
        }

        // 4. 提取类型部分（最后一个词是参数名，其余是类型）
        // 处理泛型：如 List<User> users → 类型是 List<User>
        // 需要按空格分割，但泛型内可能有空格
        String typePart = extractTypePart(stripped);
        if (typePart == null || typePart.isEmpty()) {
            return "";
        }

        // 5. 简化类型名
        return simplifyTypeName(typePart, isVararg);
    }

    /**
     * 从 "Type name" 或 "Type<Generic> name" 中提取类型部分
     */
    private String extractTypePart(String param) {
        int depth = 0;
        int lastTypeEnd = -1;

        for (int i = 0; i < param.length(); i++) {
            char c = param.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ' ' && depth == 0) {
                // 在泛型外遇到空格，前面的部分可能是类型
                lastTypeEnd = i;
            }
        }

        if (lastTypeEnd > 0) {
            return param.substring(0, lastTypeEnd).trim();
        }
        // 没有空格，整个字符串可能就是类型
        return param.trim();
    }

    /**
     * 将类型名简化为与反射一致的简单名称
     * <p>
     * 示例：
     * <ul>
     *   <li>{@code java.util.List<User>} → "List"</li>
     *   <li>{@code Long[]} → "Long[]"</li>
     *   <li>{@code com.example.User} → "User"</li>
     * </ul>
     */
    private String simplifyTypeName(String type, boolean isArray) {
        // 去除泛型部分
        if (type.contains("<")) {
            type = type.substring(0, type.indexOf("<"));
        }
        // 去除包名
        if (type.contains(".")) {
            type = type.substring(type.lastIndexOf(".") + 1);
        }
        // 处理数组标记
        boolean hasArrayBracket = type.contains("[]");
        type = type.replace("[]", "");
        if (isArray || hasArrayBracket) {
            type = type + "[]";
        }
        return type;
    }

    /**
     * 按逗号分割参数列表，尊重泛型尖括号嵌套
     */
    private List<String> splitRespectingGenerics(String str) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) {
                result.add(str.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < str.length()) {
            result.add(str.substring(start).trim());
        }
        return result;
    }

    /**
     * 找到左括号对应的右括号位置
     */
    private int findMatchingParen(String str, int openPos) {
        if (openPos < 0 || openPos >= str.length() || str.charAt(openPos) != '(') {
            return -1;
        }
        int depth = 0;
        for (int i = openPos; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    // ======================== 内部：JavaDoc 内容解析 ========================

    /**
     * 解析 JavaDoc 块内容，提取描述、@param、@return
     */
    private MethodJavaDoc parseMethodJavaDoc(String javadocContent) {
        MethodJavaDoc doc = new MethodJavaDoc();

        // 提取首句描述
        doc.setDescription(extractFirstSentence(javadocContent));

        // 提取 @param 标签
        Map<String, String> paramDescs = extractParamTags(javadocContent);
        doc.setParamDescriptions(paramDescs);

        // 提取 @return 标签
        String returnDesc = extractReturnTag(javadocContent);
        doc.setReturnDescription(returnDesc);

        return doc;
    }

    /**
     * 从 JavaDoc 中提取首句描述
     * <p>
     * 规则：
     * <ol>
     *   <li>去除 JavaDoc 起始和结束标记</li>
     *   <li>去除每行前导 {@code *}</li>
     *   <li>遇到 {@code @tag}（如 @param）停止</li>
     *   <li>遇到空行（已有文本时）停止</li>
     *   <li>遇到 HTML 标签（如 {@code <p>}）停止</li>
     *   <li>在结果中查找首个句号（{@code 。}或{@code .}后跟空格/行尾）截断</li>
     * </ol>
     */
    private String extractFirstSentence(String javadoc) {
        if (javadoc == null || javadoc.isEmpty()) {
            return "";
        }

        // 1. 去除 /** 和 */
        String content = javadoc;
        content = content.replaceAll("^/\\*\\*+", "").replaceAll("\\*+/$", "");

        // 2. 逐行处理
        String[] lines = content.split("\n");
        StringBuilder textBuilder = new StringBuilder();
        boolean foundText = false;

        for (String line : lines) {
            String trimmed = line.trim();
            // 去除前导 *
            if (trimmed.startsWith("*")) {
                trimmed = trimmed.substring(1).trim();
            }
            // 遇到 @tag 停止
            if (trimmed.startsWith("@")) {
                break;
            }
            // 空行处理
            if (trimmed.isEmpty()) {
                if (foundText) break;
                else continue;
            }
            // HTML 标签处理
            if (trimmed.startsWith("<")) {
                if (foundText) break;
                else continue;
            }

            foundText = true;
            textBuilder.append(trimmed).append(" ");
        }

        String text = textBuilder.toString().trim();
        if (text.isEmpty()) {
            return "";
        }

        // 3. 在首句中查找句号截断
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '。') {
                return text.substring(0, i + 1);
            }
            if (c == '.' && (i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1)))) {
                return text.substring(0, i + 1);
            }
        }

        return text;
    }

    /**
     * 提取 @param 标签
     * <p>
     * 格式：{@code @param paramName 参数描述}
     *
     * @return 参数名 → 描述文本的映射
     */
    private Map<String, String> extractParamTags(String javadoc) {
        Map<String, String> result = new LinkedHashMap<>();
        if (javadoc == null || javadoc.isEmpty()) {
            return result;
        }

        // 去除 /** 和 */
        String content = javadoc.replaceAll("^/\\*\\*+", "").replaceAll("\\*+/$", "");

        // 逐行处理，提取 @param 行
        String[] lines = content.split("\n");
        String currentParam = null;
        StringBuilder currentDesc = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*")) {
                trimmed = trimmed.substring(1).trim();
            }

            if (trimmed.startsWith("@param")) {
                // 保存上一个参数
                if (currentParam != null && currentDesc != null) {
                    result.put(currentParam, currentDesc.toString().trim());
                }
                // 解析新参数
                String rest = trimmed.substring("@param".length()).trim();
                // 参数名是第一个词
                int spaceIdx = rest.indexOf(' ');
                if (spaceIdx > 0) {
                    currentParam = rest.substring(0, spaceIdx);
                    currentDesc = new StringBuilder(rest.substring(spaceIdx + 1).trim());
                } else {
                    currentParam = rest;
                    currentDesc = new StringBuilder();
                }
            } else if (currentParam != null) {
                // 续行（遇到其他 @tag 停止）
                if (trimmed.startsWith("@")) {
                    if (currentDesc != null) {
                        result.put(currentParam, currentDesc.toString().trim());
                    }
                    currentParam = null;
                    currentDesc = null;
                } else if (!trimmed.isEmpty()) {
                    if (currentDesc != null) {
                        currentDesc.append(" ").append(trimmed);
                    }
                }
            }
        }

        // 保存最后一个参数
        if (currentParam != null && currentDesc != null) {
            result.put(currentParam, currentDesc.toString().trim());
        }

        return result;
    }

    /**
     * 提取 @return 标签
     */
    private String extractReturnTag(String javadoc) {
        if (javadoc == null || javadoc.isEmpty()) {
            return null;
        }

        String content = javadoc.replaceAll("^/\\*\\*+", "").replaceAll("\\*+/$", "");
        String[] lines = content.split("\n");
        StringBuilder returnDesc = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*")) {
                trimmed = trimmed.substring(1).trim();
            }

            if (trimmed.startsWith("@return")) {
                String rest = trimmed.substring("@return".length()).trim();
                returnDesc = new StringBuilder(rest);
            } else if (returnDesc != null) {
                if (trimmed.startsWith("@")) {
                    break;
                } else if (!trimmed.isEmpty()) {
                    returnDesc.append(" ").append(trimmed);
                }
            }
        }

        return returnDesc != null ? returnDesc.toString().trim() : null;
    }

    // ======================== 内部：工具方法 ========================

    /**
     * 查找文本中所有 JavaDoc 块
     */
    private List<JavadocBlock> findAllJavadocBlocks(String content) {
        List<JavadocBlock> blocks = new ArrayList<>();
        Matcher matcher = JAVADOC_PATTERN.matcher(content);
        while (matcher.find()) {
            blocks.add(new JavadocBlock(matcher.group(), matcher.start(), matcher.end()));
        }
        return blocks;
    }

    /**
     * 去除文本开头的注解（@Annotation 和 @Annotation(...)）
     */
    private String stripLeadingAnnotations(String text) {
        String result = text;
        while (true) {
            result = result.trim();
            if (result.startsWith("@")) {
                // 找到注解结束位置
                int i = 1;
                while (i < result.length() && Character.isJavaIdentifierPart(result.charAt(i))) {
                    i++;
                }
                // 检查是否有括号
                if (i < result.length() && result.charAt(i) == '(') {
                    int closeParen = findMatchingParen(result, i);
                    if (closeParen >= 0) {
                        i = closeParen + 1;
                    } else {
                        break; // 括号不匹配，放弃
                    }
                }
                result = result.substring(i);
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * 根据反射 Method 构建签名 key
     * <p>
     * 与源码解析的签名 key 格式一致：{@code "方法名(参数类型1,参数类型2)"}
     */
    private String buildSignatureKey(Method method) {
        StringBuilder sb = new StringBuilder(method.getName());
        sb.append("(");
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(paramTypes[i].getSimpleName());
        }
        sb.append(")");
        return sb.toString();
    }

    // ======================== 内部数据结构 ========================

    /** JavaDoc 块信息 */
    private static class JavadocBlock {
        final String content;
        final int start;
        final int end;

        JavadocBlock(String content, int start, int end) {
            this.content = content;
            this.start = start;
            this.end = end;
        }
    }

    /** 方法声明信息 */
    private static class MethodDeclaration {
        final String methodName;
        final List<String> paramTypes;
        final String signatureKey;

        MethodDeclaration(String methodName, List<String> paramTypes, String signatureKey) {
            this.methodName = methodName;
            this.paramTypes = paramTypes;
            this.signatureKey = signatureKey;
        }
    }
}
