package com.costco.gb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 🌟 告訴總機小姐：
        // 當收到 "/images/任何檔名" 的網址請求時 (虛擬路徑)
        registry.addResourceHandler("/images/**")
                // 請去專案根目錄的 "uploads/campaigns/" 資料夾找檔案 (實體路徑)
                // 注意：前面的 "file:" 絕對不能漏掉！它代表去本機硬碟找，而不是去編譯好的 jar 檔裡面找。
                .addResourceLocations("file:uploads/campaigns/");
    }
}