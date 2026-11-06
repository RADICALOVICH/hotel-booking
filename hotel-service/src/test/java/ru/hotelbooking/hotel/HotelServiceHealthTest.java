package ru.hotelbooking.hotel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.hotelbooking.hotel.support.HotelTestConfig;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(HotelTestConfig.class)
class HotelServiceHealthTest {
    private final MockMvc mvc;

    @Autowired
    HotelServiceHealthTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void healthReportsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void tracesAuthenticationFailuresBeforeTheController() throws Exception {
        String trace = "0123456789ABCDEF0123456789ABCDEF";
        mvc.perform(get("/api/hotels").header("X-Trace-Id", trace))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Trace-Id", trace))
                .andExpect(jsonPath("$.traceId").value(trace));

        mvc.perform(get("/api/hotels").header("X-Trace-Id", trace, trace))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        result -> {
                            String generated = result.getResponse().getHeader("X-Trace-Id");
                            assertThat(generated)
                                    .matches("[0-9a-f]{32}")
                                    .isNotEqualToIgnoringCase(trace);
                            jsonPath("$.traceId").value(generated).match(result);
                        });
    }
}
