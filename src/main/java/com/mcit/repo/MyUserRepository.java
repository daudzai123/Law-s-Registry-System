package com.mcit.repo;


import com.mcit.entity.MyUser;
import com.mcit.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MyUserRepository extends JpaRepository<MyUser, Long> {

    // Find user by email
    Optional<MyUser> findByEmail(String email);

    Optional<MyUser> findByUsername(String username);

    Page<MyUser> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    Optional<MyUser> findById(Long id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // Add these methods to your repository
    long countByIsActiveTrue();
    long countByIsActiveFalse();
    long count(); // No need to write @Query
}