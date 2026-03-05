package com.costco.gb.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService; // 你自己寫的 JWT 工具類 (負責 parseToken)

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. 取得 Header 中的 Authorization
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userId;

        // 2. 如果沒有 Header，或是格式不對 (不是以 Bearer 開頭)，就直接放行給後面的 Filter 處理 (通常會被擋下)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. 擷取 Token 本體 (去掉 "Bearer " 前 7 個字元)
        jwt = authHeader.substring(7);

        try {
            // 4. 解析 Token 取得 user_id (這裡依賴你的 JwtService 實作)
            userId = jwtService.extractUserId(jwt);

            // 5. 如果有解析出 userId，且目前 SecurityContext 中沒有身分紀錄
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 確認 Token 是否合法 (有沒有過期等)
                if (jwtService.isTokenValid(jwt)) {

                    // 建立身分認證物件 (因為我們不依賴密碼，所以 credentials 放 null)
                    // 如果未來你有權限分級 (例如 ADMIN/USER)，可以把 authorities 塞在第三個參數
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            Collections.emptyList()
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 6. 將身分存入 SecurityContext，代表此 Request 已通過認證！
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token 過期或偽造等例外處理
            // 可以根據需求記錄 Log，或是透過 Response 寫回 401 狀態碼
            logger.error("JWT Validation failed: " + e.getMessage());
        }

        // 7. 繼續執行下一個 Filter 或 Controller
        filterChain.doFilter(request, response);
    }
}