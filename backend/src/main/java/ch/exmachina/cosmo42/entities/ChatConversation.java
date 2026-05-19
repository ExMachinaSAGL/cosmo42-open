package ch.exmachina.cosmo42.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_conversation")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class ChatConversation extends BaseEntity {

    @Column(nullable = false, unique = true, length = 36)
    @EqualsAndHashCode.Include
    @ToString.Include
    String uuid;

    @Column
    String title;

    @Column(name = "created_at", nullable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    LocalDateTime updatedAt;
}