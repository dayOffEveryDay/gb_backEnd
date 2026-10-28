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

    private final JwtService jwtService;

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
            // 4. 透過 JwtService 解析 Token 取得 user_id
            userId = jwtService.extractUserId(jwt);

            // 5. 如果有解析出 userId，且目前 SecurityContext 中沒有身分紀錄
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 確認 Token 是否合法 (有沒有過期等)
                if (jwtService.isTokenValid(jwt)) {

                    // JWT 驗證不使用密碼，因此 credentials 設為 null
                    // 目前尚未設定角色權限，因此 authorities 使用空集合
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
            logger.warn("event=JWT_VALIDATION_REJECTED reasonCode=INVALID_TOKEN errorType=" + e.getClass().getSimpleName());
        }

        // 7. 繼續執行下一個 Filter 或 Controller
        filterChain.doFilter(request, response);
    }
}
