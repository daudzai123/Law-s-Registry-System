package com.mcit.security;

import com.mcit.service.MyUserDetailService;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final MyUserDetailService userDetailService;

    public SecurityConfiguration(@Lazy MyUserDetailService userDetailService) {
        this.userDetailService = userDetailService;
    }

    /* -------------------- Common Beans -------------------- */

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(userDetailService);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return userDetailService;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /* -------------------- Security Filter Chain -------------------- */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authenticationProvider(authenticationProvider())

                .authorizeHttpRequests(registry -> {

                    // ✅ allow CORS preflight
                    registry.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    registry.requestMatchers(HttpMethod.GET, "/api/laws/**").permitAll();


                    // ✅ public endpoints
                    registry.requestMatchers(
                            "/home",
                            "/api/register/**",
                            "/api/authenticate",
                            "/api/verify-email",
                            "/api/users/**",
                            "/api/account/**",
                            "/api/laws/download_attachment/**",
                            "/api/laws/view_attachment/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/swagger-ui/index.html",
                            "/v3/api-docs/**",
                            "/v3/api-docs.yaml",
                            "/webjars/**",
                            "/api/laws"
                    ).permitAll();

                    // ✅ static frontend files (if any)
                    registry.requestMatchers(
                            "/", "/index.html", "/static/**", "/assets/**", "/favicon.ico"
                    ).permitAll();

                    // 🔐 secure all remaining API endpoints
                    registry.requestMatchers("/api/**").authenticated();

                    // allow anything else
                    registry.anyRequest().permitAll();
                })

                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /* -------------------- CORS Configuration -------------------- */

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",
//                "https://localhost:3000",
                "http://103.132.98.92:3000",
//                "https://103.132.98.92:3000",
                "http://103.132.98.92"
//                "https://103.132.98.92",
//                "http://ictinnovation.gov.af",
//                "https://ictinnovation.gov.af"
        ));

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
