package hotel.exception;

/** Thrown when a room is already occupied by another guest during the requested dates. */
public class RoomConflictException extends Exception {
    public RoomConflictException(String message) {
        super(message);
    }
}
