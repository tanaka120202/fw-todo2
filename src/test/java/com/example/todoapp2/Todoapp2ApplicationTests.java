package com.example.todoapp2;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application-smoke",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "TODO_AUTH_INITIAL_USERNAME=",
        "TODO_AUTH_INITIAL_PASSWORD="
})
class Todoapp2ApplicationTests {

	@Test
	void contextLoads() {
	}

}
