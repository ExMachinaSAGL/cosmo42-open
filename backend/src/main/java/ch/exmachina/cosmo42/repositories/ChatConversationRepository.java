package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.entities.ChatConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    Optional<ChatConversation> findByUuid(String uuid);

    Page<ChatConversation> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
                UPDATE ChatConversation c
                SET c.title = :title, c.updatedAt = :updatedAt
                WHERE c.uuid = :uuid
            """)
    int updateTitleByUuid(@Param("uuid") String uuid,
                          @Param("title") String title,
                          @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
                UPDATE ChatConversation c
                SET c.updatedAt = :updatedAt
                WHERE c.uuid = :uuid
            """)
    int updateActivityByUuid(@Param("uuid") String uuid,
                             @Param("updatedAt") LocalDateTime updatedAt);

    int deleteByUuid(String uuid);
}
