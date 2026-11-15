package com.bookstore.orders;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {

    @EntityGraph(attributePaths = {"items", "items.book"})
    List<Order> findByOwnerSessionIdOrderByIdDesc(String ownerSessionId);

    @EntityGraph(attributePaths = {"items", "items.book"})
    Optional<Order> findByIdAndOwnerSessionId(String id, String ownerSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select o from CustomerOrder o
        where o.id = :id and o.ownerSessionId = :ownerSessionId
        """)
    Optional<Order> findByIdAndOwnerSessionIdForUpdate(
        @Param("id") String id,
        @Param("ownerSessionId") String ownerSessionId);

    /** Keeps guest orders visible after login changes the session ID (see AuthController). */
    @Modifying
    @Transactional
    @Query("""
        update CustomerOrder o set o.ownerSessionId = :newSessionId
        where o.ownerSessionId = :oldSessionId
        """)
    int reassignOwnerSession(
        @Param("oldSessionId") String oldSessionId,
        @Param("newSessionId") String newSessionId);
}
