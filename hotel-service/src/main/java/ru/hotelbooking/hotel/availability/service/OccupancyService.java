package ru.hotelbooking.hotel.availability.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import ru.hotelbooking.hotel.availability.client.BookingOccupancy;
import ru.hotelbooking.hotel.availability.dto.HotelOccupancyResponse;
import ru.hotelbooking.hotel.availability.dto.RoomOccupancyResponse;
import ru.hotelbooking.hotel.availability.exception.InvalidReportInputException;
import ru.hotelbooking.hotel.availability.repository.OccupancyRepository;
import ru.hotelbooking.support.dates.BookedStay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

@Service
public class OccupancyService {
    private final OccupancyRepository reports;
    private final BookingOccupancy occupancy;

    OccupancyService(OccupancyRepository reports, BookingOccupancy occupancy) {
        this.reports = reports;
        this.occupancy = occupancy;
    }

    public Page<RoomOccupancyResponse> rooms(
            LocalDate startDate,
            LocalDate endDate,
            Long hotelId,
            Boolean available,
            String sort,
            Pageable page) {
        long nights = reportNights(startDate, endDate);
        Comparator<RoomOccupancyResponse> order = roomOrder(ReportOrder.parse(sort));
        List<RoomOccupancyResponse> rows =
                rows(hotelId, available, startDate, endDate).stream()
                        .filter(row -> row.roomId() != null)
                        .map(row -> room(row, nights))
                        .sorted(order)
                        .toList();
        return page(rows, page);
    }

    public Page<HotelOccupancyResponse> hotels(
            LocalDate startDate,
            LocalDate endDate,
            Long hotelId,
            Boolean available,
            String sort,
            Pageable page) {
        long nights = reportNights(startDate, endDate);
        Comparator<HotelOccupancyResponse> order = hotelOrder(ReportOrder.parse(sort));
        List<HotelOccupancyResponse> rows =
                rows(hotelId, available, startDate, endDate).stream()
                        .collect(Collectors.groupingBy(RoomReportRow::hotelId))
                        .values()
                        .stream()
                        .map(rooms -> hotel(rooms, nights))
                        .sorted(order)
                        .toList();
        return page(rows, page);
    }

    private List<RoomReportRow> rows(
            Long hotelId, Boolean available, LocalDate start, LocalDate end) {
        Map<Long, List<BookedStay>> stays =
                occupancy.find(start, end).stream()
                        .collect(Collectors.groupingBy(BookedStay::roomId));
        return reports.report(hotelId, available).stream()
                .map(
                        row -> {
                            List<BookedStay> occupied =
                                    stays.getOrDefault(row.getRoomId(), List.of());
                            long nights =
                                    occupied.stream()
                                            .mapToLong(
                                                    stay ->
                                                            ChronoUnit.DAYS.between(
                                                                    stay.startDate().isAfter(start)
                                                                            ? stay.startDate()
                                                                            : start,
                                                                    stay.endDate().isBefore(end)
                                                                            ? stay.endDate()
                                                                            : end))
                                            .sum();
                            return new RoomReportRow(
                                    row.getHotelId(),
                                    row.getHotelName(),
                                    row.getRoomId(),
                                    row.getNumber(),
                                    Boolean.TRUE.equals(row.getAvailable()),
                                    row.getTimesBooked() == null ? 0 : row.getTimesBooked(),
                                    occupied.size(),
                                    nights);
                        })
                .toList();
    }

    private record RoomReportRow(
            long hotelId,
            String hotelName,
            Long roomId,
            String number,
            boolean available,
            long timesBooked,
            long allocationCount,
            long occupiedRoomNights) {}

    private static RoomOccupancyResponse room(RoomReportRow row, long nights) {
        boolean available = row.available();
        long occupied = row.occupiedRoomNights();
        long occupiedAvailable = available ? occupied : 0;
        long capacity = available ? nights : 0;
        return new RoomOccupancyResponse(
                row.roomId(),
                row.hotelId(),
                row.number(),
                available,
                row.timesBooked(),
                row.allocationCount(),
                occupied,
                occupiedAvailable,
                capacity,
                percent(occupiedAvailable, capacity));
    }

