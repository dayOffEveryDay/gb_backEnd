package com.costco.gb.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    // 從 application.yml 讀取密鑰 (Secret Key)
    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    // 從 application.yml 讀取過期時間 (毫秒)
    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    /**
     * 從 Token 中取出使用者的 ID (我們把它當作 Subject 存進去)
     */
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * 驗證 Token 是否合法 (比對 userId 並檢查是否過期)
     */
    public boolean isTokenValid(String token) {
        try {
            // 只要能成功解析且未過期，就代表是我們簽發的合法 Token
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 為使用者生成新的 JWT Token (登入成功時呼叫)
     */
    public String generateToken(Long userId) {
        return generateToken(new HashMap<>(), String.valueOf(userId));
    }

    /**
     * 生成 Token 的底層邏輯
     */
    public String generateToken(Map<String, Object> extraClaims, String subject) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(subject) // 這裡放 userId
                .setIssuedAt(new Date(System.currentTimeMillis())) // 核發時間
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration)) // 過期時間
                .signWith(getSignInKey(), SignatureAlgorithm.HS256) // 使用 HS256 演算法與密鑰簽名
                .compact();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}