package ch.exmachina.cosmo42.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "ingestion_job_page")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class IngestionJobPage extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_job_id")
    IngestionJob job;

    @Column(nullable = false)
    Integer pageIndex;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    IngestionJobPageStatus status;

    @Column(columnDefinition = "LONGTEXT")
    String chunksJson;
}
