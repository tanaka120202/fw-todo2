package com.example.todoapp2;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.todoapp2.api.TodoApiController;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserMapper users) {
        return username -> {
            UserAccount account = users.findByUsername(username);
            if (account == null) {
                throw new UsernameNotFoundException("ユーザーが見つかりません");
            }
            return new AccountPrincipal(account);
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserMapper users,
            ObjectProvider<McpServerStreamableHttpProperties> mcpProperties) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        var api = paths.matcher("/api/todos/**");
        var todoApi = paths.matcher(TodoApiController.BASE_PATH + "/**");
        var mcp = paths.matcher(mcpProperties.getIfAvailable(
                McpServerStreamableHttpProperties::new).getMcpEndpoint());
        return http.authorizeHttpRequests(requests -> requests
                        .requestMatchers("/css/**", "/error", "/login").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .cors(Customizer.withDefaults())
                // Browser forms cannot send JSON or a DELETE request across origins.
                .csrf(csrf -> csrf.ignoringRequestMatchers(request ->
                        (api.matches(request) || todoApi.matches(request) || mcp.matches(request))
                        && ("DELETE".equals(request.getMethod()) || isJson(request))))
                .formLogin(form -> form.loginPage("/login").permitAll())
                .httpBasic(Customizer.withDefaults())
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                            FilterChain chain) throws ServletException, IOException {
                        var authentication = SecurityContextHolder.getContext().getAuthentication();
                        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal account
                                && users.isCurrent(account.getId(), account.getVersion()) == 0) {
                            new SecurityContextLogoutHandler().logout(request, response, authentication);
                        }
                        chain.doFilter(request, response);
                    }
                }, AuthorizationFilter.class)
                .build();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${todo.api.cors.allowed-origins}") List<String> allowedOrigins) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        cors.setExposedHeaders(List.of("Location"));
        cors.setAllowCredentials(true);
        cors.validateAllowCredentials();

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(TodoApiController.BASE_PATH + "/**", cors);
        return source;
    }

    private boolean isJson(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.matches("(?i)application/json(?:\\s*;.*)?");
    }

    @Bean
    ApplicationRunner initializeAccount(Environment environment, UserMapper users, BCryptPasswordEncoder encoder) {
        return args -> {
            String username = environment.getProperty("TODO_AUTH_INITIAL_USERNAME", "").strip();
            String password = environment.getProperty("TODO_AUTH_INITIAL_PASSWORD", "");
            if (username.isEmpty() && password.isEmpty()) {
                return;
            }
            if (username.isEmpty() || password.isEmpty()) {
                throw new IllegalArgumentException("初回ユーザー名とパスワードを両方指定してください。");
            }
            if (users.findByUsername(username) == null) {
                if (username.length() > 64) {
                    throw new IllegalArgumentException("初回ユーザー名は64文字以内にしてください。");
                }
                var role = UserAccount.Role.valueOf(environment.getProperty("TODO_AUTH_INITIAL_ROLE", "USER"));
                String hash = UserManagementService.hash(encoder, password);
                try {
                    users.insert(username, hash, role);
                } catch (org.springframework.dao.DuplicateKeyException duplicate) {
                    // Another instance initialized the account first.
                }
            }
        };
    }
}
