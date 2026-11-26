package com.example.todoapp2.mcp;

import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.json.JsonMapper;

import com.example.todoapp2.AccountPrincipal;

@Configuration
@ConditionalOnProperty(name = "spring.ai.mcp.server.protocol", havingValue = "STREAMABLE")
class McpSecurityConfig {
    static final String ACCOUNT = "todoAccount";

    @Bean
    WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
            @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
            McpServerStreamableHttpProperties properties) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint(properties.getMcpEndpoint())
                .keepAliveInterval(properties.getKeepAliveInterval())
                .disallowDelete(properties.isDisallowDelete())
                .contextExtractor(request -> request.principal()
                        .filter(Authentication.class::isInstance)
                        .map(Authentication.class::cast)
                        .filter(auth -> auth.getPrincipal() instanceof AccountPrincipal)
                        .map(auth -> McpTransportContext.create(Map.of(ACCOUNT, auth.getPrincipal())))
                        .orElse(McpTransportContext.EMPTY))
                .build();
    }
}