    private static HotelOccupancyResponse hotel(List<RoomReportRow> rooms, long nights) {
        RoomReportRow hotel = rooms.getFirst();
        long roomCount = 0;
        long allocations = 0;
        long occupied = 0;
        long occupiedAvailable = 0;
        var load = new LongSummaryStatistics();
        for (RoomReportRow room : rooms) {
            if (room.roomId() == null) {
                continue;
            }
            roomCount++;
            allocations += room.allocationCount();
            occupied += room.occupiedRoomNights();
            if (room.available()) {
                occupiedAvailable += room.occupiedRoomNights();
                load.accept(room.timesBooked());
            }
        }
        long capacity = load.getCount() * nights;
        long spread = load.getCount() == 0 ? 0 : load.getMax() - load.getMin();
        return new HotelOccupancyResponse(
                hotel.hotelId(),
                hotel.hotelName(),
                roomCount,
                load.getCount(),
                allocations,
                occupied,
                occupiedAvailable,
                capacity,
                percent(occupiedAvailable, capacity),
                spread);
    }

    private static BigDecimal percent(long occupied, long capacity) {
        if (capacity == 0) {
            return null;
        }
        return BigDecimal.valueOf(occupied)
                .movePointRight(2)
                .divide(BigDecimal.valueOf(capacity), 2, RoundingMode.HALF_UP);
    }

    private static long reportNights(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new InvalidReportInputException("startDate", "Report start date is required");
        }
        if (endDate == null || !endDate.isAfter(startDate)) {
            throw new InvalidReportInputException(
                    "endDate", "Report end date must follow its start");
        }
        return ChronoUnit.DAYS.between(startDate, endDate);
    }

    private static Comparator<RoomOccupancyResponse> roomOrder(ReportOrder order) {
        Comparator<RoomOccupancyResponse> comparator =
                switch (order.field()) {
                    case "id" -> order.byLong(RoomOccupancyResponse::roomId);
                    case "timesBooked" -> order.byLong(RoomOccupancyResponse::timesBooked);
                    case "allocationCount" -> order.byLong(RoomOccupancyResponse::allocationCount);
                    case "occupiedRoomNights" ->
                            order.byLong(RoomOccupancyResponse::occupiedRoomNights);
                    case "occupancyPercent" ->
                            order.byPercent(RoomOccupancyResponse::occupancyPercent);
                    default -> throw invalidSort();
                };
        return comparator.thenComparingLong(RoomOccupancyResponse::roomId);
    }

    private static Comparator<HotelOccupancyResponse> hotelOrder(ReportOrder order) {
        Comparator<HotelOccupancyResponse> comparator =
                switch (order.field()) {
                    case "id" -> order.byLong(HotelOccupancyResponse::hotelId);
                    case "timesBookedSpread" ->
                            order.byLong(HotelOccupancyResponse::timesBookedSpread);
                    case "allocationCount" -> order.byLong(HotelOccupancyResponse::allocationCount);
                    case "occupiedRoomNights" ->
                            order.byLong(HotelOccupancyResponse::occupiedRoomNights);
                    case "occupancyPercent" ->
                            order.byPercent(HotelOccupancyResponse::occupancyPercent);
                    default -> throw invalidSort();
                };
        return comparator.thenComparingLong(HotelOccupancyResponse::hotelId);
    }

    private static <T> Page<T> page(List<T> rows, Pageable page) {
        int from = (int) Math.min(page.getOffset(), rows.size());
        int to = (int) Math.min((long) from + page.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(from, to), page, rows.size());
    }

    private static InvalidReportInputException invalidSort() {
        return new InvalidReportInputException(
                "sort", "Unsupported report sort field or direction");
    }

    private record ReportOrder(String field, boolean descending) {
        static ReportOrder parse(String sort) {
            String[] parts = sort.split(",", -1);
            if (parts.length == 1) {
                return new ReportOrder(parts[0], false);
            }
            if (parts.length != 2 || (!parts[1].equals("asc") && !parts[1].equals("desc"))) {
                throw invalidSort();
            }
            return new ReportOrder(parts[0], parts[1].equals("desc"));
        }

        <T> Comparator<T> byLong(ToLongFunction<T> value) {
            Comparator<T> comparator = Comparator.comparingLong(value);
            return descending ? comparator.reversed() : comparator;
        }

        <T> Comparator<T> byPercent(Function<T, BigDecimal> value) {
            Comparator<BigDecimal> order =
                    descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
            return Comparator.comparing(value, Comparator.nullsLast(order));
        }
    }
}
