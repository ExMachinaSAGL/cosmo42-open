package ch.exmachina.cosmo42.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "kb_document")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class KBDocument extends BaseEntity {

    @Column(nullable = false)
    @EqualsAndHashCode.Include
    @ToString.Include
    String uuid;

    @Column(nullable = false)
    String fileName;

    @Column(nullable = false)
    Long fileSize;

    @Column(nullable = false)
    LocalDateTime creationTimestamp;

}
