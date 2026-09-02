package top.ortus.lightmark.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductDTO {
    private String id;
    private String product_type;
    private String name;
    private BigDecimal price;
    private int stock;
    private int sold_count;
    private String category_tags;
    private int status;
    private String extra;
    private LocalDateTime create_time;
    private LocalDateTime update_time;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProduct_type() { return product_type; }
    public void setProduct_type(String value) { this.product_type = value; }
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal value) { this.price = value; }
    public int getStock() { return stock; }
    public void setStock(int value) { this.stock = value; }
    public int getSold_count() { return sold_count; }
    public void setSold_count(int value) { this.sold_count = value; }
    public String getCategory_tags() { return category_tags; }
    public void setCategory_tags(String value) { this.category_tags = value; }
    public int getStatus() { return status; }
    public void setStatus(int value) { this.status = value; }
    public String getExtra() { return extra; }
    public void setExtra(String value) { this.extra = value; }
    public LocalDateTime getCreate_time() { return create_time; }
    public void setCreate_time(LocalDateTime value) { this.create_time = value; }
    public LocalDateTime getUpdate_time() { return update_time; }
    public void setUpdate_time(LocalDateTime value) { this.update_time = value; }
}
