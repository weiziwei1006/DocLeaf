package com.docleaf.javadoc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 类级别的 JavaDoc 解析结果
 * <p>
 * 封装从源文件中提取的类注释信息，包括：
 * <ul>
 *   <li>类的简短描述（类级 JavaDoc 首句）</li>
 *   <li>该类下所有方法的 JavaDoc 映射</li>
 * </ul>
 * 方法映射的 key 使用 {@code "方法名(参数类型1,参数类型2)"} 格式，
 * 同时也存有仅以方法名为 key 的映射，用于重载场景的回退匹配。
 *
 * @author DocLeaf
 */
public class ClassJavaDoc {

    /** 类的简短描述 */
    private String classDescription;

    /** 方法 JavaDoc 映射，key = "方法名(参数类型1,参数类型2)" */
    private Map<String, MethodJavaDoc> methodDocsBySignature;

    /** 方法 JavaDoc 映射，key = "方法名"（用于无重载时的快速匹配） */
    private Map<String, MethodJavaDoc> methodDocsByName;

    public ClassJavaDoc() {
        this.methodDocsBySignature = new LinkedHashMap<>();
        this.methodDocsByName = new LinkedHashMap<>();
    }

    public String getClassDescription() {
        return classDescription;
    }

    public void setClassDescription(String classDescription) {
        this.classDescription = classDescription;
    }

    public Map<String, MethodJavaDoc> getMethodDocsBySignature() {
        return methodDocsBySignature != null ? methodDocsBySignature : Collections.<String, MethodJavaDoc>emptyMap();
    }

    public void setMethodDocsBySignature(Map<String, MethodJavaDoc> methodDocsBySignature) {
        this.methodDocsBySignature = methodDocsBySignature;
    }

    public Map<String, MethodJavaDoc> getMethodDocsByName() {
        return methodDocsByName != null ? methodDocsByName : Collections.<String, MethodJavaDoc>emptyMap();
    }

    public void setMethodDocsByName(Map<String, MethodJavaDoc> methodDocsByName) {
        this.methodDocsByName = methodDocsByName;
    }
}
