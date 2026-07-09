package com.docleaf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * DocLeaf 配置属性类
 * <p>
 * 通过 {@code application.properties} 或 {@code application.yml} 配置，
 * 支持自定义输出目录和输出格式。
 * <p>
 * 配置示例：
 * <pre>
 * docleaf.output.dir=./docs
 * docleaf.output.formats=markdown,openapi,html
 * </pre>
 *
 * @author DocLeaf
 */
@Component
@ConfigurationProperties(prefix = "docleaf")
public class DocLeafProperties {

    /** 源码根目录（相对于 user.dir，用于 JavaDoc 提取） */
    private String sourceRoot = "src/main/java";

    /** 是否启用 JavaDoc 注释提取 */
    private boolean javadocEnabled = true;

    /** 输出配置 */
    private Output output = new Output();

    // ==================== Output 内部类 ====================

    public static class Output {

        /**
         * 输出目录（相对于 user.dir）
         * <p>
         * 默认为当前目录（即 user.dir），即输出到项目根目录。
         */
        private String dir = "outdoc";

        /**
         * 输出格式列表
         * <p>
         * 可选值：markdown, openapi, html
         * 默认全部输出。
         */
        private List<String> formats = Arrays.asList("markdown", "openapi", "html");

        // ==================== Getters & Setters ====================

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public List<String> getFormats() {
            return formats;
        }

        public void setFormats(List<String> formats) {
            this.formats = formats;
        }

        /**
         * 判断是否需要输出指定格式
         */
        public boolean shouldOutput(String format) {
            return formats != null && formats.contains(format);
        }
    }

    // ==================== Getters & Setters ====================

    public String getSourceRoot() {
        return sourceRoot;
    }

    public void setSourceRoot(String sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    public boolean isJavadocEnabled() {
        return javadocEnabled;
    }

    public void setJavadocEnabled(boolean javadocEnabled) {
        this.javadocEnabled = javadocEnabled;
    }

    public Output getOutput() {
        return output;
    }

    public void setOutput(Output output) {
        this.output = output;
    }
}
