package com.shopstream.common.exception;

import lombok.Getter;

/**
 * Exception métier de base.
 * 
 * POURQUOI créer nos propres exceptions:
 * - Séparer exceptions techniques (SQLException) des erreurs métier
 * - Code HTTP approprié pour chaque type erreur
 * - Messages client-friendly
 * - Traçabilité avec error codes
 * 
 * PATTERN: Exception Translation (traduire exceptions low-level en high-level)
 */
@Getter
public class BusinessException extends RuntimeException {
    private final String errorCode;
    private final int httpStatus;

    public BusinessException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public BusinessException(String message, String errorCode, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
