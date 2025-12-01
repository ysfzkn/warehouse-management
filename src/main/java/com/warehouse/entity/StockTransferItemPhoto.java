package com.warehouse.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "stock_transfer_item_photos",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stock_transfer_item_photo_item",
                        columnNames = {"stock_transfer_item_id"}
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = {"item"})
public class StockTransferItemPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_transfer_item_id", nullable = false, unique = true)
    private StockTransferItem item;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "relative_path", nullable = false, length = 500)
    private String relativePath;

    @Column(name = "thumbnail_path", nullable = false, length = 500)
    private String thumbnailPath;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}


