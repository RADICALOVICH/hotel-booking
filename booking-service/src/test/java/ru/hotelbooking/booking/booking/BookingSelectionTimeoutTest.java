package ru.hotelbooking.booking.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.hotelbooking.booking.security.TestJwtKeys;
import ru.hotelbooking.booking.support.TestAccounts;
import ru.hotelbooking.booking.user.service.UserService;
import ru.hotelbooking.support.dates.StayDateRules;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(properties = "booking.selection-timeout=1ns")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(TestJwtKeys.class)
@ActiveProfiles("test")
class BookingSelectionTimeoutTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserService users;

    @Test
    void expiredSelectionCancelsPendingAndCanBeReplayed() throws Exception {
        var guest = new TestAccounts(mvc, json, users).user("selection-timeout-guest");
        var start = LocalDate.now(StayDateRules.BUSINESS_ZONE).plusDays(1);
        String request =
                json.writeValueAsString(
                        Map.of(
                                "requestId",
                                UUID.randomUUID(),
                                "autoSelect",
                                true,
                                "startDate",
                                start,
                                "endDate",
                                start.plusDays(1)));

        mvc.perform(
                        post("/booking")
                                .header(HttpHeaders.AUTHORIZATION, guest.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("ROOM_SELECTION_TIMEOUT"))
                .andExpect(jsonPath("$.bookingId").isNumber());
        mvc.perform(
                        post("/booking")
                                .header(HttpHeaders.AUTHORIZATION, guest.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.compensationPending").value(false))
                .andExpect(jsonPath("$.roomId").doesNotExist());
    }
}
