package hotel.exception;

/** Thrown when guest details fail validation (blank name, bad phone, check-out before check-in, ...). */
public class InvalidGuestDataException extends Exception {
    public InvalidGuestDataException(String message) {
        super(message);
    }
}
