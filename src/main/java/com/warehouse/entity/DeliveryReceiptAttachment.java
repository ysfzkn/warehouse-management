package com.warehouse.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * A photograph or scan of the signed receipt, uploaded after the delivery.
 *
 * <p>Several per receipt are allowed: the page is often photographed rather than scanned,
 * and a long item list runs onto a second sheet.</p>
 */
@Entity
@Table(name = "delivery_receipt_attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = "receipt")
public class DeliveryReceiptAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private DeliveryReceipt receipt;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    /** Original name, kept for display only — never used to build a path. */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /**
     * Derived from the file's magic bytes by {@code UploadValidator}, never copied from
     * the uploader's header, because this value is echoed back as the response
     * {@code Content-Type} when the file is served.
     */
    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @PrePersist
    protected void onCreate() {
        if (this.uploadedAt == null) {
            this.uploadedAt = LocalDateTime.now();
        }
    }
}
