package ru.hotelbooking.hotel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.hotelbooking.hotel.catalog.controller.HotelController;
import ru.hotelbooking.hotel.catalog.dto.CreateHotelRequest;
import ru.hotelbooking.hotel.support.HotelTestConfig;
import ru.hotelbooking.hotel.support.StubAccessVerifier;
import ru.hotelbooking.hotel.support.TestTokens;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(HotelTestConfig.class)
@ActiveProfiles("test")
class HotelSecurityTest {
    private static final String HOTEL_JSON =
            "{\"name\":\"Отель Тест\",\"address\":\"ул. Тестовая, 1\"}";

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final TestTokens tokens;
    private final StubAccessVerifier booking;
    private final HotelController hotels;

    @Autowired
    HotelSecurityTest(
            MockMvc mvc,
            ObjectMapper json,
            TestTokens tokens,
            StubAccessVerifier booking,
            HotelController hotels) {
        this.mvc = mvc;
        this.json = json;
        this.tokens = tokens;
        this.booking = booking;
        this.hotels = hotels;
    }

    @BeforeEach
    @AfterEach
    void resetBookingAndContext() {
        booking.reset();
        SecurityContextHolder.clearContext();
    }

    @Test
    void userReadsCatalogButOnlyAdminChangesIt() throws Exception {
        String user = tokens.user();
        long id = createHotel(tokens.admin());

        send(user, get("/api/hotels")).andExpect(status().isOk());
        send(user, get("/api/hotels/" + id)).andExpect(status().isOk());
        for (MockHttpServletRequestBuilder write :
                List.of(
                        post("/api/hotels")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(HOTEL_JSON),
                        patch("/api/hotels/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Взлом\"}"),
                        delete("/api/hotels/" + id),
                        get("/api/rooms/catalog"))) {
            send(user, write)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        send(tokens.admin(), get("/api/hotels/" + id))
                .andExpect(jsonPath("$.name").value("Отель Тест"));
    }

    @Test
    void requestsWithoutTokenGet401ButHealthIsPublic() throws Exception {
        for (MockHttpServletRequestBuilder request :
                List.of(get("/api/hotels"), get("/api/rooms/catalog"), delete("/api/hotels/1"))) {
            mvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        assertThat(booking.checkedTokens()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(InvalidToken.class)
    void rejectsTokensThatHotelCannotTrust(InvalidToken invalid) throws Exception {
        send(invalid.create(tokens), get("/api/hotels"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertThat(booking.checkedTokens()).isEmpty();
    }

    @Test
    void revokedUserTokenGets401AfterBookingCheck() throws Exception {
        String token = tokens.user();
        send(token, get("/api/hotels")).andExpect(status().isOk());

        booking.revoke(token);

        send(token, get("/api/hotels"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertThat(booking.checkedTokens()).containsExactly(token, token);
    }

    @Test
    void unavailableBookingCheckGives503AndDoesNotRunTheRequest() throws Exception {
        String name = "Отель при недоступном Booking";
        booking.makeUnavailable();

        send(
                        tokens.admin(),
                        post("/api/hotels")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("name", name, "address", "ул. Тихая, 3"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_SERVICE_UNAVAILABLE"));

        booking.reset();
        send(tokens.admin(), get("/api/hotels").param("name", name))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"));
    }

    @Test
    void onlyServiceTokenPassesInternalRoomRules() throws Exception {
        for (String path : List.of("/api/rooms/1/confirm-availability", "/api/rooms/1/release")) {
            send(tokens.user(), post(path)).andExpect(status().isForbidden());
            send(tokens.admin(), post(path)).andExpect(status().isForbidden());
            send(tokens.service(), post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        send(tokens.service(), get("/api/hotels")).andExpect(status().isForbidden());
        assertThat(booking.checkedTokens()).hasSize(4);
    }

    @Test
    void preAuthorizeGuardsControllerWithoutHttpRules() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "tester", null, AuthorityUtils.createAuthorityList("ROLE_USER")));

        assertThatThrownBy(() -> hotels.create(new CreateHotelRequest("Отель", "Адрес")))
                .isInstanceOf(AccessDeniedException.class);
    }

    private long createHotel(String adminToken) throws Exception {
        return json.readTree(
                        send(
                                        adminToken,
                                        post("/api/hotels")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(HOTEL_JSON))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsByteArray())
                .path("id")
                .asLong();
    }

    private ResultActions send(String token, MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    enum InvalidToken {
        TAMPERED_SIGNATURE(tokens -> tamper(tokens.user())),
        FOREIGN_KEY(tokens -> tokens.signWith(TestTokens.encoderFor(newKeyPair()))),
        EXPIRED(
                tokens ->
                        tokens.userWith(
                                claims ->
                                        claims.issuedAt(Instant.now().minusSeconds(7200))
                                                .expiresAt(Instant.now().minusSeconds(1)))),
        WRONG_ISSUER(tokens -> tokens.userWith(claims -> claims.issuer("http://elsewhere"))),
        WRONG_AUDIENCE(tokens -> tokens.userWith(claims -> claims.audience(List.of("other-api")))),
        BOTH_AUDIENCES(
                tokens ->
                        tokens.userWith(
                                claims ->
                                        claims.audience(
                                                List.of("hotel-booking-api", "hotel-internal")))),
        UNKNOWN_ROLE(tokens -> tokens.userWith(claims -> claims.claim("role", "SERVICE"))),
        NO_ACCESS_VERSION(
                tokens ->
                        tokens.userWith(
                                claims -> claims.claims(map -> map.remove("access_version")))),
        SERVICE_TOKEN_FROM_OTHER_SUBJECT(
                tokens ->
                        tokens.userWith(
                                claims ->
                                        claims.subject("hotel-service")
                                                .audience(List.of("hotel-internal"))));

        private final Function<TestTokens, String> factory;

        InvalidToken(Function<TestTokens, String> factory) {
            this.factory = factory;
        }

        String create(TestTokens tokens) {
            return factory.apply(tokens);
        }

        private static String tamper(String token) {
            String[] parts = token.split("\\.");
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            signature[0] ^= 1;
            parts[2] = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            return String.join(".", parts);
        }

        private static KeyPair newKeyPair() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                return generator.generateKeyPair();
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
