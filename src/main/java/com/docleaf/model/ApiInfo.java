package com.docleaf.model;

import java.util.Set;

/**
 * API 接口信息模型
 * <p>
 * 封装从 Spring MVC 映射中提取的单个接口的完整元数据。
 *
 * @author DocLeaf
 */
public class ApiInfo {

    /** 所属 Controller 类的简单名称（如 UserController） */
    private String controllerName;

    /** Controller 的描述（来自类级 JavaDoc 首句，无则为 null） */
    private String controllerDescription;

    /** 方法名（作为接口描述的默认值） */
    private String methodName;

    /** 接口描述（来自方法 JavaDoc 首句，无则为方法名） */
    private String description;

    /** 请求路径集合（如 /api/users/{id}），一个方法可能映射多个路径 */
    private Set<String> paths;

    /** 支持的 HTTP 方法集合（如 GET、POST） */
    private Set<String> httpMethods;

    /** 参数列表 */
    private java.util.List<ApiParamInfo> params;

    /** 返回值类型描述（如 User 或 ResponseEntity&lt;User&gt;） */
    private String returnType;

    /** 返回值描述（来自 @return 标签，无则为 null） */
    private String returnDescription;

    // ==================== Getters & Setters ====================

    public String getControllerName() {
        return controllerName;
    }

    public void setControllerName(String controllerName) {
        this.controllerName = controllerName;
    }

    public String getControllerDescription() {
        return controllerDescription;
    }

    public void setControllerDescription(String controllerDescription) {
        this.controllerDescription = controllerDescription;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<String> getPaths() {
        return paths;
    }

    public void setPaths(Set<String> paths) {
        this.paths = paths;
    }

    public Set<String> getHttpMethods() {
        return httpMethods;
    }

    public void setHttpMethods(Set<String> httpMethods) {
        this.httpMethods = httpMethods;
    }

    public java.util.List<ApiParamInfo> getParams() {
        return params;
    }

    public void setParams(java.util.List<ApiParamInfo> params) {
        this.params = params;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public String getReturnDescription() {
        return returnDescription;
    }

    public void setReturnDescription(String returnDescription) {
        this.returnDescription = returnDescription;
    }

    @Override
    public String toString() {
        return "ApiInfo{" +
                "controllerName='" + controllerName + '\'' +
                ", controllerDescription='" + controllerDescription + '\'' +
                ", methodName='" + methodName + '\'' +
                ", description='" + description + '\'' +
                ", paths=" + paths +
                ", httpMethods=" + httpMethods +
                ", params=" + params +
                ", returnType='" + returnType + '\'' +
                ", returnDescription='" + returnDescription + '\'' +
                '}';
    }
}
