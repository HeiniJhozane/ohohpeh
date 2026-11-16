package hotel.exception;

/** Thrown when booking details fail validation (bad dates, too many guests, wrong booking type, ...). */
public class InvalidBookingDataException extends Exception {
    public InvalidBookingDataException(String message) {
        super(message);
    }
}
