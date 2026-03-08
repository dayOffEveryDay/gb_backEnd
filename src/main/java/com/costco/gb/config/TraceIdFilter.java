package com.costco.gb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 確保在所有 Filter (包含 Security) 之前執行
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 嘗試從前端的 Request Header 獲取 traceId (如果有微服務串聯時很有用)
        String traceId = request.getHeader(TRACE_ID_HEADER);

        // 2. 如果前端沒傳，我們就自己生成一個 UUID (去掉連字號讓它短一點)
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        // 3. 把 traceId 放進 MDC (這會綁定在當前的執行緒 ThreadLocal 裡)
        MDC.put(MDC_TRACE_ID_KEY, traceId);

        // 4. (貼心設計) 把 traceId 也塞進 Response Header 回傳給前端，方便前端工程師報修
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            // 5. 繼續執行後續的 Filter (包含 JWT 驗證與 Controller)
            filterChain.doFilter(request, response);
        } finally {
            // 6. 【超級重要】Request 結束後一定要清除 MDC，否則 Thread Pool 會有 Memory Leak 或資料污染
            MDC.remove(MDC_TRACE_ID_KEY);
        }
    }
}