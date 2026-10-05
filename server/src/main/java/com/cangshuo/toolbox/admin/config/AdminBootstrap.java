package com.cangshuo.toolbox.admin.config;

import com.cangshuo.toolbox.admin.repository.AdminAccountRepository;
import com.cangshuo.toolbox.admin.service.AdminInput;
import com.cangshuo.toolbox.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first administrator from environment variables; never writes credentials to Git or logs. */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final AdminAccountRepository admins;
    private final PasswordEncoder passwords;
    private final String bootstrapUsername;
    private final String bootstrapPassword;

    public AdminBootstrap(AdminAccountRepository admins, PasswordEncoder passwords,
                          @Value("${toolbox.admin.bootstrap-username:}") String bootstrapUsername,
                          @Value("${toolbox.admin.bootstrap-password:}") String bootstrapPassword) {
        this.admins = admins; this.passwords = passwords;
        this.bootstrapUsername = bootstrapUsername; this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean hasUsername = bootstrapUsername != null && !bootstrapUsername.isBlank();
        boolean hasPassword = bootstrapPassword != null && !bootstrapPassword.isEmpty();
        if (!hasUsername && !hasPassword) return;
        if (!hasUsername || !hasPassword) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_USERNAME and ADMIN_BOOTSTRAP_PASSWORD must be set together");
        }
        String username;
        try {
            username = AdminInput.username(bootstrapUsername);
            AdminInput.bootstrapPassword(bootstrapPassword);
        } catch (ApiException exception) {
            throw new IllegalStateException("Admin bootstrap credentials are invalid: use a 3-32 character username and a 12-72 character password containing letters and digits");
        }
        if (admins.existsAny()) {
            log.info("Admin bootstrap skipped: an administrator account already exists");
            return;
        }
        admins.create(username, passwords.encode(bootstrapPassword), "管理员", "SUPER_ADMIN");
        log.info("Admin bootstrap created the first administrator account");
    }
}
