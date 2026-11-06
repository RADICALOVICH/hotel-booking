package ru.hotelbooking.hotel.catalog;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ru.hotelbooking.hotel.support.AdminTokenByDefault;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminTokenByDefault.class)
class HotelCatalogTest {
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    @Autowired
    HotelCatalogTest(MockMvc mvc, ObjectMapper json, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.json = json;
        this.jdbc = jdbc;
    }

    @Test
    void createsHotelInH2AndReadsItByGeneratedId() throws Exception {
        var response =
                postHotel(
                                """
                        {"name":"Отель Маяк","address":"ул. Морская, 7"}
                        """)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.name").value("Отель Маяк"))
                        .andExpect(jsonPath("$.address").value("ул. Морская, 7"))
                        .andReturn()
                        .getResponse();

        long id = json.readTree(response.getContentAsByteArray()).path("id").asLong();
        String location = response.getHeader("Location");
        assertThat(id).isPositive();
        assertThat(location).isEqualTo("/api/hotels/" + id);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from hotels where id = ? and name = ? and address = ?",
                                Integer.class,
                                id,
                                "Отель Маяк",
                                "ул. Морская, 7"))
                .isEqualTo(1);

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Отель Маяк"))
                .andExpect(jsonPath("$.address").value("ул. Морская, 7"));

        var second =
                postHotel(
                                """
                        {"name":"Отель Парк","address":"ул. Садовая, 2"}
                        """)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse();
        assertThat(json.readTree(second.getContentAsByteArray()).path("id").asLong())
                .isPositive()
                .isNotEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"name\":\" \",\"address\":\"ул. Морская, 7\"}",
                "{\"name\":\"Отель Маяк\",\"address\":\" \"}"
            })
    void rejectsMissingOrBlankFields(String body) throws Exception {
        postHotel(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void returnsNotFoundForUnknownId() throws Exception {
        mvc.perform(get("/api/hotels/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOTEL_NOT_FOUND"));
        patchHotel("/api/hotels/" + Long.MAX_VALUE, "{\"address\":\"ул. Морская, 9\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOTEL_NOT_FOUND"));
    }

    @Test
    void patchesListsAndDeletesHotel() throws Exception {
        String location = createHotel();
        long id = Long.parseLong(location.substring("/api/hotels/".length()));

        patchHotel(location, "{\"address\":\"ул. Морская, 9\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Отель Маяк"))
                .andExpect(jsonPath("$.address").value("ул. Морская, 9"));

        patchHotel(location, "{\"name\":\"Новый Маяк\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Новый Маяк"))
                .andExpect(jsonPath("$.address").value("ул. Морская, 9"));

        mvc.perform(get("/api/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %s)].name", id).value(contains("Новый Маяк")))
                .andExpect(
                        jsonPath("$[?(@.id == %s)].address", id).value(contains("ул. Морская, 9")));

        mvc.perform(delete(location)).andExpect(status().isNoContent());
        mvc.perform(get("/api/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %s)]", id).isEmpty());
        mvc.perform(delete(location))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOTEL_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value(location))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "null",
                "[]",
                "{",
                "{\"name\":null}",
                "{\"address\":null}",
                "{\"name\":5}",
                "{\"name\":\"Маяк\",\"adress\":\"ул. Морская, 9\"}"
            })
    void rejectsInvalidPatchBodies(String body) throws Exception {
        patchHotel("/api/hotels/1", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.path").value("/api/hotels/1"));
    }

    @Test
    void rejectsBlankFieldBeforeApplyingAnyPatchChanges() throws Exception {
        String location = createHotel();
        patchHotel(location, "{\"name\":\"  \",\"address\":\"ул. Морская, 9\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Отель Маяк"))
                .andExpect(jsonPath("$.address").value("ул. Морская, 7"));
    }

    @Test
    void returnsErrorDtoForInvalidPathId() throws Exception {
        mvc.perform(get("/api/hotels/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HTTP_400"))
                .andExpect(jsonPath("$.path").value("/api/hotels/abc"));
    }

    @Test
    void filtersByNameAndAddressIgnoringCaseAndPaginates() throws Exception {
        postHotel("{\"name\":\"Северная звезда\",\"address\":\"Набережная, 5\"}")
                .andExpect(status().isCreated());
        postHotel("{\"name\":\"Южная звезда\",\"address\":\"Набережная, 6\"}")
                .andExpect(status().isCreated());
        postHotel("{\"name\":\"Звезда парка\",\"address\":\"Парковая, 1\"}")
                .andExpect(status().isCreated());

        mvc.perform(
                        get("/api/hotels")
                                .param("name", "ЗВЕЗДА")
                                .param("address", "набережная")
                                .param("page", "1")
                                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[*].name").value(contains("Южная звезда")));
        mvc.perform(get("/api/hotels").param("name", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HTTP_400"));
    }

    private String createHotel() throws Exception {
        return postHotel("{\"name\":\"Отель Маяк\",\"address\":\"ул. Морская, 7\"}")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
    }

    private ResultActions patchHotel(String location, String body) throws Exception {
        return mvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions postHotel(String body) throws Exception {
        return mvc.perform(
                post("/api/hotels").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
