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
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "Điện thoại",
            "Máy tính xách tay",
            "Ví tiền & Giấy tờ cá nhân",
            "Chìa khóa",
            "Balo, Túi xách",
            "Trang phục (Clothing & Apparel)",
            "Phụ kiện và Trang sức",
            "Văn phòng phẩm",
            "Đồ dùng cá nhân",
            "Đồ dùng khác",
            "Đồ dùng trẻ em",
            "Máy tính & Thiết bị điện tử",
            "Kính mắt",
            "Giày dép",
            "Giấy tờ & Thẻ",
            "Bình nước"
    );

    private static final List<String> SQL_MIGRATIONS = List.of(
            "db/migrations/05_patrol_intake_receipt.sql",
            "db/migrations/07_p2p_claim_draft.sql",
            "db/migrations/08_allow_claim_retry_after_cancel.sql",
            "db/migrations/09_add_claim_categories.sql",
            "db/migrations/10_claim_questionnaires.sql",
            "db/migrations/11_p2p_chat.sql",
            "db/migrations/12_chat_read_timestamps.sql",
            "db/migrations/13_chat_locations.sql",
            "db/migrations/14_office_claims.sql",
            "db/migrations/15_reports.sql"
    );

    @Bean
    public CommandLineRunner initDefaultAccounts(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            DataSource dataSource
    ) {
        return args -> {
            // 0. Chuẩn hóa role cũ, tạo cột search_document cho bảng post, đảm bảo 16 danh mục và chạy migrations SQL
            migrateLegacyAccountRoles(dataSource);
            ensureCoreSchemaAndDefaults(dataSource);
            runSqlMigrations(dataSource);

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

            // 3. Seed tài khoản USER: user / 123
            Account testUser = accountRepository.findByEmailIgnoreCase("user").orElse(null);
            if (testUser == null) {
                testUser = new Account();
                testUser.setFullName("Người dùng Test (User)");
                testUser.setEmail("user");
                testUser.setPasswordHash(passwordEncoder.encode("123"));
                testUser.setRole(AccountRole.USER);
                testUser.setStatus(AccountStatus.ACTIVE);
                testUser.setMustChangePassword(false);
                testUser.setCreatedBy(admin);
                accountRepository.save(testUser);
            } else {
                testUser.setPasswordHash(passwordEncoder.encode("123"));
                testUser.setRole(AccountRole.USER);
                testUser.setStatus(AccountStatus.ACTIVE);
                testUser.setMustChangePassword(false);
                testUser.setFailedLoginCount(0);
                testUser.setLockedUntil(null);
                accountRepository.save(testUser);
            }

            // 4. Seed tài khoản USER: kayfrancistee@gmail.com / 123
            String thirdEmail = "kayfrancistee@gmail.com";
            Account thirdUser = accountRepository.findByEmailIgnoreCase(thirdEmail).orElse(null);
            if (thirdUser == null) {
                thirdUser = new Account();
                thirdUser.setFullName("Kay Francis Tee");
                thirdUser.setEmail(thirdEmail);
                thirdUser.setPasswordHash(passwordEncoder.encode("123"));
                thirdUser.setRole(AccountRole.USER);
                thirdUser.setStatus(AccountStatus.ACTIVE);
                thirdUser.setMustChangePassword(false);
                thirdUser.setCreatedBy(admin);
                accountRepository.save(thirdUser);
            }

            // 5. Tự động đồng bộ 4.203 bài đăng R2 Dataset (LOST + FOUND) cho mọi môi trường (Local & Render Staging)
            seedR2DatasetPostsIfMissing(dataSource);

            log.info("==================================================================");
            log.info("[SEED DATA - KhanhTB]");
            log.info("1. ADMIN   -> email: admin | password: 123");
            log.info("2. MANAGER -> email: {} | password: 123", managerEmail);
            log.info("3. USER    -> email: user  | password: 123");
            log.info("4. USER    -> email: {} | password: 123", thirdEmail);
            log.info("Swagger UI : http://localhost:8080/swagger-ui.html");
            log.info("==================================================================");
        };
    }

    private void migrateLegacyAccountRoles(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("""
                    SELECT con.conname
                    FROM pg_constraint con
                    JOIN pg_class rel ON rel.oid = con.conrelid
                    WHERE rel.relname = 'accounts' AND con.contype = 'c'
                      AND pg_get_constraintdef(con.oid) ILIKE '%role%'
                    """)) {
                Set<String> legacyConstraints = new HashSet<>();
                while (rs.next()) {
                    legacyConstraints.add(rs.getString(1));
                }
                for (String c : legacyConstraints) {
                    st.execute("ALTER TABLE accounts DROP CONSTRAINT IF EXISTS \"" + c + "\"");
                }
            }
            int updatedChecker = st.executeUpdate("UPDATE accounts SET role = 'APPROVER' WHERE upper(role) = 'CHECKER'");
            int updatedOther = st.executeUpdate("UPDATE accounts SET role = 'USER' WHERE role NOT IN ('ADMIN', 'USER', 'APPROVER', 'MANAGER')");
            st.execute("""
                    ALTER TABLE accounts
                    ADD CONSTRAINT chk_accounts_role
                    CHECK (role IN ('ADMIN', 'USER', 'APPROVER', 'MANAGER'))
                    """);
            if (updatedChecker > 0 || updatedOther > 0) {
                log.info("[SEED DATA] Đã chuyển đổi {} tài khoản CHECKER -> APPROVER và {} tài khoản role cũ -> USER.",
                        updatedChecker, updatedOther);
            }
        } catch (Exception e) {
            log.warn("[SEED DATA] Bỏ qua chuẩn hóa role cũ trên bảng accounts: {}", e.getMessage());
        }
    }

    private void execSafe(Statement st, String sql) {
        try {
            st.execute(sql);
        } catch (Exception e) {
            log.debug("[SCHEMA INIT] Bỏ qua câu lệnh DDL ({}): {}", sql, e.getMessage());
        }
    }

    private void ensureCoreSchemaAndDefaults(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            // 1. Khởi tạo các PL/pgSQL trigger functions trước mọi bảng và trigger
            execSafe(st, """
                    CREATE OR REPLACE FUNCTION set_updated_at()
                    RETURNS TRIGGER AS $$ BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END; $$ LANGUAGE plpgsql
                    """);
            execSafe(st, """
                    CREATE OR REPLACE FUNCTION set_post_category_updated_at()
                    RETURNS TRIGGER AS $$ BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END; $$ LANGUAGE plpgsql
                    """);

            // 2. Đảm bảo DEFAULT và trigger cho category, post, post_image, accounts
            execSafe(st, "ALTER TABLE category ALTER COLUMN is_active SET DEFAULT true");
            execSafe(st, "ALTER TABLE category ALTER COLUMN created_at SET DEFAULT now()");
            execSafe(st, "ALTER TABLE category ALTER COLUMN updated_at SET DEFAULT now()");
            execSafe(st, "CREATE UNIQUE INDEX IF NOT EXISTS uq_category_name ON category (name)");

            execSafe(st, "ALTER TABLE post ALTER COLUMN status SET DEFAULT 'OPEN'");
            execSafe(st, "ALTER TABLE post ALTER COLUMN custody_mode SET DEFAULT 'P2P'");
            execSafe(st, "ALTER TABLE post ALTER COLUMN moderation_status SET DEFAULT 'PENDING'");
            execSafe(st, "ALTER TABLE post ALTER COLUMN created_at SET DEFAULT now()");
            execSafe(st, "ALTER TABLE post ALTER COLUMN updated_at SET DEFAULT now()");

            execSafe(st, "DROP TRIGGER IF EXISTS trg_accounts_updated_at ON accounts");
            execSafe(st, "CREATE TRIGGER trg_accounts_updated_at BEFORE UPDATE ON accounts FOR EACH ROW EXECUTE FUNCTION set_updated_at()");
            execSafe(st, "DROP TRIGGER IF EXISTS trg_category_updated_at ON category");
            execSafe(st, "CREATE TRIGGER trg_category_updated_at BEFORE UPDATE ON category FOR EACH ROW EXECUTE FUNCTION set_post_category_updated_at()");
            execSafe(st, "DROP TRIGGER IF EXISTS trg_post_updated_at ON post");
            execSafe(st, "CREATE TRIGGER trg_post_updated_at BEFORE UPDATE ON post FOR EACH ROW EXECUTE FUNCTION set_post_category_updated_at()");

            // 3. Tạo cột tsvector search_document và GIN index cho chức năng tìm kiếm bài đăng
            execSafe(st, """
                    ALTER TABLE post
                    ADD COLUMN IF NOT EXISTS search_document tsvector
                    GENERATED ALWAYS AS (
                        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
                        setweight(to_tsvector('simple', coalesce(description, '')), 'B') ||
                        setweight(to_tsvector('simple', coalesce(location_text, '')), 'C')
                    ) STORED
                    """);
            execSafe(st, "CREATE INDEX IF NOT EXISTS idx_post_search_document ON post USING gin (search_document)");

            execSafe(st, "ALTER TABLE post_image ALTER COLUMN sort_order SET DEFAULT 0");
            execSafe(st, "ALTER TABLE post_image ALTER COLUMN created_at SET DEFAULT now()");

            // 4. Seed đủ 16 danh mục chuẩn trước khi chạy questionnaire và R2 dataset
            String insertCatSql = """
                    INSERT INTO category (name, is_active, created_at, updated_at)
                    SELECT ?, true, now(), now()
                    WHERE NOT EXISTS (SELECT 1 FROM category WHERE lower(btrim(name)) = lower(btrim(?)))
                    """;
            try (PreparedStatement ps = conn.prepareStatement(insertCatSql)) {
                for (String catName : DEFAULT_CATEGORIES) {
                    ps.setString(1, catName);
                    ps.setString(2, catName);
                    ps.executeUpdate();
                }
            }
            log.info("[SCHEMA INIT] Đã đảm bảo function set_updated_at(), cột post.search_document và 16 danh mục chuẩn.");
        } catch (Exception e) {
            log.warn("[SCHEMA INIT] Cảnh báo khi khởi tạo core schema/defaults: {}", e.getMessage());
        }
    }

    private void runSqlMigrations(DataSource dataSource) {
        for (String path : SQL_MIGRATIONS) {
            ClassPathResource res = new ClassPathResource(path);
            if (!res.exists()) continue;
            try (Connection conn = dataSource.getConnection()) {
                try (Statement st = conn.createStatement()) {
                    String sql = StreamUtils.copyToString(res.getInputStream(), StandardCharsets.UTF_8);
                    st.execute(sql);
                    log.info("[SQL MIGRATION] Đã thực thi thành công: {}", path);
                } catch (Exception e) {
                    // Nếu script có BEGIN; mà bị lỗi giữa chừng, bắt buộc ROLLBACK để không làm treo HikariCP connection
                    try (Statement rb = conn.createStatement()) {
                        rb.execute("ROLLBACK");
                    } catch (Exception ignored) {
                    }
                    log.warn("[SQL MIGRATION] Đã rollback script ({}): {}", path, e.getMessage());
                }
            } catch (Exception e) {
                log.warn("[SQL MIGRATION] Không thể mở kết nối cho ({}): {}", path, e.getMessage());
            }
        }
    }

    private void seedR2DatasetPostsIfMissing(DataSource dataSource) {
        ClassPathResource resource = new ClassPathResource("seed/r2_dataset_manifest.tsv.gz");
        if (!resource.exists()) {
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            Set<String> existingIdentifiers = new HashSet<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT private_identifiers FROM post WHERE private_identifiers LIKE 'DATASET_IMPORT:%'")) {
                while (rs.next()) {
                    existingIdentifiers.add(rs.getString(1));
                }
            } catch (Exception ex) {
                log.warn("[SEED R2 DATASET] Bảng post chưa sẵn sàng, bỏ qua seed R2 dataset: {}", ex.getMessage());
                return;
            }

            if (existingIdentifiers.size() >= 4203) {
                log.info("[SEED R2 DATASET] Đã có đủ {} bài đăng R2 trong Database.", existingIdentifiers.size());
                return;
            }

            Map<String, Long> accountByEmail = new HashMap<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, lower(email) FROM accounts")) {
                while (rs.next()) {
                    accountByEmail.put(rs.getString(2), rs.getLong(1));
                }
            }

            Map<String, Long> categoryByName = new HashMap<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT category_id, name FROM category")) {
                while (rs.next()) {
                    categoryByName.put(rs.getString(2).trim().toLowerCase(), rs.getLong(1));
                }
            }

            boolean origAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            String insertCategorySql = "INSERT INTO category (name, is_active, created_at, updated_at) VALUES (?, true, now(), now()) RETURNING category_id";
            String insertPostSql = """
                    INSERT INTO post (
                        account_id, created_by, category_id, post_type, title, description,
                        location_text, event_at, private_identifiers, status, custody_mode,
                        moderation_status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', 'P2P', 'VISIBLE', ?, ?)
                    RETURNING post_id
                    """;
            String insertImgSql = """
                    INSERT INTO post_image (
                        post_id, storage_key, image_url, sort_order, mime_type,
                        size_bytes, width, height, content_hash, created_at
                    ) VALUES (?, ?, NULL, 0, ?, ?, ?, ?, ?, ?)
                    """;

            int inserted = 0;
            int index = 0;
            OffsetDateTime baseTime = OffsetDateTime.now().minusMinutes(10);

            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(resource.getInputStream()), StandardCharsets.UTF_8));
                 PreparedStatement psCat = conn.prepareStatement(insertCategorySql);
                 PreparedStatement psPost = conn.prepareStatement(insertPostSql);
                 PreparedStatement psImg = conn.prepareStatement(insertImgSql)) {

                String line;
                while ((line = br.readLine()) != null) {
                    index++;
                    if (line.isBlank()) continue;
                    String[] parts = line.split("\t", -1);
                    if (parts.length < 13) continue;

                    String postType = parts[0];
                    String ownerEmail = parts[1].toLowerCase();
                    String categoryName = parts[2];
                    String title = parts[3];
                    String description = parts[4];
                    String locationText = parts[5];
                    String privateIdentifier = parts[6];
                    String storageKey = parts[7];
                    String mimeType = parts[8];
                    long sizeBytes = Long.parseLong(parts[9]);
                    int width = Integer.parseInt(parts[10]);
                    int height = Integer.parseInt(parts[11]);
                    String contentHash = parts[12];

                    if (existingIdentifiers.contains(privateIdentifier)) {
                        continue;
                    }

                    Long authorId = accountByEmail.getOrDefault(ownerEmail, accountByEmail.get("user"));
                    if (authorId == null) continue;

                    String catKey = categoryName.trim().toLowerCase();
                    Long categoryId = categoryByName.get(catKey);
                    if (categoryId == null) {
                        psCat.setString(1, categoryName.trim());
                        try (ResultSet rsCat = psCat.executeQuery()) {
                            if (rsCat.next()) {
                                categoryId = rsCat.getLong(1);
                                categoryByName.put(catKey, categoryId);
                            }
                        }
                    }
                    if (categoryId == null) continue;

                    OffsetDateTime eventAt = baseTime.minusMinutes(index % 4320);
                    Timestamp tsEvent = Timestamp.from(eventAt.toInstant());
                    Timestamp tsNow = Timestamp.from(OffsetDateTime.now().toInstant());

                    psPost.setLong(1, authorId);
                    psPost.setLong(2, authorId);
                    psPost.setLong(3, categoryId);
                    psPost.setString(4, postType);
                    psPost.setString(5, title);
                    psPost.setString(6, description);
                    psPost.setString(7, locationText);
                    psPost.setTimestamp(8, tsEvent);
                    psPost.setString(9, privateIdentifier);
                    psPost.setTimestamp(10, tsNow);
                    psPost.setTimestamp(11, tsNow);

                    long postId;
                    try (ResultSet rsPost = psPost.executeQuery()) {
                        rsPost.next();
                        postId = rsPost.getLong(1);
                    }

                    psImg.setLong(1, postId);
                    psImg.setString(2, storageKey);
                    psImg.setString(3, mimeType);
                    psImg.setLong(4, sizeBytes);
                    psImg.setInt(5, width);
                    psImg.setInt(6, height);
                    psImg.setString(7, contentHash);
                    psImg.setTimestamp(8, tsNow);
                    psImg.addBatch();

                    inserted++;
                    if (inserted % 400 == 0) {
                        psImg.executeBatch();
                        conn.commit();
                        log.info("[SEED R2 DATASET] Tiến độ đồng bộ: {}/4203 bài đăng...", inserted);
                    }
                }

                psImg.executeBatch();
                conn.commit();
                log.info("[SEED R2 DATASET] Đã tự động đồng bộ thành công {} bài đăng R2 vào Database!", inserted);
            } catch (Exception e) {
                conn.rollback();
                log.error("[SEED R2 DATASET] Lỗi khi đồng bộ bài đăng R2: {}", e.getMessage(), e);
            } finally {
                conn.setAutoCommit(origAutoCommit);
            }
        } catch (Exception e) {
            log.warn("[SEED R2 DATASET] Không thể kết nối Database để seed R2 dataset: {}", e.getMessage());
        }
    }
}
