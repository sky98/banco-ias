package co.com.bancoias.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.bancoias.model.preapproved.gateways.PreApprovedRepository;
import co.com.bancoias.model.preapprovedusage.PreApprovedUsage;
import co.com.bancoias.model.preapprovedusage.RejectionReason;
import co.com.bancoias.model.preapprovedusage.UsageProcessingResult;
import co.com.bancoias.model.preapprovedusage.UsageStatus;
import co.com.bancoias.model.preapprovedusage.gateways.PreApprovedUsageRepository;
import co.com.bancoias.usecase.processpreapprovedrequest.ProcessPreApprovedRequestCommand;
import co.com.bancoias.usecase.processpreapprovedrequest.ProcessPreApprovedRequestUseCase;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PreApprovedUsageIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName()));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    @Autowired
    private ProcessPreApprovedRequestUseCase processUseCase;

    @Autowired
    private PreApprovedUsageRepository usageRepository;

    @Autowired
    private PreApprovedRepository preApprovedRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void resetDatabase() {
        databaseClient.sql("DELETE FROM preapproved_usage").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM preapproved").fetch().rowsUpdated())
                .then(databaseClient.sql("""
                                INSERT INTO preapproved (pre_approved_id, customer_id, status, available_amount)
                                VALUES ('PRA-1', 'USR-10', 'ACTIVE', 1000.00)
                                """).fetch().rowsUpdated())
                .block();
    }

    @Test
    void approvesAndDecrementsBalance() {
        UsageProcessingResult result = process("REF-1", "PRA-1", "USR-10", "100");

        assertThat(result.usage().getStatus()).isEqualTo(UsageStatus.APPROVED);
        assertThat(result.usage().getReason()).isNull();
        assertThat(result.usage().getRemainingAmount()).isEqualByComparingTo("900.00");
        assertThat(balance("PRA-1")).isEqualByComparingTo("900.00");
    }

    @Test
    void replaysSameReferenceWithoutDoubleDecrement() {
        UsageProcessingResult first = process("REF-1", "PRA-1", "USR-10", "100");
        UsageProcessingResult replay = process("REF-1", "PRA-1", "USR-10", "100");

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(recentUsages()).hasSize(1);
        assertThat(balance("PRA-1")).isEqualByComparingTo("900.00");
    }

    @Test
    void conflictsOnSameReferenceWithDifferentPayload() {
        process("REF-1", "PRA-1", "USR-10", "100");
        UsageProcessingResult conflict = process("REF-1", "PRA-1", "USR-10", "150");

        assertThat(conflict.conflict()).isTrue();
        assertThat(recentUsages()).hasSize(1);
        assertThat(balance("PRA-1")).isEqualByComparingTo("900.00");
    }

    @Test
    void rejectsInsufficientQuotaWithoutDecrementing() {
        UsageProcessingResult result = process("REF-2", "PRA-1", "USR-10", "2000");

        assertThat(result.usage().getStatus()).isEqualTo(UsageStatus.REJECTED);
        assertThat(result.usage().getReason()).isEqualTo(RejectionReason.INSUFFICIENT_QUOTA);
        assertThat(result.usage().getRemainingAmount()).isEqualByComparingTo("1000.00");
        assertThat(balance("PRA-1")).isEqualByComparingTo("1000.00");
    }

    @Test
    void rejectsInvalidAmount() {
        UsageProcessingResult result = process("REF-3", "PRA-1", "USR-10", "0");

        assertThat(result.usage().getStatus()).isEqualTo(UsageStatus.REJECTED);
        assertThat(result.usage().getReason()).isEqualTo(RejectionReason.INVALID_AMOUNT);
        assertThat(balance("PRA-1")).isEqualByComparingTo("1000.00");
    }

    @Test
    void rejectsUnknownPreApprovedAndCustomerMismatch() {
        UsageProcessingResult missing = process("REF-4", "PRA-999", "USR-10", "10");
        UsageProcessingResult mismatch = process("REF-5", "PRA-1", "USR-99", "10");

        assertThat(missing.usage().getReason()).isEqualTo(RejectionReason.PREAPPROVED_NOT_FOUND);
        assertThat(mismatch.usage().getReason()).isEqualTo(RejectionReason.CUSTOMER_MISMATCH);
    }

    @Test
    void rejectsBlockedPreApproved() {
        databaseClient.sql("""
                        INSERT INTO preapproved (pre_approved_id, customer_id, status, available_amount)
                        VALUES ('PRA-2', 'USR-10', 'BLOCKED', 500.00)
                        """).fetch().rowsUpdated().block();

        UsageProcessingResult result = process("REF-6", "PRA-2", "USR-10", "10");

        assertThat(result.usage().getStatus()).isEqualTo(UsageStatus.REJECTED);
        assertThat(result.usage().getReason()).isEqualTo(RejectionReason.PREAPPROVED_BLOCKED);
        assertThat(balance("PRA-2")).isEqualByComparingTo("500.00");
    }

    @Test
    void concurrentSameReferenceIsAppliedOnce() {
        int attempts = 10;

        List<UsageProcessingResult> results = Flux.range(0, attempts)
                .flatMap(index -> processUseCase.execute(new ProcessPreApprovedRequestCommand(
                        "RACE-1", "PRA-1", "USR-10", new BigDecimal("100"))), attempts)
                .collectList()
                .block();

        assertThat(results).hasSize(attempts);
        long created = results.stream()
                .filter(result -> !result.replayed() && !result.conflict())
                .count();
        long replayed = results.stream()
                .filter(UsageProcessingResult::replayed)
                .count();

        assertThat(created).isEqualTo(1);
        assertThat(replayed).isEqualTo(attempts - 1);
        assertThat(balance("PRA-1")).isEqualByComparingTo("900.00");
        assertThat(recentUsages()).hasSize(1);
    }

    @Test
    void concurrentRequestsNeverOverdraw() {
        int attempts = 20;

        List<UsageProcessingResult> results = Flux.range(0, attempts)
                .flatMap(index -> processUseCase.execute(new ProcessPreApprovedRequestCommand(
                        "CONC-" + index, "PRA-1", "USR-10", new BigDecimal("100"))), attempts)
                .collectList()
                .block();

        assertThat(results).hasSize(attempts);
        long approved = results.stream()
                .filter(result -> result.usage().getStatus() == UsageStatus.APPROVED)
                .count();
        long rejected = results.stream()
                .filter(result -> result.usage().getStatus() == UsageStatus.REJECTED)
                .count();

        assertThat(approved).isEqualTo(10);
        assertThat(rejected).isEqualTo(10);
        assertThat(results.stream()
                .filter(result -> result.usage().getStatus() == UsageStatus.REJECTED)
                .allMatch(result -> result.usage().getReason() == RejectionReason.INSUFFICIENT_QUOTA))
                .isTrue();
        assertThat(balance("PRA-1")).isEqualByComparingTo("0.00");
        assertThat(recentUsages()).hasSize(attempts);
    }

    @Test
    void documentsOpenApiContract() {
        WebTestClient client = WebTestClient.bindToApplicationContext(applicationContext).build();

        client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$['paths']['/api/v1/preapproved-requests']['post']['requestBody']")
                .exists()
                .jsonPath("$['paths']['/api/v1/preapproved-requests']['post']['responses']['201']")
                .exists()
                .jsonPath("$['paths']['/api/v1/preapproved-requests']['post']['responses']['400']")
                .exists()
                .jsonPath("$['paths']['/api/v1/preapproved-requests']['post']['responses']['409']")
                .exists()
                .jsonPath("$['paths']['/api/v1/preapproved-requests']['get']['parameters'][0]['name']")
                .isEqualTo("limit");
    }

    private UsageProcessingResult process(String reference, String preApprovedId, String customerId, String amount) {
        return processUseCase.execute(new ProcessPreApprovedRequestCommand(
                        reference, preApprovedId, customerId, new BigDecimal(amount)))
                .block();
    }

    private BigDecimal balance(String preApprovedId) {
        return preApprovedRepository.findById(preApprovedId).block().getAvailableAmount();
    }

    private List<PreApprovedUsage> recentUsages() {
        return usageRepository.findRecent(100).collectList().block();
    }
}
