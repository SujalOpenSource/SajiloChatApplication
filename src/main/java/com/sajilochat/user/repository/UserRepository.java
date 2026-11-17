
package com.sajilochat.user.repository;

import com.sajilochat.user.entity.User;
import com.sajilochat.user.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    @Query("""
            SELECT u
            FROM User u
            WHERE u.id <> :currentUserId
              AND u.status = :status
              AND (
                  LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
              )
              AND NOT EXISTS (
                  SELECT b.id
                  FROM UserBlock b
                  WHERE b.blocker.id = :currentUserId
                    AND b.blocked.id = u.id
              )
              AND NOT EXISTS (
                  SELECT b.id
                  FROM UserBlock b
                  WHERE b.blocker.id = u.id
                    AND b.blocked.id = :currentUserId
              )
            ORDER BY u.username ASC
            """)
    Page<User> searchUsers(
            @Param("query") String query,
            @Param("currentUserId") Long currentUserId,
            @Param("status") UserStatus status,
            Pageable pageable
    );
}
