package top.ortus.lightmark.product.dto;

import java.math.BigDecimal;

public record RoomDTO(Long roomId, Long hotelId, String roomName, String bedType, String area,
                      Integer breakfast, String cancelPolicy, BigDecimal pricePerNight,
                      BigDecimal totalPrice, String checkInDate, String checkOutDate, long nights) { }
