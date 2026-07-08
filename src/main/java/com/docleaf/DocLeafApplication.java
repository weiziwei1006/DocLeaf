package com.docleaf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DocLeaf 主启动类
 * <p>
 * DocLeaf 是一个轻量级的 Spring Boot API 文档生成器，核心理念是"零注解、零侵入"——
 * 用户无需添加任何 Swagger 注解，直接从 Spring 原生注解中提取 API 元数据并自动生成文档。
 *
 * @author DocLeaf
 */
@SpringBootApplication
public class DocLeafApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocLeafApplication.class, args);
    }
}
