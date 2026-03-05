package com.costco.gb.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. 關閉 CSRF (REST API + JWT 架構不需要)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. 設定 CORS (允許前端跨域請求)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. 設定 Session 為無狀態 (Stateless)，不使用 Cookie/Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. 設定 API 路由的存取權限
                .authorizeHttpRequests(auth -> auth
                        // 【完全公開的 API】(permitAll)
                        .requestMatchers("/api/v1/auth/line").permitAll() // Line 登入不需要 Token
                        .requestMatchers("/api/v1/system/**").permitAll() // 系統資料(門市/分類/節日)大家都能看

                        // 【選擇性公開的 API】(視你的業務邏輯而定)
                        // 例如：首頁的合購清單大家都能看，但要「加入」才需要登入
                        .requestMatchers(HttpMethod.GET, "/api/v1/campaigns").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/campaigns/*").permitAll()

                        // 【其他所有的 API 都必須帶合法的 JWT Token】(authenticated)
                        .anyRequest().authenticated()
                )

                // 5. 將我們自訂的 JWT 攔截器，安插在 UsernamePasswordAuthenticationFilter 之前執行
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS 跨域設定細節
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 填入你 React 前端運行的網址 (本機開發通常是 3000 或 5173)
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true); // 允許攜帶認證資訊

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // 套用到所有路由
        return source;
    }
}
