package com.mcit.repo;


import com.mcit.entity.MyUser;
import com.mcit.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MyUserRepository extends JpaRepository<MyUser, Long> {

    Optional<MyUser> findByUsername(String username);

    Optional<MyUser> findById(Long id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

//    Optional<MyUser> findByEmailVerificationToken(String token);

    Optional<MyUser> findByEmail(String email);

    List<MyUser> findByRole(Role role);

    // Add these methods to your repository
    long countByIsActiveTrue();
    long countByIsActiveFalse();
    long count(); // No need to write @Query



}