package com.example.todoapp2;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

import com.example.todoapp2.api.TodoApiResponse;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final boolean showTrace;

    private final MessageSource messages;

    public GlobalExceptionHandler(Environment environment, MessageSource messages) {
        this.messages = messages;
        showTrace = environment.acceptsProfiles(Profiles.of("dev & !prod & !production"));
    }

    @ExceptionHandler(Exception.class)
    public Object handle(Exception exception, HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (exception instanceof AccessDeniedException || exception instanceof AuthenticationException) {
            throw exception;
        }

        HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
        HttpHeaders headers = HttpHeaders.EMPTY;
        if (exception instanceof TodoNotFoundException) {
            status = HttpStatus.NOT_FOUND;
        } else if (exception instanceof ErrorResponse error) {
            status = error.getStatusCode();
            headers = error.getHeaders();
        } else if (exception instanceof HttpMessageNotReadableException
                || exception instanceof MethodArgumentTypeMismatchException) {
            status = HttpStatus.BAD_REQUEST;
        }

        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String trace = exception.getClass().getName() + "\n" + Arrays.stream(exception.getStackTrace())
                .map(frame -> "    at " + frame)
                .collect(Collectors.joining("\n"));
        if (status.is5xxServerError()) {
            log.error("HTTP {} {} route={}\n{}", status.value(), request.getMethod(), route, trace);
        } else {
            log.warn("HTTP {} {} route={} exception={}", status.value(), request.getMethod(), route,
                    exception.getClass().getSimpleName());
        }

        String message = messages.getMessage(messageKey(status, exception), null, LocaleContextHolder.getLocale());
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        if (requestPath.startsWith("/api/") || requestPath.equals("/api")) {
            return apiResponse(request, requestPath, status, headers, message, exception,
                    showTrace && status.is5xxServerError() ? trace : null);
        }

        var view = new ModelAndView("error", status);
        view.addObject("status", status.value());
        view.addObject("errorMessage", message);
        if (showTrace && status.is5xxServerError()) {
            view.addObject("traceDetails", trace);
        }
        headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        return view;
    }

    private ResponseEntity<?> apiResponse(HttpServletRequest request, String requestPath, HttpStatusCode status,
                                          HttpHeaders headers, String message, Exception exception, String trace) {
        Object errors = exception instanceof MethodArgumentNotValidException invalid ? validationErrors(invalid) : null;
        if (requestPath.equals("/api/todos") || requestPath.startsWith("/api/todos/")) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
            problem.setInstance(URI.create(request.getRequestURI()));
            if (errors != null) {
                problem.setProperty("errors", errors);
            }
            if (trace != null) {
                problem.setProperty("trace", trace);
            }
            return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }
        Object data = trace == null ? errors : Map.of("trace", trace);
        return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_JSON)
                .body(new TodoApiResponse<>(false, message, data));
    }

    private Object validationErrors(MethodArgumentNotValidException exception) {
        Map<String, String> messages = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            if ("NotBlank".equals(error.getCode())) {
                messages.put(error.getField(), error.getDefaultMessage());
            } else {
                messages.putIfAbsent(error.getField(), error.getDefaultMessage());
            }
        }
        return messages.entrySet().stream()
                .map(entry -> Map.of("field", entry.getKey(), "message", entry.getValue()))
                .toList();
    }

    private String messageKey(HttpStatusCode status, Exception exception) {
        if (exception instanceof TodoNotFoundException) {
            return "error.todoMissing";
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return "error.json";
        }
        if (exception instanceof MethodArgumentTypeMismatchException) {
            return "error.type";
        }
        return switch (status.value()) {
            case 400 -> "error.badRequest";
            case 404 -> "error.notFound";
            case 405 -> "error.method";
            case 413 -> "error.size";
            case 415 -> "error.media";
            case 503 -> "error.busy";
            case 504 -> "error.timeout";
            default -> status.is5xxServerError()
                    ? "error.server"
                    : "error.generic";
        };
    }
}
