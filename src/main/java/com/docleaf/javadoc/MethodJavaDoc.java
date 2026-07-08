package com.docleaf.javadoc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 方法的 JavaDoc 解析结果
 * <p>
 * 封装从源文件中提取的方法注释信息，包括：
 * <ul>
 *   <li>方法描述（首句摘要）</li>
 *   <li>{@code @param} 参数描述映射</li>
 *   <li>{@code @return} 返回值描述</li>
 * </ul>
 *
 * @author DocLeaf
 */
public class MethodJavaDoc {

    /** 方法的简短描述（JavaDoc 首句） */
    private String description;

    /** 参数描述映射：参数名 → 描述文本 */
    private Map<String, String> paramDescriptions;

    /** 返回值描述 */
    private String returnDescription;

    public MethodJavaDoc() {
        this.paramDescriptions = new LinkedHashMap<>();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getParamDescriptions() {
        return paramDescriptions != null ? paramDescriptions : Collections.<String, String>emptyMap();
    }

    public void setParamDescriptions(Map<String, String> paramDescriptions) {
        this.paramDescriptions = paramDescriptions;
    }

    public String getReturnDescription() {
        return returnDescription;
    }

    public void setReturnDescription(String returnDescription) {
        this.returnDescription = returnDescription;
    }

    @Override
    public String toString() {
        return "MethodJavaDoc{" +
                "description='" + description + '\'' +
                ", paramDescriptions=" + paramDescriptions +
                ", returnDescription='" + returnDescription + '\'' +
                '}';
    }
}
