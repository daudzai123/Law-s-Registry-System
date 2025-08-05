package com.mcit.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class JwtUtilityClass {

    private static final String SECRET = "YCfSGDFu+xkCpm8iDAhkJy6VrtaFJE9X1uC5kkA9YVcfN0ARhcuMAeAsMpaLotYYx32HwDSAm7BEqOtpnUOHRA==";
    private static final long ACCESS_TOKEN_VALIDITY = TimeUnit.HOURS.toMillis(48);
    private static final long RESET_TOKEN_VALIDITY = TimeUnit.MINUTES.toMillis(10); // 10 minutes

    // Generate Access Token with roles
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "https://secure.ddr.com");
        List<String> roles = userDetails.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        claims.put("roles", roles);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plusMillis(ACCESS_TOKEN_VALIDITY)))
                .signWith(generateKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    // Generate Reset Token
    public String generateResetToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plusMillis(RESET_TOKEN_VALIDITY)))
                .signWith(generateKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    // Validate Reset Token
    public String validateResetToken(String token) {
        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            Claims claims = getClaims(token);
            Date expiration = claims.getExpiration();
            if (expiration != null && expiration.before(new Date())) {
                return null; // expired
            }
            return claims.getSubject(); // return email/username
        } catch (Exception e) {
            return null; // invalid
        }
    }

    public List<String> extractRoles(String jwt) {
        Claims claims = getClaims(jwt);
        return claims.get("roles", List.class);
    }

    public String extractUsername(String jwt) {
        Claims claims = getClaims(jwt);
        return claims.getSubject();
    }

    public boolean isTokenValid(String jwt) {
        try {
            Claims claims = getClaims(jwt);
            return claims.getExpiration().after(Date.from(Instant.now()));
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String jwt) {
        return Jwts.parserBuilder()
                .setSigningKey(generateKey())
                .build()
                .parseClaimsJws(jwt)
                .getBody();
    }

    private SecretKey generateKey() {
        byte[] decodedKey = Base64.getDecoder().decode(SECRET);
        return Keys.hmacShaKeyFor(decodedKey);
    }
}
