package ru.hotelbooking.hotel.availability.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.hotelbooking.hotel.support.HotelTestConfig;

@SpringBootTest(properties = "DEMO_DATE=2026-11-06")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
@Import(HotelTestConfig.class)
class DemoHotelTest {
    @Autowired private DemoAllocations demo;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;

    @Test
    void demoAllocationsAreIdempotentAndCountOnlyActiveStays() {
        demo.afterSingletonsInstantiated();
        assertThat(jdbc.queryForObject("select count(*) from hotels", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from room_allocations", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForList("select times_booked from rooms order by id", Long.class))
                .containsExactly(2L, 0L, 1L, 1L, 0L);
        assertThat(
                        jdbc.queryForObject(
                                "select state from room_allocations where booking_id = 910005",
                                String.class))
                .isEqualTo("RELEASED");
    }

    @Test
    void openApiDescribesProtectedReportsAndExcludesAllocationCommands() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Hotel Service"))
                .andExpect(
                        jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat")
                                .value("JWT"))
                .andExpect(
                        jsonPath("$.paths['/api/hotels/statistics'].get.security[0].bearerAuth")
                                .isArray())
                .andExpect(
                        jsonPath("$.paths['/api/hotels/statistics'].get['x-authorization']")
                                .value("hasRole('ADMIN')"))
                .andExpect(
                        jsonPath("$.paths['/api/rooms/{id}/confirm-availability']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/rooms/{id}/release']").doesNotExist());
    }
}
