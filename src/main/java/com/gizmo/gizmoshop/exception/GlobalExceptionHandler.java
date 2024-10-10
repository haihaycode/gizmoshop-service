package com.gizmo.gizmoshop.exception;

import com.gizmo.gizmoshop.dto.reponseDto.ResponseWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ResponseWrapper<Void>> handleUsernameNotFound(UsernameNotFoundException ex) {
        ResponseWrapper<Void> response = new ResponseWrapper<>(HttpStatus.NOT_FOUND, ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ResponseWrapper<Void>> handleInvalidInput(InvalidInputException ex) {
        ResponseWrapper<Void> response = new ResponseWrapper<>(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<ResponseWrapper<Void>> handleInvalidTokenException(InvalidTokenException ex) {
        ResponseWrapper<Void> response = new ResponseWrapper<>(HttpStatus.UNAUTHORIZED, " Access denied : " + ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // Bắt lỗi BrandNotFoundException
    @ExceptionHandler(BrandNotFoundException.class)
    public ResponseEntity<ResponseWrapper<Void>> handleBrandNotFound(BrandNotFoundException ex) {
        ResponseWrapper<Void> response = new ResponseWrapper<>(HttpStatus.NOT_FOUND, ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // Bắt lỗi DuplicateBrandException
    @ExceptionHandler(DuplicateBrandException.class)
    public ResponseEntity<ResponseWrapper<Void>> handleDuplicateBrand(DuplicateBrandException ex) {
        ResponseWrapper<Void> response = new ResponseWrapper<>(HttpStatus.CONFLICT, ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

}
