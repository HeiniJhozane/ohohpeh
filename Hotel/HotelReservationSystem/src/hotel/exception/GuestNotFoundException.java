package hotel.exception;

/** Thrown when updating or deleting a guest that no longer exists. */
public class GuestNotFoundException extends Exception {
    public GuestNotFoundException(int guestId) {
        super("Guest #" + guestId + " was not found. They may have already been removed.");
    }
}
