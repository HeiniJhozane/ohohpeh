package hotel.exception;

/** Thrown when updating or deleting a room that no longer exists. */
public class RoomNotFoundException extends Exception {
    public RoomNotFoundException(int roomNumber) {
        super("Room " + roomNumber + " was not found. It may have already been removed.");
    }
}
