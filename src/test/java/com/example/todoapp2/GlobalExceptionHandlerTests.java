package com.example.todoapp2;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTests {
    @Test
    void hidesStackTracesInProductionEvenIfDevIsAlsoActive() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(handler("dev", "prod")).build();

        mvc.perform(get("/fail"))
                .andExpect(status().isInternalServerError()).andExpect(view().name("error"))
                .andExpect(model().attributeDoesNotExist("traceDetails"));
        mvc.perform(get("/api/todo/fail"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.trace").doesNotHaveJsonPath())
                .andExpect(content().string(not(containsString("private account information"))));
    }

    @Test
    void showsDevelopmentStackFramesWithoutExceptionMessages() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(handler("dev")).build();

        mvc.perform(get("/fail"))
                .andExpect(status().isInternalServerError())
                .andExpect(model().attribute("traceDetails", containsString("FailingController.fail")))
                .andExpect(model().attribute("traceDetails", not(containsString("private account information"))));
        mvc.perform(get("/api/todo/fail"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.data.trace", containsString("FailingController.fail")))
                .andExpect(content().string(not(containsString("private account information"))));
    }

    private GlobalExceptionHandler handler(String... profiles) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new GlobalExceptionHandler(environment, new LocaleConfig().messageSource());
    }

    @Controller
    static class FailingController {
        @GetMapping({"/fail", "/api/todo/fail"})
        String fail() {
            throw new IllegalStateException("private account information");
        }
    }
}
