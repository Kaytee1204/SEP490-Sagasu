package com.example.base.config;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner initDefaultAccounts(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            // 1. Seed tài khoản ADMIN: admin / 123
            Account admin = accountRepository.findByEmailIgnoreCase("admin").orElse(null);
            if (admin == null) {
                admin = new Account();
                admin.setFullName("Quản trị viên");
                admin.setEmail("admin");
                admin.setPasswordHash(passwordEncoder.encode("123"));
                admin.setRole(AccountRole.ADMIN);
                admin.setStatus(AccountStatus.ACTIVE);
                admin.setMustChangePassword(false);
                admin = accountRepository.save(admin);
            } else {
                if (!StringUtils.hasText(admin.getFullName())) {
                    admin.setFullName("Quản trị viên");
                }
                admin.setPasswordHash(passwordEncoder.encode("123"));
                admin.setRole(AccountRole.ADMIN);
                admin.setStatus(AccountStatus.ACTIVE);
                admin.setMustChangePassword(false);
                admin.setFailedLoginCount(0);
                admin.setLockedUntil(null);
                admin = accountRepository.save(admin);
            }

            // 2. Seed tài khoản MANAGER: khanhtrinh1204@gmail.com / 123
            String managerEmail = "khanhtrinh1204@gmail.com";
            Account manager = accountRepository.findByEmailIgnoreCase(managerEmail).orElse(null);
            if (manager == null) {
                manager = new Account();
                manager.setFullName("Khánh Trịnh");
                manager.setEmail(managerEmail);
                manager.setPasswordHash(passwordEncoder.encode("123"));
                manager.setRole(AccountRole.MANAGER);
                manager.setStatus(AccountStatus.ACTIVE);
                manager.setMustChangePassword(true);
                manager.setCreatedBy(admin);
                accountRepository.save(manager);
            } else {
                if (!StringUtils.hasText(manager.getFullName())
                        || manager.getFullName().contains("(Manager)")
                        || manager.getFullName().contains("(MANAGER)")) {
                    manager.setFullName("Khánh Trịnh");
                }
                if (manager.isMustChangePassword()) {
                    manager.setPasswordHash(passwordEncoder.encode("123"));
                }
                manager.setRole(AccountRole.MANAGER);
                manager.setStatus(AccountStatus.ACTIVE);
                manager.setFailedLoginCount(0);
                manager.setLockedUntil(null);
                accountRepository.save(manager);
            }

            log.info("==================================================================");
            log.info("[SEED DATA - KhanhTB]");
            log.info("1. ADMIN   -> email: admin | password: 123");
            log.info("2. MANAGER -> email: {} | password: 123", managerEmail);
            log.info("Swagger UI : http://localhost:8080/swagger-ui.html");
            log.info("==================================================================");
        };
    }
}
