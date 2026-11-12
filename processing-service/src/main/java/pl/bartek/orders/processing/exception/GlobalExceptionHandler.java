package pl.bartek.orders.processing.exception;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ProductNotFoundException.class)
  public ProblemDetail handleProductNotFoundException(ProductNotFoundException ex){
    return ProblemDetail.forStatusAndDetail(NOT_FOUND,  ex.getMessage());
  }

  @ExceptionHandler(ProductInvalidRequestDataException.class)
  public ProblemDetail handleProductInvalidRequestDataException(ProductInvalidRequestDataException ex){
    return ProblemDetail.forStatusAndDetail(BAD_REQUEST,  ex.getMessage());
  }
}
