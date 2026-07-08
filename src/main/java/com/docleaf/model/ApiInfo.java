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

    /** 方法名（作为接口描述的默认值） */
    private String methodName;

    /** 请求路径集合（如 /api/users/{id}），一个方法可能映射多个路径 */
    private Set<String> paths;

    /** 支持的 HTTP 方法集合（如 GET、POST） */
    private Set<String> httpMethods;

    /** 参数列表 */
    private java.util.List<ApiParamInfo> params;

    /** 返回值类型描述（如 User 或 ResponseEntity&lt;User&gt;） */
    private String returnType;

    // ==================== Getters & Setters ====================

    public String getControllerName() {
        return controllerName;
    }

    public void setControllerName(String controllerName) {
        this.controllerName = controllerName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
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

    @Override
    public String toString() {
        return "ApiInfo{" +
                "controllerName='" + controllerName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", paths=" + paths +
                ", httpMethods=" + httpMethods +
                ", params=" + params +
                ", returnType='" + returnType + '\'' +
                '}';
    }
}
