package ru.hotelbooking.hotel.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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
        postHotel(body).andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForUnknownId() throws Exception {
        mvc.perform(get("/api/hotels/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    private ResultActions postHotel(String body) throws Exception {
        return mvc.perform(
                post("/api/hotels").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
