package ru.hotelbooking.booking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ru.hotelbooking.booking.user.UserService;
import ru.hotelbooking.booking.user.UserSession;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(TestJwtKeys.class)
@ActiveProfiles("test")
class JwtSecurityTest {
    private static final String PASSWORD = "test-password-42";
    private static final String VALIDATE = "/internal/auth/validate";

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JwtDecoder decoder;
    private final JwtEncoder encoder;
    private final UserService users;
    private final JdbcTemplate jdbc;
    private final String issuer;

    @Autowired
    JwtSecurityTest(
            MockMvc mvc,
            ObjectMapper json,
            JwtDecoder decoder,
            JwtEncoder encoder,
            UserService users,
            JdbcTemplate jdbc,
            @Value("${booking.jwt.issuer}") String issuer) {
        this.mvc = mvc;
        this.json = json;
        this.decoder = decoder;
        this.encoder = encoder;
        this.users = users;
        this.jdbc = jdbc;
        this.issuer = issuer;
    }

    @Test
    void issuesAnHourLongRsaTokenForRegistrationAndLogin() throws Exception {
        JsonNode registered = register("jwt-guest");
        JsonNode signedIn = login("JWT-GUEST");

        for (JsonNode response : List.of(registered, signedIn)) {
            String token = response.path("accessToken").asText();
            var jwt = decoder.decode(token);
            assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
            assertThat(jwt.getSubject()).isEqualTo(registered.path("id").asText());
            assertThat(jwt.getIssuer().toString()).isEqualTo(issuer);
            assertThat(jwt.getAudience()).containsExactly(JwtTokenService.AUDIENCE);
            assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(3600));
            assertThat(jwt.getClaims())
                    .containsEntry(JwtTokenService.ROLE_CLAIM, "USER")
                    .doesNotContainKeys("password", "passwordHash");
            verifyAccess(token).andExpect(status().isNoContent());
        }
    }

    @Test
    void distinguishesMissingTokenFromInsufficientRole() throws Exception {
        mvc.perform(get(VALIDATE))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String token = register("jwt-role-guest").path("accessToken").asText();
        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void rejectsTokenWithModifiedSignature() throws Exception {
        String[] parts = register("jwt-tamper-guest").path("accessToken").asText().split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 1;
        parts[2] = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        verifyAccess(String.join(".", parts))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @ParameterizedTest
    @EnumSource(InvalidClaim.class)
    void rejectsSignedTokenWithInvalidClaims(InvalidClaim invalid) throws Exception {
        UserSession session =
                users.register("jwt-claim-" + invalid.name().toLowerCase(Locale.ROOT), PASSWORD);
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims =
                JwtClaimsSet.builder()
                        .issuer(invalid == InvalidClaim.ISSUER ? "http://other-issuer" : issuer)
                        .subject(Long.toString(session.user().id()))
                        .audience(
                                List.of(
                                        invalid == InvalidClaim.AUDIENCE
                                                ? "hotel-internal"
                                                : JwtTokenService.AUDIENCE))
                        .issuedAt(now.minusSeconds(3600))
                        .claim(JwtTokenService.ROLE_CLAIM, "USER")
                        .claim(JwtTokenService.VERSION_CLAIM, session.accessVersion().toString());
        if (invalid != InvalidClaim.NO_EXPIRATION) {
            claims.expiresAt(
                    invalid == InvalidClaim.EXPIRED ? now.minusSeconds(1) : now.plusSeconds(60));
        }

        String token =
                encoder.encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(SignatureAlgorithm.RS256).build(),
                                        claims.build()))
                        .getTokenValue();
        verifyAccess(token).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsPreviouslyIssuedTokenAfterAccessVersionChangesOrAccountIsDeleted()
            throws Exception {
        JsonNode first = register("jwt-revoked-guest");
        long id = first.path("id").asLong();
        String oldToken = first.path("accessToken").asText();
        verifyAccess(oldToken).andExpect(status().isNoContent());

        assertThat(
                        jdbc.update(
                                "update users set access_version = ? where id = ?",
                                UUID.randomUUID(),
                                id))
                .isEqualTo(1);
        verifyAccess(oldToken).andExpect(status().isUnauthorized());

        String renewedToken = login("jwt-revoked-guest").path("accessToken").asText();
        verifyAccess(renewedToken).andExpect(status().isNoContent());
        assertThat(jdbc.update("delete from users where id = ?", id)).isEqualTo(1);
        verifyAccess(renewedToken).andExpect(status().isUnauthorized());
    }

    private JsonNode register(String username) throws Exception {
        return json.readTree(
                credentials("/user/register", username)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray());
    }

    private JsonNode login(String username) throws Exception {
        return json.readTree(
                credentials("/user/auth", username)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray());
    }

    private ResultActions credentials(String path, String username) throws Exception {
        return mvc.perform(
                post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                json.writeValueAsBytes(
                                        Map.of("username", username, "password", PASSWORD))));
    }

    private ResultActions verifyAccess(String token) throws Exception {
        return mvc.perform(get(VALIDATE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    enum InvalidClaim {
        EXPIRED,
        NO_EXPIRATION,
        ISSUER,
        AUDIENCE
    }
}
