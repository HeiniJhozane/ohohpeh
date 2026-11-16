package hotel.service;

import hotel.exception.InvalidBookingDataException;
import hotel.exception.InvalidGuestDataException;
import hotel.exception.RoomConflictException;
import hotel.exception.RoomNotFoundException;
import hotel.model.Booking;
import hotel.model.Booking.BookingType;
import hotel.model.Guest;
import hotel.model.Room;
import hotel.model.Room.RoomStatus;
import hotel.storage.FileManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Booking business logic: validate a reservation, reserve the room, create the matching guest,
 * record the payment and write the receipt. Contains no GUI code.
 *
 * Recording a booking also adds the guest to the Guest Directory (GuestService does the
 * double-booking check) and updates the room's status (Room Inventory).
 */
public class BookingService {
    /** Shown under the form: "Check-in from 2:00 PM · Check-out by 11:00 AM". */
    public static final String CHECK_IN_TIME = "2:00 PM";
    public static final String CHECK_OUT_TIME = "11:00 AM";

    /** The booking form has no address box, but Guest requires one, so this fills the gap. */
    public static final String DEFAULT_ADDRESS = "Not provided";

    private final List<Booking> bookings;
    private final FileManager files;
    private final GuestService guestService;
    private final RoomService roomService;

    public BookingService(List<Booking> bookings, FileManager files,
                          GuestService guestService, RoomService roomService) {
        this.bookings = new ArrayList<>(bookings);
        this.files = files;
        this.guestService = guestService;
        this.roomService = roomService;
    }

    // ---------------- create ----------------

    /**
     * The "+ Record payment" button. Validates everything first, then (in order) adds the guest,
     * saves the paid booking and updates the room status. Full payment is recorded at the
     * room rate x nights, which is what the receipt shows.
     */
    public Booking recordBooking(BookingType type, String name, String contact, int roomNumber,
                                 int adults, LocalDate checkIn, LocalDate checkOut, String receivedBy)
            throws InvalidBookingDataException, RoomConflictException, RoomNotFoundException {

        if (type == null)
            throw new InvalidBookingDataException("Choose Advance booking or Walk-in booking.");
        if (checkIn == null || checkOut == null)
            throw new InvalidBookingDataException("Check-in and check-out dates are required.");

        LocalDate today = LocalDate.now();
        if (type == BookingType.WALK_IN && !checkIn.equals(today))
            throw new InvalidBookingDataException(
                    "Walk-in guests check in today (" + today.format(Guest.DISPLAY_DATE) + ").");
        if (type == BookingType.ADVANCE && !checkIn.isAfter(today))
            throw new InvalidBookingDataException(
                    "Advance bookings must start after today. Use Walk-in booking for today.");

        Room room = roomService.findByNumber(roomNumber);
        if (adults > room.getCapacity())
            throw new InvalidBookingDataException("Room " + room.getRoomNumber() + " fits at most "
                    + room.getCapacity() + " guests.");

        // Build the booking first (guestId 0 for now) so a bad field is reported before anything is saved.
        Booking booking = build(nextId(), 0, type, room, adults, name, contact, checkIn, checkOut);

        Guest guest;
        try {
            guest = guestService.addGuest(roomNumber, name, contact, "", DEFAULT_ADDRESS, checkIn, checkOut);
        } catch (InvalidGuestDataException e) {
            throw new InvalidBookingDataException(e.getMessage());
        }

        try {
            booking.setGuestId(guest.getId());
            booking.recordPayment(booking.getTotal(), today, receivedBy);
        } catch (IllegalArgumentException e) {
            throw new InvalidBookingDataException(e.getMessage());
        }

        bookings.add(booking);
        files.saveBookings(bookings);

        roomService.setStatus(roomNumber, type == BookingType.WALK_IN ? RoomStatus.OCCUPIED : RoomStatus.RESERVED);
        return booking;
    }

    // ---------------- view ----------------

    public int getTotalCount() {
        return bookings.size();
    }

    /** Newest booking first. */
    public List<Booking> getAll() {
        List<Booking> copy = new ArrayList<>(bookings);
        copy.sort((a, b) -> Integer.compare(b.getId(), a.getId()));
        return copy;
    }

    /** Writes data/receipt_000N.txt for the booking and returns its path (null on failure). */
    public String exportReceipt(Booking booking) {
        return files.saveReceipt(booking);
    }

    // ---------------- internals ----------------

    /** Wraps the model's IllegalArgumentException so the GUI only has to catch one checked type. */
    private Booking build(int id, int guestId, BookingType type, Room room, int adults, String name,
                          String contact, LocalDate in, LocalDate out) throws InvalidBookingDataException {
        try {
            return new Booking(id, guestId, type, room.getRoomNumber(), room.getType(), room.getRate(),
                    adults, name, contact, in, out, 0, null, "");
        } catch (IllegalArgumentException e) {
            throw new InvalidBookingDataException(e.getMessage());
        }
    }

    private int nextId() {
        int max = 0;
        for (Booking b : bookings) max = Math.max(max, b.getId());
        return max + 1;
    }
}
