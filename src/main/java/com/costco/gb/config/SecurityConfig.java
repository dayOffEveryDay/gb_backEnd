package com.costco.gb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Basic security filter chain configuration for Spring Boot 3+.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF 令牌，通常在 RESTful API 中會這樣做
            .csrf(csrf -> csrf.disable())
            
            // 定義授權規則
            .authorizeHttpRequests(authorize -> authorize
                // 允許對 /public 開頭的請求 (如果有的話)
                .requestMatchers("/public/**").permitAll()
                // 允許對 /actuator/health 請求 (常見的健康檢查端點)
                .requestMatchers("/actuator/health").permitAll()
                // 其他所有請求都需要認證
                .anyRequest().authenticated()
            )
            
            // 啟用 HTTP Basic 或 Form 登入
            .httpBasic(httpBasic -> httpBasic.disable()) // 禁用 HTTP Basic 認證以使用 Form 或 JWT
            .formLogin(formLogin -> formLogin.permitAll()); // 允許所有人訪問登入頁面

        return http.build();
    }

    /**
     * In-memory user service for demonstration purposes. 
     * This should be replaced by a proper JDBC/JPA/LDAP service in production.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        var user = User.withDefaultPasswordEncoder()
            .username("admin")
            .password("password") // WARNING: Use strong passwords in production!
            .roles("USER", "ADMIN")
            .build();

        return new InMemoryUserDetailsManager(user);
    }
}