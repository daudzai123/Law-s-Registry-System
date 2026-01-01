package com.mcit.config;

import com.mcit.entity.User;
import com.mcit.enums.Role;
import com.mcit.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        System.out.println("✅ UserDataInitializer constructor called.");
    }


    @Override
    public void run(String... args) {
        System.out.println("🔁 Running UserDataInitializer...");
        createDefaultUser("lawManager", "LawManager@1234@", Role.ROLE_LAW_MANAGER, "Law", "Manager", "lawmgr@example.com");
        createDefaultUser("admin", "Admin@1234@", Role.ROLE_ADMIN, "Default", "Admin", "admin@example.com");
    }

    private void createDefaultUser(String username, String password, Role role, String firstName, String lastName, String email) {
        try {
            if (!userRepository.existsByUsername(username)) {
                User user = new User();
                user.setUsername(username);
                user.setPassword(passwordEncoder.encode(password));
                user.setRole(role);
                user.setFirstname(firstName);
                user.setLastname(lastName);
                user.setEmail(email);
                user.setIsActive(true);
                userRepository.save(user);
                System.out.println("✅ Created user: " + username);
            } else {
                System.out.println("ℹ️ User already exists: " + username);
            }
        } catch (Exception e) {
            System.err.println("❌ Error creating user " + username);
            e.printStackTrace();
        }
    }
}
