package ch.exmachina.cosmo42.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.time.LocalDateTime;

@Embeddable
class ChatMessageId implements Serializable {

    @Column(nullable = false, name= "conversation_id", insertable=false, updatable=false)
    String conversationId;
    @Column(nullable = false, insertable=false, updatable=false)
    LocalDateTime timestamp;
}



@Entity
@Table(name = "\"SPRING_AI_CHAT_MEMORY\"")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class ChatMessage {
    @EmbeddedId
    ChatMessageId id;

    @Column(nullable = false)
    String content;

    @Column(nullable = false)
    String type;

    @Column(nullable = false, name= "conversation_id")
    String conversationId;
    @Column(nullable = false)
    LocalDateTime timestamp;

}
