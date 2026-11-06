package ru.hotelbooking.hotel.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomCatalogTest {
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    @Autowired
    RoomCatalogTest(MockMvc mvc, ObjectMapper json, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.json = json;
        this.jdbc = jdbc;
    }

    @Test
    void createsRoomAndReadsItFromCardAndCatalog() throws Exception {
        long hotelId = createHotel("Маяк");
        var response =
                createRoom(hotelId, "010A")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse();
        long roomId = json.readTree(response.getContentAsByteArray()).path("id").asLong();

        mvc.perform(get("/api/rooms/{id}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotelId").value(hotelId))
                .andExpect(jsonPath("$.number").value("010A"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.timesBooked").value(0))
                .andExpect(jsonPath("$.hotel").doesNotExist());
        mvc.perform(get("/api/rooms/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %s)].number", roomId).value(contains("010A")));
    }

    @Test
    void requiresUniqueNumberOnlyWithinTheSameHotel() throws Exception {
        long firstHotel = createHotel("Маяк");
        long otherHotel = createHotel("Парк");

        createRoom(firstHotel, "010A").andExpect(status().isCreated());
        createRoom(firstHotel, " 010A ")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NUMBER_CONFLICT"));
        createRoom(otherHotel, "010A")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hotelId").value(otherHotel));
    }

    @Test
    void returnsNotFoundForUnknownHotelAndUnknownRoom() throws Exception {
        createRoom(Long.MAX_VALUE, "010A")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOTEL_NOT_FOUND"));
        mvc.perform(get("/api/rooms/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void validatesHotelIdAndNumber() throws Exception {
        createRoom(0, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.hotelId").exists())
                .andExpect(jsonPath("$.fieldErrors.number").exists());
    }

    @Test
    void databaseEnforcesRoomConstraintsForDirectWrites() throws Exception {
        long hotelId = createHotel("Маяк");
        createRoom(hotelId, "010A").andExpect(status().isCreated());
        String insert =
                "insert into rooms (hotel_id, number, available, times_booked) values (?, ?, true, ?)";

        assertThatThrownBy(() -> jdbc.update(insert, hotelId, "010A", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, Long.MAX_VALUE, "010A", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, hotelId, "010B", -1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsRoomAndHotelWhenForeignKeyPreventsDeletion() throws Exception {
        long hotelId = createHotel("Маяк");
        var response =
                createRoom(hotelId, "010A")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse();
        long roomId = json.readTree(response.getContentAsByteArray()).path("id").asLong();

        mvc.perform(delete("/api/hotels/{id}", hotelId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_CONFLICT"));
        mvc.perform(get("/api/hotels/{id}", hotelId)).andExpect(status().isOk());
        mvc.perform(get("/api/rooms/{id}", roomId)).andExpect(status().isOk());
    }

    private long createHotel(String name) throws Exception {
        var response =
                mvc.perform(
                                post("/api/hotels")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        new CreateHotelRequest(
                                                                name, "ул. Морская, 7"))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse();
        return json.readTree(response.getContentAsByteArray()).path("id").asLong();
    }

    private ResultActions createRoom(long hotelId, String number) throws Exception {
        return mvc.perform(
                post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateRoomRequest(hotelId, number))));
    }
}
