package com.docleaf.model;

/**
 * API 参数信息模型
 * <p>
 * 封装单个接口参数的元数据，包括参数名、来源类型、Java 类型、是否必填等。
 *
 * @author DocLeaf
 */
public class ApiParamInfo {

    /** 参数名 */
    private String name;

    /** 参数来源类型：PathVariable / RequestParam / RequestBody / RequestHeader 等 */
    private String source;

    /** 参数的 Java 类型（如 Long、String、User） */
    private String type;

    /** 是否必填 */
    private boolean required;

    /** 默认值（仅 @RequestParam 等可能存在，无则为 null） */
    private String defaultValue;

    // ==================== Getters & Setters ====================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public String toString() {
        return "ApiParamInfo{" +
                "name='" + name + '\'' +
                ", source='" + source + '\'' +
                ", type='" + type + '\'' +
                ", required=" + required +
                ", defaultValue='" + defaultValue + '\'' +
                '}';
    }
}
