package top.ortus.lightmark.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import top.ortus.lightmark.backend.dto.module.TrainCalendarDayResponse;
import top.ortus.lightmark.backend.dto.module.TrainCalendarRequest;
import top.ortus.lightmark.backend.dto.module.TrainSearchRequest;
import top.ortus.lightmark.backend.dto.module.TrainStationOptionsResponse;
import top.ortus.lightmark.backend.dto.module.TrainTicketDTO;
import top.ortus.lightmark.backend.utils.Constant;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

@Service
public class TrainServiceImpl implements TrainService {
    private static final String TRAIN_TYPE_HIGH_SPEED = "\u9ad8\u94c1";
    private static final String TRAIN_TYPE_BULLET = "\u52a8\u8f66";
    private static final String TRAIN_TYPE_NORMAL = "\u666e\u901f";
    private static final List<String> SEAT_ORDER = List.of("商务座", "一等座", "二等座", "软卧", "硬卧", "硬座");
    private static final List<String> TRANSFER_FALLBACK_STATIONS = List.of(
        "石家庄", "郑州", "济南", "南京", "西安", "武汉", "上海", "天津", "保定", "太原南",
        "徐州", "合肥", "长沙", "南昌", "杭州", "成都", "重庆", "广州", "深圳"
    );
    private static final Map<String, String> API_SEAT_NAMES = Map.of(
        "business", "商务座",
        "first_class", "一等座",
        "second_class", "二等座",
        "soft_sleeper", "软卧",
        "hard_sleeper", "硬卧",
        "hard_seat", "硬座"
    );

    private final TrainMcpClient trainMcpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CacheEntry> ticketCache = new ConcurrentHashMap<>();

    public TrainServiceImpl(TrainMcpClient trainMcpClient, ObjectMapper objectMapper) {
        this.trainMcpClient = trainMcpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TrainTicketDTO> search(TrainSearchRequest request) {
        if (request == null || isBlank(request.getStartStation()) || isBlank(request.getEndStation())) {
            return List.of();
        }
        List<String> dates = resolveSearchDates(request);
        return dates.stream()
            .flatMap(date -> queryTickets(request.getStartStation(), request.getEndStation(), date, request.getTrainTypes(), request.getSeatTypes()).stream())
            .sorted(Comparator
                .comparing((TrainTicketDTO ticket) -> String.valueOf(ticket.getExtra().get("date")))
                .thenComparing(ticket -> String.valueOf(ticket.getExtra().get("depart_time")))
                .thenComparing(TrainTicketDTO::getName))
            .toList();
    }

    @Override
    public List<TrainTicketDTO> searchTransfers(TrainSearchRequest request) {
        if (request == null || isBlank(request.getStartStation()) || isBlank(request.getEndStation())) {
            return List.of();
        }
        List<String> dates = resolveSearchDates(request);
        return dates.stream()
            .flatMap(date -> queryTransfers(request.getStartStation(), request.getEndStation(), date, request.getTrainTypes(), request.getSeatTypes()).stream())
            .sorted(Comparator
                .comparing((TrainTicketDTO ticket) -> String.valueOf(ticket.getExtra().get("date")))
                .thenComparing(ticket -> String.valueOf(ticket.getExtra().get("depart_time")))
                .thenComparing(TrainTicketDTO::getName))
            .toList();
    }

    @Override
    public List<TrainCalendarDayResponse> calendar(TrainCalendarRequest request) {
        if (request == null || isBlank(request.getStartStation()) || isBlank(request.getEndStation()) || isBlank(request.getMonth())) {
            return List.of();
        }
        YearMonth month = YearMonth.parse(request.getMonth());
        List<TrainCalendarDayResponse> days = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 10_000L;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            if (System.currentTimeMillis() >= deadline) {
                break;
            }
            String date = month.atDay(day).toString();
            TrainCalendarDayResponse stat = queryCalendarDay(request.getStartStation(), request.getEndStation(), date, request.getTrainTypes(), request.getSeatTypes());
            if (stat.getTicketCount() > 0 || stat.getTrainCount() > 0) {
                days.add(stat);
            }
        }
        return days;
    }

    @Override
    public TrainTicketDTO searchById(String ticketId) {
        return decodeTicketId(ticketId);
    }

    @Override
    public TrainStationOptionsResponse stationOptions() {
        List<String> stations = List.of(Constant.stations);
        return new TrainStationOptionsResponse(stations, stations, defaultDateOptions());
    }

