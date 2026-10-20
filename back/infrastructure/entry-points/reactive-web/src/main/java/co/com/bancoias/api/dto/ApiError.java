package co.com.bancoias.api.dto;

public record ApiError(String code, String message, Object details) {
}
