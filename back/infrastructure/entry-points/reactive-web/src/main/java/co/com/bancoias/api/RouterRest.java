package co.com.bancoias.api;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import co.com.bancoias.api.dto.ApiError;
import co.com.bancoias.api.dto.CreatePreApprovedUsageRequest;
import co.com.bancoias.api.dto.PreApprovedResponse;
import co.com.bancoias.api.dto.PreApprovedUsageResponse;
import co.com.bancoias.api.error.ApiExceptionHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
                    beanMethod = "create",
                    operation = @Operation(
                            operationId = "createPreApprovedUsage",
                            summary = "Procesa una solicitud de consumo de cupo preaprobado",
                            tags = {"Solicitudes"},
                            requestBody = @RequestBody(
                                    required = true,
                                    description = "Datos de la solicitud",
                                    content = @Content(schema = @Schema(
                                            implementation = CreatePreApprovedUsageRequest.class))),
                            responses = {
                                    @ApiResponse(
                                            responseCode = "201",
                                            description = "Solicitud procesada, aprobada o rechazada",
                                            content = @Content(schema = @Schema(
                                                    implementation = PreApprovedUsageResponse.class))),
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Replay idempotente de una solicitud ya procesada",
                                            content = @Content(schema = @Schema(
                                                    implementation = PreApprovedUsageResponse.class))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "Solicitud inválida",
                                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                                    @ApiResponse(
                                            responseCode = "409",
                                            description = "Referencia ya procesada con un payload distinto",
                                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                                    @ApiResponse(
                                            responseCode = "500",
                                            description = "Error inesperado",
                                            content = @Content(schema = @Schema(implementation = ApiError.class)))
                            })),
            @RouterOperation(
                    path = "/api/v1/preapproved-requests/{requestReference}",
                    method = RequestMethod.GET,
                    beanClass = PreApprovedUsageHandler.class,
                    beanMethod = "getByReference",
                    operation = @Operation(
                            operationId = "getPreApprovedUsage",
                            summary = "Consulta una solicitud por su referencia",
                            tags = {"Solicitudes"},
                            parameters = @Parameter(
                                    name = "requestReference",
                                    in = ParameterIn.PATH,
                                    required = true,
                                    description = "Referencia única de la solicitud",
                                    schema = @Schema(type = "string", maxLength = 80)),
                            responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Solicitud encontrada",
                                            content = @Content(schema = @Schema(
                                                    implementation = PreApprovedUsageResponse.class))),
                                    @ApiResponse(responseCode = "404", description = "Solicitud no encontrada")
                            })),
            @RouterOperation(
                    path = "/api/v1/preapproved-requests",
                    method = RequestMethod.GET,
                    beanClass = PreApprovedUsageHandler.class,
                    beanMethod = "listRecent",
                    operation = @Operation(
                            operationId = "listRecentPreApprovedUsages",
                            summary = "Lista las solicitudes recientes",
                            tags = {"Solicitudes"},
                            parameters = @Parameter(
                                    name = "limit",
                                    in = ParameterIn.QUERY,
                                    required = false,
                                    description = "Cantidad máxima de resultados (1-100)",
                                    schema = @Schema(
                                            type = "integer",
                                            minimum = "1",
                                            maximum = "100",
                                            defaultValue = "20")),
                            responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Listado de solicitudes",
                                            content = @Content(array = @ArraySchema(schema = @Schema(
                                                    implementation = PreApprovedUsageResponse.class)))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "Límite inválido",
                                            content = @Content(schema = @Schema(implementation = ApiError.class)))
                            })),
            @RouterOperation(
                    path = "/api/v1/preapproved/{preApprovedId}",
                    method = RequestMethod.GET,
                    beanClass = PreApprovedUsageHandler.class,
                    beanMethod = "getPreApproved",
                    operation = @Operation(
                            operationId = "getPreApproved",
                            summary = "Consulta el cupo disponible de un preaprobado",
                            tags = {"Preaprobados"},
                            parameters = @Parameter(
                                    name = "preApprovedId",
                                    in = ParameterIn.PATH,
                                    required = true,
                                    description = "Identificador del preaprobado",
                                    schema = @Schema(type = "string", maxLength = 40)),
                            responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Preaprobado encontrado",
                                            content = @Content(schema = @Schema(
                                                    implementation = PreApprovedResponse.class))),
                                    @ApiResponse(responseCode = "404", description = "Preaprobado no encontrado")
                            }))
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
