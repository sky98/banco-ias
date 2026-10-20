package co.com.bancoias.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import co.com.bancoias.api.error.ApiExceptionHandler;
import co.com.bancoias.api.validation.ValidadorRequest;
import co.com.bancoias.model.preapproved.PreApproved;
import co.com.bancoias.model.preapproved.PreApprovedStatus;
import co.com.bancoias.model.preapprovedusage.PreApprovedUsage;
import co.com.bancoias.model.preapprovedusage.RejectionReason;
import co.com.bancoias.model.preapprovedusage.UsageProcessingResult;
import co.com.bancoias.model.preapprovedusage.UsageStatus;
import co.com.bancoias.usecase.getpreapproved.GetPreApprovedUseCase;
import co.com.bancoias.usecase.getpreapprovedrequest.GetPreApprovedRequestUseCase;
import co.com.bancoias.usecase.listrecentpreapprovedrequests.ListRecentPreApprovedRequestsUseCase;
import co.com.bancoias.usecase.processpreapprovedrequest.ProcessPreApprovedRequestCommand;
import co.com.bancoias.usecase.processpreapprovedrequest.ProcessPreApprovedRequestUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class PreApprovedUsageHandlerTest {

    private static final Validator VALIDATOR = localValidator();

    @Mock
    private ProcessPreApprovedRequestUseCase processUseCase;

    @Mock
    private GetPreApprovedRequestUseCase getUsageUseCase;

    @Mock
    private ListRecentPreApprovedRequestsUseCase listUseCase;

    @Mock
    private GetPreApprovedUseCase getPreApprovedUseCase;

    private static Validator localValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        PreApprovedUsageHandler handler = new PreApprovedUsageHandler(
                processUseCase,
                getUsageUseCase,
                listUseCase,
                getPreApprovedUseCase,
                new ValidadorRequest(VALIDATOR));
        client = WebTestClient.bindToRouterFunction(
                        new RouterRest().routerFunction(handler, new ApiExceptionHandler()))
                .build();
    }

    @Test
    void createsApprovedUsage() {
        when(processUseCase.execute(any(ProcessPreApprovedRequestCommand.class)))
                .thenReturn(Mono.just(UsageProcessingResult.created(usage(UsageStatus.APPROVED, null))));

        client.post()
                .uri("/api/v1/preapproved-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "requestReference": "REF-1",
                          "preApprovedId": "PRA-1",
                          "customerId": "USR-10",
                          "amount": 100.00
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.requestReference").isEqualTo("REF-1")
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.reason").doesNotExist()
                .jsonPath("$.replayed").isEqualTo(false);
    }

    @Test
    void returnsOkWhenReferenceIsReplayed() {
        when(processUseCase.execute(any(ProcessPreApprovedRequestCommand.class)))
                .thenReturn(Mono.just(UsageProcessingResult.replayed(usage(UsageStatus.APPROVED, null))));

        client.post()
                .uri("/api/v1/preapproved-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "requestReference": "REF-1",
                          "preApprovedId": "PRA-1",
                          "customerId": "USR-10",
                          "amount": 100.00
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.replayed").isEqualTo(true);
    }

    @Test
    void returnsConflictWhenReferenceHasDifferentPayload() {
        when(processUseCase.execute(any(ProcessPreApprovedRequestCommand.class)))
                .thenReturn(Mono.just(UsageProcessingResult.conflict(usage(UsageStatus.APPROVED, null))));

        client.post()
                .uri("/api/v1/preapproved-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "requestReference": "REF-1",
                          "preApprovedId": "PRA-1",
                          "customerId": "USR-10",
                          "amount": 100.00
                        }
                        """)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("REFERENCE_CONFLICT")
                .jsonPath("$.details.requestReference").isEqualTo("REF-1");
    }

    @Test
    void returnsBadRequestOnValidationError() {
        client.post()
                .uri("/api/v1/preapproved-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "requestReference": "",
                          "preApprovedId": "PRA-1",
                          "customerId": "USR-10",
                          "amount": 100.00
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void returnsBadRequestOnMalformedJson() {
        client.post()
                .uri("/api/v1/preapproved-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{ not-json")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsTooLongRequestReference() {
        String reference = "R".repeat(81);

        client.post()
                .uri("/api/v1/preapproved-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "requestReference": "%s",
                          "preApprovedId": "PRA-1",
                          "customerId": "USR-10",
                          "amount": 100.00
                        }
                        """.formatted(reference))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.message")
                .value(containsString("requestReference: máximo 80 caracteres"));
    }

    @Test
    void rejectsTooLongPreApprovedId() {
        String preApprovedId = "P".repeat(41);

        client.post()
                .uri("/api/v1/preapproved-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "requestReference": "REF-1",
                          "preApprovedId": "%s",
                          "customerId": "USR-10",
                          "amount": 100.00
                        }
                        """.formatted(preApprovedId))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.message")
                .value(containsString("preApprovedId: máximo 40 caracteres"));
    }

    @Test
    void rejectsTooLongCustomerId() {
        String customerId = "C".repeat(41);

        client.post()
                .uri("/api/v1/preapproved-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "requestReference": "REF-1",
                          "preApprovedId": "PRA-1",
                          "customerId": "%s",
                          "amount": 100.00
                        }
                        """.formatted(customerId))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.message")
                .value(containsString("customerId: máximo 40 caracteres"));
    }

    @Test
    void rejectsAmountWithMoreThanTwoDecimals() {
        client.post()
                .uri("/api/v1/preapproved-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "requestReference": "REF-1",
                          "preApprovedId": "PRA-1",
                          "customerId": "USR-10",
                          "amount": 100.005
                        }
                        """)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.message")
                .value(containsString("amount: máximo 2 decimales"));
    }

    @Test
    void rejectsAmountOverflowingDatabasePrecision() {
        client.post()
                .uri("/api/v1/preapproved-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "requestReference": "REF-1",
                          "preApprovedId": "PRA-1",
                          "customerId": "USR-10",
                          "amount": 100000000000000000
                        }
                        """)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.message")
                .value(containsString("amount: supera el máximo permitido"));
    }

    @Test
    void acceptsAmountWithTrailingZeros() {
        when(processUseCase.execute(any(ProcessPreApprovedRequestCommand.class)))
                .thenReturn(Mono.just(UsageProcessingResult.created(usage(UsageStatus.APPROVED, null))));

        client.post()
                .uri("/api/v1/preapproved-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "requestReference": "REF-1",
                          "preApprovedId": "PRA-1",
                          "customerId": "USR-10",
                          "amount": 100.000
                        }
                        """)
                .exchange()
                .expectStatus()
                .isCreated();
    }

    @Test
    void getsUsageByReference() {
        when(getUsageUseCase.execute("REF-1"))
                .thenReturn(Mono.just(usage(UsageStatus.REJECTED, RejectionReason.INSUFFICIENT_QUOTA)));

        client.get()
                .uri("/api/v1/preapproved-requests/REF-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("REJECTED")
                .jsonPath("$.reason").isEqualTo("INSUFFICIENT_QUOTA");
    }

    @Test
    void returnsNotFoundForUnknownReference() {
        when(getUsageUseCase.execute("UNKNOWN")).thenReturn(Mono.empty());

        client.get()
                .uri("/api/v1/preapproved-requests/UNKNOWN")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void listsRecentUsages() {
        when(listUseCase.execute(20)).thenReturn(Flux.just(usage(UsageStatus.APPROVED, null)));

        client.get()
                .uri("/api/v1/preapproved-requests")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].requestReference").isEqualTo("REF-1");
    }

    @Test
    void rejectsInvalidLimit() {
        client.get()
                .uri("/api/v1/preapproved-requests?limit=abc")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getsPreApprovedBalance() {
        when(getPreApprovedUseCase.execute("PRA-1")).thenReturn(Mono.just(PreApproved.builder()
                .preApprovedId("PRA-1")
                .customerId("USR-10")
                .status(PreApprovedStatus.ACTIVE)
                .availableAmount(new BigDecimal("900.00"))
                .build()));

        client.get()
                .uri("/api/v1/preapproved/PRA-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.preApprovedId").isEqualTo("PRA-1")
                .jsonPath("$.availableAmount").isEqualTo(900.00);
    }

    private PreApprovedUsage usage(UsageStatus status, RejectionReason reason) {
        return PreApprovedUsage.builder()
                .id(1L)
                .requestReference("REF-1")
                .preApprovedId("PRA-1")
                .customerId("USR-10")
                .amount(new BigDecimal("100.00"))
                .status(status)
                .reason(reason)
                .payloadHash("hash")
                .processedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .remainingAmount(new BigDecimal("900.00"))
                .build();
    }
}
