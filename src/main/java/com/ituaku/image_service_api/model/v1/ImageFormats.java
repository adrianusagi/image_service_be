package com.ituaku.image_service_api.model.v1;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_imgsrv_rf_formats")
@Data
public class ImageFormats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String configs;
    @Column(name = "custom_order")
    private Integer customOrder;
}