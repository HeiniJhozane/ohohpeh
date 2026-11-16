package hotel.exception;

/** Thrown when a staff login fails (blank fields, unknown user, or wrong password). */
public class InvalidCredentialsException extends Exception {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}