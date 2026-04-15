package io.github.jsbxyyx.jdbcdsl.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Test entity with NO {@code @Column} annotations — used to verify that the active
 * {@link io.github.jsbxyyx.jdbcdsl.NamingStrategy} is applied to unannotated fields.
 *
 * <p>The table name is explicit ({@code @Table(name="t_product")}) so that only
 * column-name derivation is tested in isolation.
 */
@Entity
@Table(name = "t_product")
public class TProductNoAnnotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productName;

    private BigDecimal unitPrice;

    private Integer stockQty;

    public TProductNoAnnotation() {}

    public Long getId() { return id; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public Integer getStockQty() { return stockQty; }
    public void setStockQty(Integer stockQty) { this.stockQty = stockQty; }
}
