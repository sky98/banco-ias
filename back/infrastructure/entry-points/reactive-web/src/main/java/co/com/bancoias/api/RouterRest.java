package co.com.bancoias.api;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import co.com.bancoias.api.error.ApiExceptionHandler;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class RouterRest {

    @RouterOperations({
            @RouterOperation(
                    path = "/api/v1/preapproved-requests",
                    method = RequestMethod.POST,
                    beanClass = PreApprovedUsageHandler.class,
                    beanMethod = "create"),
            @RouterOperation(
                    path = "/api/v1/preapproved-requests/{requestReference}",
                    method = RequestMethod.GET,
                    beanClass = PreApprovedUsageHandler.class,
                    beanMethod = "getByReference"),
            @RouterOperation(
                    path = "/api/v1/preapproved-requests",
                    method = RequestMethod.GET,
                    beanClass = PreApprovedUsageHandler.class,
                    beanMethod = "listRecent"),
            @RouterOperation(
                    path = "/api/v1/preapproved/{preApprovedId}",
                    method = RequestMethod.GET,
                    beanClass = PreApprovedUsageHandler.class,
                    beanMethod = "getPreApproved")
    })
    @Bean
    public RouterFunction<ServerResponse> routerFunction(
            PreApprovedUsageHandler handler, ApiExceptionHandler errorHandler) {
        return route(POST("/api/v1/preapproved-requests"), handler::create)
                .andRoute(GET("/api/v1/preapproved-requests/{requestReference}"), handler::getByReference)
                .andRoute(GET("/api/v1/preapproved-requests"), handler::listRecent)
                .andRoute(GET("/api/v1/preapproved/{preApprovedId}"), handler::getPreApproved)
                .filter((request, next) -> next.handle(request).onErrorResume(errorHandler::handle));
    }
}
