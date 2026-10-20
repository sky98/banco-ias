package co.com.bancoias.usecase.getpreapproved;

import co.com.bancoias.model.preapproved.PreApproved;
import co.com.bancoias.model.preapproved.gateways.PreApprovedRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetPreApprovedUseCase {

    private final PreApprovedRepository repository;

    public Mono<PreApproved> execute(String preApprovedId) {
        return repository.findById(preApprovedId);
    }
}
