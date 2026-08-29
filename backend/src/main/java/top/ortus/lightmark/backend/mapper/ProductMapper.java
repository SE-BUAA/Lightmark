package top.ortus.lightmark.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;
import top.ortus.lightmark.backend.dao.Product;
import top.ortus.lightmark.backend.dto.HotelSearchDTO;

import java.math.BigDecimal;

@Mapper
@Repository("hotelSearchProductMapper")
public interface ProductMapper extends BaseMapper<Product> {

    @Select("""
            <script>
            SELECT
                product.id AS id,
                product.name AS name,
                product.extra AS extraJson,
                COALESCE(MIN(rt.price), product.price, 0) AS priceMin,
                MIN(rt.cancel_policy) AS cancelPolicy,
                4.5 AS rating,
                <choose>
                    <when test="query.lat != null and query.lng != null">
                        0
                    </when>
                    <otherwise>
                        NULL
                    </otherwise>
                </choose> AS distance
            FROM product
            LEFT JOIN room_type rt ON rt.hotel_id = product.id
            ${ew.customSqlSegment}
            <if test="query.starLevel != null">
              AND product.extra LIKE CONCAT('%"starLevel":', #{query.starLevel}, '%')
            </if>
            <if test="query.brand != null and query.brand != ''">
              AND product.extra LIKE CONCAT('%"brand":"', #{query.brand}, '"%')
            </if>
            <if test="query.facility != null and query.facility != ''">
              AND product.extra LIKE CONCAT('%"', #{query.facility}, '"%')
            </if>
            <if test="query.cancelPolicy != null and query.cancelPolicy != ''">
              AND rt.cancel_policy = #{query.cancelPolicy}
            </if>
            GROUP BY product.id, product.name, product.price, product.extra
            <if test="(query.lat != null and query.lng != null and query.radius != null) or query.maxPrice != null">
            HAVING 1 = 1
              <if test="query.lat != null and query.lng != null and query.radius != null">
              AND distance &lt;= #{query.radius}
              </if>
              <if test="query.maxPrice != null">
              AND priceMin &lt;= #{query.maxPrice}
              </if>
            </if>
            <choose>
                <when test='query.sort != null and query.sort.equals("price_asc")'>
                    ORDER BY priceMin ASC, product.id ASC
                </when>
                <when test='query.sort != null and query.sort.equals("price_desc")'>
                    ORDER BY priceMin DESC, product.id ASC
                </when>
                <when test='query.sort != null and query.sort.equals("rating_asc")'>
                    ORDER BY rating ASC, product.id ASC
                </when>
                <when test='query.sort != null and query.sort.equals("rating_desc")'>
                    ORDER BY rating DESC, product.id ASC
                </when>
                <when test='query.sort != null and query.sort.equals("distance_asc") and query.lat != null and query.lng != null'>
                    ORDER BY distance ASC, product.id ASC
                </when>
                <when test='query.sort != null and query.sort.equals("distance_desc") and query.lat != null and query.lng != null'>
                    ORDER BY distance DESC, product.id ASC
                </when>
                    <otherwise>
                    ORDER BY product.sold_count DESC, product.id ASC
                </otherwise>
            </choose>
            </script>
            """)
    IPage<HotelSearchRow> selectHotelPage(Page<HotelSearchRow> page,
                                          @Param(Constants.WRAPPER) Wrapper<Product> wrapper,
                                          @Param("query") HotelSearchDTO query);

    @Select("""
            SELECT
                product.id AS id,
                product.name AS name,
                product.extra AS extraJson,
                COALESCE(MIN(rt.price), product.price, 0) AS priceMin,
                MIN(rt.cancel_policy) AS cancelPolicy,
                4.5 AS rating,
                NULL AS distance
            FROM product
            LEFT JOIN room_type rt ON rt.hotel_id = product.id
            WHERE product.id = #{hotelId}
              AND product.product_type = 'HOTEL'
              AND product.status = 1
            GROUP BY product.id, product.name, product.price, product.extra
            """)
    HotelSearchRow selectHotelById(@Param("hotelId") Long hotelId);

