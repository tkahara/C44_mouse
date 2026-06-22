package com.kakogawa.traffic.config;

import java.nio.file.Paths;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 💡【PC依存の完全解消】プログラムが動いている場所からの「相対パス」を、自動で絶対URIに変換します
        // これにより、Windows、Mac、どんなPCのどんなフォルダに置かれても自動で正しいパスが構築されます
        String uploadDir = Paths.get("./upload").toAbsolutePath().toUri().toString();
        
        // ブラウザから「/upload/ファイル名」でアクセスされた際に、自動解決された上記の実際のフォルダを見に行く設定
        registry.addResourceHandler("/upload/**")
                .addResourceLocations(uploadDir);
    }
}
