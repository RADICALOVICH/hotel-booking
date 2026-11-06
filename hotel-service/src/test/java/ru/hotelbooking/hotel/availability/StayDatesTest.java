package ru.hotelbooking.hotel.availability;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ru.hotelbooking.hotel.availability.exception.InvalidStayDatesException;
import ru.hotelbooking.hotel.availability.service.StayDates;
import ru.hotelbooking.hotel.support.HotelTestConfig;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Today is 2026-11-06 in Moscow; offsets are days from today. */
class StayDatesTest {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @ParameterizedTest(name = "check-in +{0}, check-out +{1} is accepted")
    @CsvSource({"0, 1", "0, 30", "365, 366", "10, 12"})
    void acceptsStaysWithinTheRules(int startOffset, int endOffset) {
        StayDates dates = stayDates();

        assertThatCode(() -> dates.requireBookable(day(startOffset), day(endOffset)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "check-in +{0}, check-out +{1} fails on {2}")
    @CsvSource({
        "5, 5, endDate",
        "6, 5, endDate",
        "-1, 2, startDate",
        "0, 31, endDate",
        "366, 367, startDate"
    })
    void rejectsStaysOutsideTheRules(int startOffset, int endOffset, String field) {
        StayDates dates = stayDates();

        assertThatThrownBy(() -> dates.requireBookable(day(startOffset), day(endOffset)))
                .isInstanceOf(InvalidStayDatesException.class)
                .hasFieldOrPropertyWithValue("field", field);
    }

    @ParameterizedTest(name = "missing {0}")
    @CsvSource({"startDate", "endDate"})
    void requiresBothDates(String missing) {
        StayDates dates = stayDates();
        LocalDate start = missing.equals("startDate") ? null : day(1);
        LocalDate end = missing.equals("endDate") ? null : day(2);

        assertThatThrownBy(() -> dates.requireBookable(start, end))
                .hasFieldOrPropertyWithValue("field", missing);
    }

    private static LocalDate day(int offset) {
        return HotelTestConfig.TODAY.plusDays(offset);
    }

    /** 00:30 in Moscow is still the previous day in UTC, so the zone really matters here. */
    private static StayDates stayDates() {
        return new StayDates(
                Clock.fixed(
                        HotelTestConfig.TODAY.atTime(0, 30).atZone(MOSCOW).toInstant(), MOSCOW));
    }
}