    @Select("""
            SELECT
                rt.id AS roomId,
                rt.hotel_id AS hotelId,
                rt.room_name AS roomName,
                CASE
                    WHEN rt.room_name LIKE '%双床%' THEN '双床'
                    WHEN rt.room_name LIKE '%大床%' THEN '大床'
                    WHEN rt.room_name LIKE '%套房%' THEN '大床/双床'
                    ELSE '床型待确认'
                END AS bedType,
                CASE
                    WHEN rt.room_name LIKE '%套房%' THEN '58㎡'
                    WHEN rt.room_name LIKE '%双床%' THEN '36㎡'
                    WHEN rt.room_name LIKE '%大床%' THEN '30㎡'
                    ELSE '面积待确认'
                END AS area,
                rt.breakfast AS breakfast,
                rt.cancel_policy AS cancelPolicy,
                rt.price AS pricePerNight,
                p.name AS hotelName,
                p.stock AS stock,
                p.status AS productStatus,
                p.product_type AS productType
            FROM room_type rt
            JOIN product p ON p.id = rt.hotel_id
            WHERE rt.id = #{roomId}
            """)
    RoomDetailRow selectRoomDetail(@Param("roomId") Long roomId);

    @Select("""
            SELECT
                rt.id AS roomId,
                rt.hotel_id AS hotelId,
                rt.room_name AS roomName,
                CASE
                    WHEN rt.room_name LIKE '%双床%' THEN '双床'
                    WHEN rt.room_name LIKE '%大床%' THEN '大床'
                    WHEN rt.room_name LIKE '%套房%' THEN '大床/双床'
                    ELSE '床型待确认'
                END AS bedType,
                CASE
                    WHEN rt.room_name LIKE '%套房%' THEN '58㎡'
                    WHEN rt.room_name LIKE '%双床%' THEN '36㎡'
                    WHEN rt.room_name LIKE '%大床%' THEN '30㎡'
                    ELSE '面积待确认'
                END AS area,
                rt.breakfast AS breakfast,
                rt.cancel_policy AS cancelPolicy,
                rt.price AS pricePerNight,
                p.name AS hotelName,
                p.stock AS stock,
                p.status AS productStatus,
                p.product_type AS productType
            FROM room_type rt
            JOIN product p ON p.id = rt.hotel_id
            WHERE rt.hotel_id = #{hotelId}
              AND p.product_type = 'HOTEL'
              AND p.status = 1
            ORDER BY rt.price ASC, rt.id ASC
            """)
    java.util.List<RoomDetailRow> selectRoomsByHotelId(@Param("hotelId") Long hotelId);

    @Select("""
            SELECT
                p.id AS hotelId,
                p.name AS hotelName,
                COALESCE(p.price, 300) AS pricePerNight,
                p.stock AS stock,
                p.status AS productStatus,
                p.product_type AS productType
            FROM product p
            WHERE p.id = #{hotelId}
              AND p.product_type = 'HOTEL'
              AND p.status = 1
            """)
    RoomDetailRow selectHotelRoomBase(@Param("hotelId") Long hotelId);

    @Update("""
            UPDATE product
            SET stock = stock - #{roomNum},
                sold_count = COALESCE(sold_count, 0) + #{roomNum},
                update_time = NOW()
            WHERE id = #{hotelId}
              AND product_type = 'HOTEL'
              AND status = 1
              AND stock >= #{roomNum}
            """)
    int decrementHotelStock(@Param("hotelId") Long hotelId, @Param("roomNum") Integer roomNum);

    @Data
    @NoArgsConstructor
    class HotelSearchRow {

        private String id;

        private String name;

        private String extraJson;

        private BigDecimal priceMin;

        private String cancelPolicy;

        private Double rating;

        private Double distance;
    }

    @Data
    @NoArgsConstructor
    class RoomDetailRow {

        private Long roomId;

        private Long hotelId;

        private String roomName;

        private String bedType;

        private String area;

        private Integer breakfast;

        private String cancelPolicy;

        private BigDecimal pricePerNight;

        private String hotelName;

        private Integer stock;

        private Integer productStatus;

        private String productType;
    }
}
