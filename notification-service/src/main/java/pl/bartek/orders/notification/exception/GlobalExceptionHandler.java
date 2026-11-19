package pl.bartek.orders.notification.exception;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NotificationNotFoundException.class)
  public ProblemDetail handleNotificationNotFoundException(NotificationNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(NOT_FOUND, ex.getMessage());
  }
}
