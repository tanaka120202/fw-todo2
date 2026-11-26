package com.example.todoapp2.api;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HolidayApiController {
    private final HolidayClient holidayClient;

    public HolidayApiController(HolidayClient holidayClient) {
        this.holidayClient = holidayClient;
    }

    @GetMapping(value = "/api/holidays", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> holidays(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var result = holidayClient.fetchHolidays();
        if (result.isEmpty()) {
            return ResponseEntity.ok()
                    .header("X-Holidays-Unavailable", "true")
                    .body(Map.of());
        }
        Map<String, String> holidays = result.get();
        if (from == null && to == null) {
            return ResponseEntity.ok(holidays);
        }

        Map<String, String> filtered = new LinkedHashMap<>();
        for (var holiday : holidays.entrySet()) {
            LocalDate date = LocalDate.parse(holiday.getKey());
            if ((from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))) {
                filtered.put(holiday.getKey(), holiday.getValue());
            }
        }
        return ResponseEntity.ok(filtered);
    }
}
