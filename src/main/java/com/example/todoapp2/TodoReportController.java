package com.example.todoapp2;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class TodoReportController {
    private final TodoReportService reports;
    private final Duration timeout;

    public TodoReportController(TodoReportService reports, @Value("${todo.report.timeout:5s}") Duration timeout) {
        this.reports = reports;
        this.timeout = timeout;
    }

    @GetMapping("/todos/report.csv")
    public ResponseEntity<byte[]> download(@RequestParam(defaultValue = "") String keyword,
                                           @RequestParam(defaultValue = "") String category,
                                           @RequestParam(defaultValue = "asc") String order,
                                           @AuthenticationPrincipal AccountPrincipal actor) {
        try {
            var future = reports.generate(keyword, category, order, actor);
            byte[] csv = future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            String filename = "todo_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                    .body(csv);
        } catch (TaskRejectedException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, null, exception);
        } catch (TimeoutException exception) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, null, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, null, exception);
        } catch (ExecutionException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, null, exception.getCause());
        }
    }
}
