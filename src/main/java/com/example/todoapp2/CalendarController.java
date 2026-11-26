package com.example.todoapp2;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class CalendarController {
    @GetMapping("/calendar")
    public String calendar(@RequestParam(name = "year", required = false) Integer year,
                           @RequestParam(name = "month", required = false) Integer month,
                           Model model) {
        YearMonth current = YearMonth.now(ZoneId.of("Asia/Tokyo"));
        YearMonth selected;
        YearMonth previous;
        YearMonth next;
        try {
            selected = YearMonth.of(year == null ? current.getYear() : year,
                    month == null ? current.getMonthValue() : month);
            previous = selected.minusMonths(1);
            next = selected.plusMonths(1);
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "年月が正しくありません", exception);
        }

        LocalDate firstDay = selected.atDay(1);
        LocalDate lastDay = selected.atEndOfMonth();
        int leadingCells = firstDay.getDayOfWeek().getValue() % 7;
        List<List<LocalDate>> weeks = new ArrayList<>();
        for (int start = 1 - leadingCells; start <= lastDay.getDayOfMonth(); start += 7) {
            List<LocalDate> week = new ArrayList<>(7);
            for (int day = start; day < start + 7; day++) {
                week.add(day >= 1 && day <= lastDay.getDayOfMonth() ? selected.atDay(day) : null);
            }
            weeks.add(week);
        }

        model.addAttribute("selected", selected);
        model.addAttribute("previous", previous);
        model.addAttribute("next", next);
        model.addAttribute("firstDay", firstDay);
        model.addAttribute("lastDay", lastDay);
        model.addAttribute("weeks", weeks);
        return "calendar";
    }
}
