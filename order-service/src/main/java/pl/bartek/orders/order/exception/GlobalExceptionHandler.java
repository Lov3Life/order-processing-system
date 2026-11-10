package pl.bartek.orders.order.exception;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.springframework.http.HttpStatus.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(CustomerNotFoundException.class)
  public ProblemDetail handleCustomerNotFoundException(CustomerNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(CustomerNotUniqueEmailException.class)
  public ProblemDetail handleCustomerNotUniqueEmailException(CustomerNotUniqueEmailException ex) {
    return ProblemDetail.forStatusAndDetail(CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(OrderNotFoundException.class)
  public ProblemDetail handleOrderNotFoundException(OrderNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(OrderInvalidRequestDataException.class)
  public ProblemDetail handleOrderInvalidDataException(OrderInvalidRequestDataException ex) {
    return ProblemDetail.forStatusAndDetail(BAD_REQUEST, ex.getMessage());
  }
}
