package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.entities.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationId(String conversationId);
    void deleteByConversationId(String conversationId);

}
