package com.ituaku.image_service_api.model.v1;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@NoArgsConstructor  // Required by JPA/Hibernate
@AllArgsConstructor // Required for your "new Images(...)" call
@Table(name = "tb_imgsrv_tr_images")
@Data
public class Images {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ori_filename")
    private String oriFilename;
    
    private String filename;
    private Integer filesize;
    
    @Column(name = "upload_date")
    private LocalDateTime uploadDate;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}