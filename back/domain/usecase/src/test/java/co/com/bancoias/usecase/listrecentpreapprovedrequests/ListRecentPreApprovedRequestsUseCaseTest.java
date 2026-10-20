package co.com.bancoias.usecase.listrecentpreapprovedrequests;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.bancoias.model.preapprovedusage.gateways.PreApprovedUsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ListRecentPreApprovedRequestsUseCaseTest {

    @Mock
    private PreApprovedUsageRepository repository;

    @InjectMocks
    private ListRecentPreApprovedRequestsUseCase useCase;

    @Test
    void usesDefaultLimitWhenLimitIsNotPositive() {
        when(repository.findRecent(20)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(0)).verifyComplete();
        StepVerifier.create(useCase.execute(-10)).verifyComplete();

        verify(repository, org.mockito.Mockito.times(2)).findRecent(20);
    }

    @Test
    void capsLimitAtOneHundred() {
        when(repository.findRecent(100)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(500)).verifyComplete();

        verify(repository).findRecent(100);
    }

    @Test
    void keepsValidLimit() {
        when(repository.findRecent(30)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(30)).verifyComplete();

        verify(repository).findRecent(30);
    }
}
