package com.coderag.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA 前后端路由分发配置
 *
 * 路由规则：
 * 1. /api/** → 后端 Controller 处理（优先级最高，由 Spring MVC 分派）
 * 2. 静态资源（js/css/images/fonts 等） → 从 classpath:/static/ 提供
 * 3. 其他非 /api 路径（如 /login、/chat/123 等 SPA 页面路由）→ 返回 index.html
 *    解决 SPA 页面刷新 404 问题
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    /**
     * SPA 路由转发解析器
     * - 请求对应静态文件存在 → 正常返回该文件
     * - 请求路径以 api/ 开头 → 返回 null，交给后端 Controller 或 404
     * - 其他路径（SPA 页面路由）→ 回退到 index.html
     */
    static class SpaResourceResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            // 1. 先尝试按原路径查找静态文件
            Resource resource = super.getResource(resourcePath, location);
            if (resource != null) {
                return resource;
            }
            // 2. /api 路径不回退，交给 Controller 处理或返回 404
            if (resourcePath.startsWith("api/")) {
                return null;
            }
            // 3. 其他路径回退到 index.html（SPA 路由）
            return new ClassPathResource("/static/index.html");
        }
    }
}
