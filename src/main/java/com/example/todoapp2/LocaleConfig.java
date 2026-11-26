package com.example.todoapp2;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

@Configuration
public class LocaleConfig implements WebMvcConfigurer {
    @Bean
    MessageSource messageSource() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        messages.setDefaultLocale(Locale.JAPANESE);
        return messages;
    }

    @Bean
    LocalValidatorFactoryBean defaultValidator(MessageSource messages) {
        var validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messages);
        return validator;
    }

    @Bean
    LocaleResolver localeResolver() {
        var resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(Locale.JAPANESE);
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        var interceptor = new LocaleChangeInterceptor() {
            @Override
            protected Locale parseLocaleValue(String value) {
                return switch (value) {
                    case "ja" -> Locale.JAPANESE;
                    case "en" -> Locale.ENGLISH;
                    default -> throw new IllegalArgumentException("Unsupported language");
                };
            }
        };
        interceptor.setParamName("lang");
        interceptor.setHttpMethods("GET", "HEAD");
        interceptor.setIgnoreInvalidLocale(true);
        registry.addInterceptor(interceptor).excludePathPatterns("/css/**", "/mcp");
    }
}
