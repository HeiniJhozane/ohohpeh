package hotel.exception;

/** Thrown when a room number is already used by another room. */
public class DuplicateRoomException extends Exception {
    public DuplicateRoomException(String message) {
        super(message);
    }
}
