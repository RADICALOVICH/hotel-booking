package ru.hotelbooking.hotel.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ru.hotelbooking.hotel.catalog.dto.CreateHotelRequest;
import ru.hotelbooking.hotel.catalog.dto.CreateRoomRequest;

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

    @Test
    void changesNumberAndAvailabilityTogetherAndRejectsDuplicateNumber() throws Exception {
        long hotelId = createHotel("Маяк");
        String room = createRoomPath(hotelId, "010A");
        createRoom(hotelId, "010B").andExpect(status().isCreated());

        patchRoom(room, "{\"number\":\"010B\",\"available\":false}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NUMBER_CONFLICT"));
        mvc.perform(get(room))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("010A"))
                .andExpect(jsonPath("$.available").value(true));

        patchRoom(room, "{\"number\":\" 010C \",\"available\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("010C"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.hotelId").value(hotelId))
                .andExpect(jsonPath("$.timesBooked").value(0));
        mvc.perform(get(room))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("010C"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"hotelId\":1}",
                "{\"available\":null}",
                "{\"available\":\"false\"}",
                "{\"number\":\" \"}"
            })
    void rejectsInvalidRoomPatch(String body) throws Exception {
        patchRoom("/api/rooms/1", body).andExpect(status().isBadRequest());
    }

    @Test
    void deletesRoomAndThenAllowsDeletingItsHotel() throws Exception {
        long hotelId = createHotel("Маяк");
        String room = createRoomPath(hotelId, "010A");

        mvc.perform(delete(room)).andExpect(status().isNoContent());
        mvc.perform(get(room)).andExpect(status().isNotFound());
        mvc.perform(delete(room))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
        mvc.perform(delete("/api/hotels/{id}", hotelId)).andExpect(status().isNoContent());
    }

    @Test
    void filtersCatalogAndReturnsStablePages() throws Exception {
        long hotelId = createHotel("Маяк");
        createRoomPath(hotelId, "010A");
        createRoomPath(hotelId, "010B");
        String disabled = createRoomPath(hotelId, "010C");
        patchRoom(disabled, "{\"available\":false}").andExpect(status().isOk());
        createRoomPath(createHotel("Парк"), "010D");

        String query = "/api/rooms/catalog?hotelId=" + hotelId + "&available=true&size=1";
        mvc.perform(get(query + "&page=0"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[*].number").value(contains("010A")));
        mvc.perform(get(query + "&page=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].number").value(contains("010B")));
        mvc.perform(get("/api/rooms/catalog?hotelId=" + hotelId + "&available=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].number").value(contains("010C")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "hotelId=0",
                "available=maybe",
                "page=-1",
                "size=0",
                "size=101",
                "page=2147483647&size=100"
            })
    void rejectsInvalidCatalogParameters(String query) throws Exception {
        mvc.perform(get("/api/rooms/catalog?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HTTP_400"));
    }

    private String createRoomPath(long hotelId, String number) throws Exception {
        return createRoom(hotelId, number)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
    }

    private ResultActions patchRoom(String path, String body) throws Exception {
        return mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(body));
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
