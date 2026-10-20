package co.com.bancoias.config;

import co.com.bancoias.model.preapproved.gateways.PreApprovedRepository;
import co.com.bancoias.model.preapprovedusage.gateways.PreApprovedRequestEventPublisher;
import co.com.bancoias.model.preapprovedusage.gateways.PreApprovedUsageRepository;
import co.com.bancoias.usecase.getpreapproved.GetPreApprovedUseCase;
import co.com.bancoias.usecase.getpreapprovedrequest.GetPreApprovedRequestUseCase;
import co.com.bancoias.usecase.listrecentpreapprovedrequests.ListRecentPreApprovedRequestsUseCase;
import co.com.bancoias.usecase.processpreapprovedrequest.ProcessPreApprovedRequestUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseBeansConfig {

    @Bean
    public ProcessPreApprovedRequestUseCase processPreApprovedRequestUseCase(
            PreApprovedUsageRepository repository,
            PreApprovedRequestEventPublisher eventPublisher) {
        return new ProcessPreApprovedRequestUseCase(repository, eventPublisher);
    }

    @Bean
    public ListRecentPreApprovedRequestsUseCase listRecentPreApprovedRequestsUseCase(
            PreApprovedUsageRepository repository) {
        return new ListRecentPreApprovedRequestsUseCase(repository);
    }

    @Bean
    public GetPreApprovedUseCase getPreApprovedUseCase(PreApprovedRepository repository) {
        return new GetPreApprovedUseCase(repository);
    }

    @Bean
    public GetPreApprovedRequestUseCase getPreApprovedRequestUseCase(
            PreApprovedUsageRepository repository) {
        return new GetPreApprovedRequestUseCase(repository);
    }
}
