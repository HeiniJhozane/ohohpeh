package hotel.exception;

/** Thrown when room details fail validation (bad number, floor, rate, capacity, ...). */
public class InvalidRoomDataException extends Exception {
    public InvalidRoomDataException(String message) {
        super(message);
    }
}