    public TrainTicketDTO decodeTicketId(String ticketId) {
        if (ticketId == null || !ticketId.startsWith("MCP:")) {
            throw new IllegalArgumentException("远程车票信息无效");
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(ticketId.substring(4)), StandardCharsets.UTF_8);
            return objectMapper.readValue(json, TrainTicketDTO.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("远程车票信息解析失败");
        }
    }

    private List<TrainTicketDTO> queryTickets(String startStation, String endStation, String date, List<String> trainTypes, List<String> seatTypes) {
        String cacheKey = cacheKey(startStation, endStation, date, trainTypes, seatTypes);
        CacheEntry cached = ticketCache.get(cacheKey);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            return cached.tickets();
        }
        Map<String, Object> ticketResponse = queryTicketTool(startStation, endStation, date);
        Map<String, Object> priceResponse = queryTicketPriceTool(startStation, endStation, date);
        Map<String, Map<String, Double>> pricesByTrainCode = pricesByTrainCode(priceResponse);
        List<Map<String, Object>> trains = readTrainRows(ticketResponse);
        List<TrainTicketDTO> tickets = trains.stream()
            .map(train -> toTicket(startStation, endStation, date, train, pricesByTrainCode))
            .filter(ticket -> ticketMatchesTrainTypes(ticket, trainTypes))
            .filter(ticket -> ticketMatchesSeatTypes(ticket, seatTypes))
            .toList();
        ticketCache.put(cacheKey, new CacheEntry(tickets, System.currentTimeMillis() + 2 * 60 * 1000L));
        return tickets;
    }

    private Map<String, Object> queryTicketTool(String startStation, String endStation, String date) {
        Map<String, Object> arguments = Map.of(
            "from_station", startStation,
            "to_station", endStation,
            "train_date", date
        );
        return trainMcpClient.callToolFast("query-tickets", arguments);
    }

    private Map<String, Object> queryTicketPriceTool(String startStation, String endStation, String date) {
        Map<String, Object> arguments = Map.of(
            "from_station", startStation,
            "to_station", endStation,
            "train_date", date
        );
        try {
            return trainMcpClient.callToolFast("query_ticket_price", arguments);
        } catch (RuntimeException ex) {
            // Price lookup is supplementary; keep ticket availability usable if the tool is unavailable.
            return Map.of();
        }
    }

    private List<TrainTicketDTO> queryTransfers(String startStation, String endStation, String date, List<String> trainTypes, List<String> seatTypes) {
        String cacheKey = "TRANSFER_V2|" + cacheKey(startStation, endStation, date, trainTypes, seatTypes);
        CacheEntry cached = ticketCache.get(cacheKey);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            return cached.tickets();
        }
        Map<String, Object> transferResponse = queryTransferTool(startStation, endStation, date);
        List<Map<String, Object>> transferRows = readTransferRows(transferResponse);
        if (transferRows.isEmpty() && !Boolean.TRUE.equals(transferResponse.get("success"))) {
            transferRows = fallbackTransferRows(startStation, endStation, date);
        }
        List<TrainTicketDTO> tickets = transferRows.stream()
            .map(transfer -> toTransferTicket(startStation, endStation, date, transfer))
            .filter(Objects::nonNull)
            .filter(ticket -> transferMatchesTrainTypes(ticket, trainTypes))
            .filter(ticket -> transferMatchesSeatTypes(ticket, seatTypes))
            .toList();
        ticketCache.put(cacheKey, new CacheEntry(tickets, System.currentTimeMillis() + 2 * 60 * 1000L));
        return tickets;
    }

    private Map<String, Object> queryTransferTool(String startStation, String endStation, String date) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("from_station", startStation);
        arguments.put("to_station", endStation);
        arguments.put("train_date", date);
        arguments.put("middle_station", "");
        arguments.put("isShowWZ", "N");
        arguments.put("purpose_codes", "00");
        return trainMcpClient.callToolFast("query-transfer", arguments);
    }

    private List<Map<String, Object>> fallbackTransferRows(String startStation, String endStation, String date) {
        List<Map<String, Object>> rows = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 12_000L;
        for (String middleStation : TRANSFER_FALLBACK_STATIONS) {
            if (System.currentTimeMillis() >= deadline || rows.size() >= 20) {
                break;
            }
            if (middleStation.equals(startStation) || middleStation.equals(endStation)) {
                continue;
            }
            List<Map<String, Object>> firstLegs = readTrainRows(queryTicketTool(startStation, middleStation, date));
            if (firstLegs.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> secondLegs = readTrainRows(queryTicketTool(middleStation, endStation, date));
            if (secondLegs.isEmpty()) {
                continue;
            }
            for (Map<String, Object> firstLeg : firstLegs.stream().limit(8).toList()) {
                for (Map<String, Object> secondLeg : secondLegs.stream().limit(24).toList()) {
                    if (!isReasonableTransfer(firstLeg, secondLeg)) {
                        continue;
                    }
                    Map<String, Object> transfer = new LinkedHashMap<>();
                    transfer.put("middle_station", middleStation);
                    transfer.put("wait_time", calculateWaitTime(firstLeg, secondLeg));
                    transfer.put("total_duration", calculateTotalDuration(firstLeg, secondLeg));
                    transfer.put("segments", List.of(firstLeg, secondLeg));
                    rows.add(transfer);
                    if (rows.size() >= 20) {
                        return rows;
                    }
                }
            }
        }
        return rows;
    }

    private TrainCalendarDayResponse queryCalendarDay(String startStation, String endStation, String date, List<String> trainTypes, List<String> seatTypes) {
        Map<String, Object> ticketResponse = queryTicketTool(startStation, endStation, date);
        Map<String, Map<String, Double>> pricesByTrainCode = pricesByTrainCode(
            queryTicketPriceTool(startStation, endStation, date));
        List<TrainTicketDTO> tickets = readTrainRows(ticketResponse).stream()
            .map(train -> toTicket(startStation, endStation, date, train, pricesByTrainCode))
            .filter(ticket -> ticketMatchesTrainTypes(ticket, trainTypes))
            .filter(ticket -> ticketMatchesSeatTypes(ticket, seatTypes))
            .toList();
        int ticketCount = tickets.stream().mapToInt(ticket -> Objects.requireNonNullElse(ticket.getStock(), 0)).sum();
        int trainCount = tickets.size();
        return new TrainCalendarDayResponse(date, ticketCount, trainCount);
    }

    private String cacheKey(String startStation, String endStation, String date, List<String> trainTypes, List<String> seatTypes) {
        return startStation + "|" + endStation + "|" + date + "|"
            + String.join(",", trainTypes == null ? List.of() : trainTypes) + "|"
            + String.join(",", seatTypes == null ? List.of() : seatTypes);
    }

    private TrainTicketDTO toTicket(String startStation, String endStation, String date, Map<String, Object> train, Map<String, Map<String, Double>> pricesByTrainCode) {
        String trainCode = extractTrainNo(train);
        String trainType = resolveTrainType(trainCode);
        Map<String, Integer> parsedSeats = normalizeSeats(readMap(firstValue(train, "seats", "seat_info", "tickets")));
        final Map<String, Integer> seats = parsedSeats.isEmpty() ? defaultSeatsForTrainType(trainType) : orderSeats(parsedSeats);
        Map<String, Double> prices = completePrices(
            pricesByTrainCode.getOrDefault(trainCode, Map.of()),
            trainType,
            seats.keySet()
        );
        List<String> tags = new ArrayList<>();
        tags.add(trainType);
        tags.addAll(seats.keySet());
        double minPrice = seats.keySet().stream()
            .map(prices::get)
            .filter(Objects::nonNull)
            .min(Double::compareTo)
            .orElse(0.0);
        int stock = seats.values().stream().mapToInt(Integer::intValue).sum();

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("start_station", startStation);
        extra.put("end_station", endStation);
        extra.put("date", date);
        extra.put("depart_time", firstText(train.get("start_time"), train.get("depart_time")));
        extra.put("arrive_time", firstText(train.get("arrive_time"), train.get("arrival_time")));
        extra.put("duration", valueOf(train.get("duration")));
        extra.put("train_type", trainType);
        extra.put("train_no", trainCode);
        extra.put("train_code", trainCode);

        TrainTicketDTO ticket = new TrainTicketDTO(null, trainCode, minPrice, stock, 0, tags, extra, seats, prices);
        ticket.setId(encodeTicketId(ticket));
        return ticket;
    }

    private TrainTicketDTO toTransferTicket(String startStation, String endStation, String date, Map<String, Object> transfer) {
        List<Map<String, Object>> rawSegments = readList(transfer.get("segments"));
        if (rawSegments.size() < 2) {
            return null;
        }

        List<TrainTicketDTO> segmentTickets = rawSegments.stream()
            .limit(2)
            .map(segment -> toTransferSegmentTicket(startStation, endStation, date, segment))
            .toList();
        if (segmentTickets.size() < 2) {
            return null;
        }

        TrainTicketDTO first = segmentTickets.get(0);
        TrainTicketDTO second = segmentTickets.get(1);
        Map<String, Integer> seats = mergeSeats(first.getSeats(), second.getSeats());
        Map<String, Double> prices = mergeTransferPrices(first.getPrices(), second.getPrices(), seats.keySet());
        List<String> tags = new ArrayList<>();
        tags.addAll(segmentTickets.stream()
            .map(ticket -> String.valueOf(ticket.getExtra().get("train_type")))
            .distinct()
            .toList());
        tags.addAll(seats.keySet());
        double minPrice = prices.values().stream().filter(price -> price > 0).min(Double::compareTo).orElse(0.0);
        int stock = seats.values().stream().mapToInt(Integer::intValue).sum();
        String firstTrain = first.getName();
        String secondTrain = second.getName();

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("transfer", true);
        extra.put("start_station", startStation);
        extra.put("end_station", endStation);
        extra.put("middle_station", firstText(transfer.get("middle_station"), first.getExtra().get("end_station")));
        extra.put("date", date);
        extra.put("depart_time", first.getExtra().get("depart_time"));
        extra.put("arrive_time", second.getExtra().get("arrive_time"));
        extra.put("duration", firstText(transfer.get("total_duration"), transfer.get("duration")));
        extra.put("wait_time", firstText(transfer.get("wait_time"), transfer.get("transfer_time")));
        extra.put("train_type", String.join("/", tags.stream().filter(type -> List.of("高铁", "动车", "普速").contains(type)).toList()));
        extra.put("segments", segmentTickets.stream().map(this::ticketSnapshot).toList());

        TrainTicketDTO ticket = new TrainTicketDTO(null, firstTrain + " / " + secondTrain, minPrice, stock, 0, tags, extra, seats, prices);
        ticket.setId(encodeTicketId(ticket));
        return ticket;
    }

    private TrainTicketDTO toTransferSegmentTicket(String startStation, String endStation, String date, Map<String, Object> segment) {
        String from = firstText(segment.get("from_station"), segment.get("start_station"));
        String to = firstText(segment.get("to_station"), segment.get("end_station"));
        String trainNo = extractTrainNo(segment);
        TrainTicketDTO ticket = toTicket(
            isBlank(from) ? startStation : from,
            isBlank(to) ? endStation : to,
            date,
            segment,
            Map.of()
        );
        Map<String, Object> extra = new LinkedHashMap<>(ticket.getExtra() == null ? Map.of() : ticket.getExtra());
        extra.put("train_no", trainNo);
        extra.put("train_code", trainNo);
        extra.put("train_type", resolveTrainType(trainNo));
        ticket.setName(trainNo);
        ticket.setExtra(extra);
        return ticket;
    }

    private Map<String, Object> ticketSnapshot(TrainTicketDTO ticket) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", ticket.getId());
        snapshot.put("name", ticket.getName());
        snapshot.put("price", ticket.getPrice());
        snapshot.put("stock", ticket.getStock());
        snapshot.put("extra", ticket.getExtra());
        snapshot.put("train_no", ticket.getExtra() == null ? ticket.getName() : firstText(ticket.getExtra().get("train_no"), ticket.getName()));
        snapshot.put("train_type", ticket.getExtra() == null ? resolveTrainType(ticket.getName()) : firstText(ticket.getExtra().get("train_type"), resolveTrainType(ticket.getName())));
        snapshot.put("seats", ticket.getSeats());
        snapshot.put("prices", ticket.getPrices());
        return snapshot;
    }

    private Map<String, Integer> mergeSeats(Map<String, Integer> firstSeats, Map<String, Integer> secondSeats) {
        Map<String, Integer> seats = new LinkedHashMap<>();
        for (String seat : SEAT_ORDER) {
            boolean exists = firstSeats.containsKey(seat) || secondSeats.containsKey(seat);
            if (exists) {
                int firstCount = firstSeats.getOrDefault(seat, 0);
                int secondCount = secondSeats.getOrDefault(seat, 0);
                seats.put(seat, firstCount + secondCount);
            }
        }
        return seats;
    }

    private Map<String, Double> mergeTransferPrices(Map<String, Double> firstPrices, Map<String, Double> secondPrices, Set<String> seats) {
        Map<String, Double> prices = new LinkedHashMap<>();
        double firstFallback = minPositivePrice(firstPrices);
        double secondFallback = minPositivePrice(secondPrices);
        for (String seat : SEAT_ORDER) {
            if (!seats.contains(seat)) {
                continue;
            }
            double first = firstPrices.getOrDefault(seat, firstFallback);
            double second = secondPrices.getOrDefault(seat, secondFallback);
            prices.put(seat, Math.round((first + second) * 10.0) / 10.0);
        }
        return prices;
    }

    private double minPositivePrice(Map<String, Double> prices) {
        return prices.values().stream()
            .filter(price -> price != null && price > 0)
            .min(Double::compareTo)
            .orElse(0.0);
    }

    private Map<String, Map<String, Double>> pricesByTrainCode(Map<String, Object> priceResponse) {
        Map<String, Map<String, Double>> result = new LinkedHashMap<>();
        for (Map<String, Object> item : readList(priceResponse.get("data"))) {
            String trainCode = firstText(item.get("train_code"), item.get("train_no"));
            if (!isBlank(trainCode)) {
                result.put(trainCode, normalizePrices(readMap(item.get("prices"))));
            }
        }
        return result;
    }

    private String encodeTicketId(TrainTicketDTO ticket) {
        try {
            String json = objectMapper.writeValueAsString(ticket);
            return "MCP:" + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalArgumentException("远程车票信息编码失败");
        }
    }

    private List<String> resolveSearchDates(TrainSearchRequest request) {
        if (!isBlank(request.getDate())) {
            return List.of(request.getDate());
        }
        YearMonth month = !isBlank(request.getMonth()) ? YearMonth.parse(request.getMonth()) : YearMonth.now();
        LocalDate start = month.equals(YearMonth.now()) ? LocalDate.now() : month.atDay(1);
        return List.of(start.toString());
    }

    private boolean ticketMatchesTrainTypes(TrainTicketDTO ticket, List<String> trainTypes) {
        if (trainTypes == null || trainTypes.isEmpty()) {
            return true;
        }
        String trainNo = firstText(
            ticket.getExtra() == null ? null : ticket.getExtra().get("train_no"),
            ticket.getExtra() == null ? null : ticket.getExtra().get("train_code"),
            ticket.getName()
        );
        String actualType = resolveTrainType(trainNo);
        return trainTypes.stream()
            .map(this::normalizeTrainType)
            .anyMatch(actualType::equals);
    }

    private boolean ticketMatchesSeatTypes(TrainTicketDTO ticket, List<String> seatTypes) {
        return seatTypes == null || seatTypes.isEmpty() || seatTypes.stream().anyMatch(seat -> ticket.getSeats().containsKey(seat));
    }

    private boolean transferMatchesTrainTypes(TrainTicketDTO ticket, List<String> trainTypes) {
        if (trainTypes == null || trainTypes.isEmpty()) {
            return true;
        }
        return transferSegments(ticket).stream()
            .map(segment -> resolveTrainType(firstText(
                segment.get("train_no"),
                readMap(segment.get("extra")).get("train_no"),
                readMap(segment.get("extra")).get("train_code"),
                segment.get("name")
            )))
            .anyMatch(actualType -> trainTypes.stream().map(this::normalizeTrainType).anyMatch(actualType::equals));
    }

    private boolean transferMatchesSeatTypes(TrainTicketDTO ticket, List<String> seatTypes) {
        if (seatTypes == null || seatTypes.isEmpty()) {
            return true;
        }
        return transferSegments(ticket).stream()
            .map(segment -> readMap(segment.get("seats")))
            .anyMatch(seats -> seatTypes.stream().anyMatch(seats::containsKey));
    }

    private Map<String, Integer> normalizeSeats(Map<String, Object> rawSeats) {
        Map<String, Integer> seats = new LinkedHashMap<>();
        rawSeats.forEach((key, value) -> {
            String seatName = normalizeSeatName(key);
            if (SEAT_ORDER.contains(seatName)) {
                seats.put(seatName, parseSeatCount(value));
            }
        });
        return seats;
    }

    private Map<String, Integer> defaultSeatsForTrainType(String trainType) {
        Map<String, Integer> seats = new LinkedHashMap<>();
        if (TRAIN_TYPE_NORMAL.equals(trainType)) {
            seats.put("硬座", 20);
            seats.put("硬卧", 10);
            seats.put("软卧", 6);
        } else {
            seats.put("二等座", 20);
            seats.put("一等座", 10);
            seats.put("商务座", 5);
        }
        return seats;
    }

    private Map<String, Integer> orderSeats(Map<String, Integer> rawSeats) {
        Map<String, Integer> seats = new LinkedHashMap<>();
        for (String seat : SEAT_ORDER) {
            if (rawSeats.containsKey(seat)) {
                seats.put(seat, Math.max(0, rawSeats.getOrDefault(seat, 0)));
            }
        }
        return seats;
    }

    private Map<String, Double> normalizePrices(Map<String, Object> rawPrices) {
        Map<String, Double> prices = new LinkedHashMap<>();
        rawPrices.forEach((key, value) -> {
            String seatName = normalizeSeatName(key);
            if (SEAT_ORDER.contains(seatName)) {
                prices.put(seatName, parseDouble(value));
            }
        });
        return prices;
    }

    private Map<String, Double> defaultPrices(String trainType, Set<String> seats) {
        Map<String, Double> prices = new LinkedHashMap<>();
        double base = TRAIN_TYPE_HIGH_SPEED.equals(trainType) ? 180.0 : TRAIN_TYPE_BULLET.equals(trainType) ? 120.0 : 80.0;
        for (String seat : seats) {
            double multiplier = switch (seat) {
                case "商务座" -> 3.0;
                case "一等座" -> 1.8;
                case "二等座" -> 1.0;
                case "软卧" -> 1.6;
                case "硬卧" -> 1.1;
                case "硬座" -> 0.7;
                default -> 1.0;
            };
            prices.put(seat, Math.round(base * multiplier * 10.0) / 10.0);
        }
        return prices;
    }

    private Map<String, Double> completePrices(Map<String, Double> rawPrices, String trainType, Set<String> seats) {
        Map<String, Double> defaults = defaultPrices(trainType, seats);
        Map<String, Double> prices = new LinkedHashMap<>();
        for (String seat : SEAT_ORDER) {
            if (!seats.contains(seat)) {
                continue;
            }
            double price = rawPrices.getOrDefault(seat, defaults.getOrDefault(seat, 0.0));
            prices.put(seat, price);
        }
        return prices;
    }

    private boolean isReasonableTransfer(Map<String, Object> firstLeg, Map<String, Object> secondLeg) {
        LocalTime arrive = parseTime(firstText(firstLeg.get("arrive_time"), firstLeg.get("arrival_time")));
        LocalTime depart = parseTime(firstText(secondLeg.get("start_time"), secondLeg.get("depart_time")));
        if (arrive == null || depart == null) {
            return true;
        }
        long minutes = Duration.between(arrive, depart).toMinutes();
        return minutes >= 15 && minutes <= 360;
    }

    private String calculateWaitTime(Map<String, Object> firstLeg, Map<String, Object> secondLeg) {
        LocalTime arrive = parseTime(firstText(firstLeg.get("arrive_time"), firstLeg.get("arrival_time")));
        LocalTime depart = parseTime(firstText(secondLeg.get("start_time"), secondLeg.get("depart_time")));
        if (arrive == null || depart == null) {
            return "";
        }
        long minutes = Duration.between(arrive, depart).toMinutes();
        if (minutes < 0) {
            minutes += 24 * 60;
        }
        return formatMinutes(minutes);
    }

    private String calculateTotalDuration(Map<String, Object> firstLeg, Map<String, Object> secondLeg) {
        LocalTime depart = parseTime(firstText(firstLeg.get("start_time"), firstLeg.get("depart_time")));
        LocalTime arrive = parseTime(firstText(secondLeg.get("arrive_time"), secondLeg.get("arrival_time")));
        if (depart == null || arrive == null) {
            return "";
        }
        long minutes = Duration.between(depart, arrive).toMinutes();
        if (minutes < 0) {
            minutes += 24 * 60;
        }
        return formatMinutes(minutes);
    }

    private String formatMinutes(long minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    private LocalTime parseTime(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveTrainType(String trainNo) {
        if (isBlank(trainNo)) {
            return TRAIN_TYPE_NORMAL;
        }
        char first = Character.toUpperCase(trainNo.charAt(0));
        if (first == 'G') {
            return TRAIN_TYPE_HIGH_SPEED;
        }
        if (first == 'D') {
            return TRAIN_TYPE_BULLET;
        }
        return TRAIN_TYPE_NORMAL;
    }

    private String normalizeTrainType(String trainType) {
        if (isBlank(trainType)) {
            return "";
        }
        String text = trainType.trim();
        if (TRAIN_TYPE_HIGH_SPEED.equals(text) || "G".equalsIgnoreCase(text) || text.toLowerCase().contains("high")) {
            return TRAIN_TYPE_HIGH_SPEED;
        }
        if (TRAIN_TYPE_BULLET.equals(text) || "D".equalsIgnoreCase(text) || text.toLowerCase().contains("bullet")) {
            return TRAIN_TYPE_BULLET;
        }
        if (TRAIN_TYPE_NORMAL.equals(text) || "K".equalsIgnoreCase(text) || "T".equalsIgnoreCase(text) || "Z".equalsIgnoreCase(text)) {
            return TRAIN_TYPE_NORMAL;
        }
        return text;
    }

    private int parseSeatCount(Object value) {
        if (value == null) {
            return 0;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank() || "--".equals(text) || "无".equals(text)) {
            return 0;
        }
        if ("有".equals(text)) {
            return 20;
        }
        if (text.contains("有") || text.contains("充足")) {
            return 20;
        }
        if (text.contains("候补") || text.contains("无") || text.contains("售完")) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(text));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private double parseDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value).replace("¥", "").trim());
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private String normalizeSeatName(String seatName) {
        return Optional.ofNullable(seatName)
            .map(name -> name.replace(" ", "").trim())
            .map(name -> API_SEAT_NAMES.getOrDefault(name, name))
            .orElse("");
    }

    private List<Map<String, Object>> readTrainRows(Map<String, Object> response) {
        List<Map<String, Object>> trains = readList(response.get("trains"));
        if (!trains.isEmpty()) {
            return trains;
        }
        trains = readList(response.get("data"));
        if (!trains.isEmpty()) {
            return trains;
        }
        trains = readList(response.get("tickets"));
        if (!trains.isEmpty()) {
            return trains;
        }
        return List.of();
    }

    private List<Map<String, Object>> readTransferRows(Map<String, Object> response) {
        List<Map<String, Object>> transfers = readList(response.get("transfers"));
        if (!transfers.isEmpty()) {
            return transfers;
        }
        transfers = readList(response.get("data"));
        if (!transfers.isEmpty()) {
            return transfers;
        }
        return List.of();
    }

    private List<Map<String, Object>> transferSegments(TrainTicketDTO ticket) {
        if (ticket == null || ticket.getExtra() == null) {
            return List.of();
        }
        return readList(ticket.getExtra().get("segments"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Object firstValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String extractTrainNo(Map<String, Object> map) {
        String direct = firstText(
            map.get("train_no"),
            map.get("trainNo"),
            map.get("train_code"),
            map.get("trainCode"),
            map.get("station_train_code"),
            map.get("stationTrainCode"),
            map.get("train_number"),
            map.get("trainNumber")
        );
        if (!isBlank(direct)) {
            return direct;
        }
        return findTrainNo(map, 0);
    }

    @SuppressWarnings("unchecked")
    private String findTrainNo(Object value, int depth) {
        if (value == null || depth > 4) {
            return "";
        }
        if (value instanceof Map<?, ?> map) {
            String direct = firstText(
                map.get("train_no"),
                map.get("trainNo"),
                map.get("train_code"),
                map.get("trainCode"),
                map.get("station_train_code"),
                map.get("stationTrainCode"),
                map.get("train_number"),
                map.get("trainNumber")
            );
            if (!isBlank(direct)) {
                return direct;
            }
            for (Object nested : map.values()) {
                String found = findTrainNo(nested, depth + 1);
                if (!isBlank(found)) {
                    return found;
                }
            }
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String found = findTrainNo(item, depth + 1);
                if (!isBlank(found)) {
                    return found;
                }
            }
        }
        return "";
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = valueOf(value);
            if (!isBlank(text)) {
                return text;
            }
        }
        return "";
    }

    private String valueOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<String> defaultDateOptions() {
        LocalDate start = LocalDate.now();
        return IntStream.range(0, 180)
            .mapToObj(offset -> start.plusDays(offset).toString())
            .toList();
    }

    private record CacheEntry(List<TrainTicketDTO> tickets, long expiresAt) {
    }
}
