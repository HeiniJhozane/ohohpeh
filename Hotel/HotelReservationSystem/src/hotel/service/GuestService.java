package hotel.service;

import hotel.exception.GuestNotFoundException;
import hotel.exception.InvalidGuestDataException;
import hotel.exception.RoomConflictException;
import hotel.exception.RoomNotFoundException;
import hotel.model.Booking;
import hotel.model.Guest;
import hotel.model.Room;
import hotel.model.Room.RoomStatus;
import hotel.storage.FileManager;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Guest business logic: add / update / delete / search / filter / paginate / report.
 * Contains no GUI code. Every change is saved straight away through FileManager.
 *
 * Guests and rooms stay in step: deleting a guest or reaching a guest's check-out date
 * frees their room (see syncRoomStatus / releaseCheckedOutRooms).
 */
public class GuestService {
    /** Rows per page in the Guest Directory table ("Showing 1-8 of 50 guests"). */
    public static final int PAGE_SIZE = 8;

    private static final Comparator<Guest> BY_ROOM_THEN_DATE =
            Comparator.comparingInt(Guest::getRoomNumber).thenComparing(Guest::getCheckIn);

    private final List<Guest> guests;
    private final FileManager files;
    private final RoomService roomService;

    public GuestService(List<Guest> guests, FileManager files, RoomService roomService) {
        this.guests = new ArrayList<>(guests);
        this.files = files;
        this.roomService = roomService;
    }

    // ---------------- create / update / delete ----------------

    public Guest addGuest(int room, String name, String contact, String email, String address,
                          LocalDate checkIn, LocalDate checkOut)
            throws InvalidGuestDataException, RoomConflictException {
        Guest g = build(nextId(), room, name, contact, email, address, checkIn, checkOut);
        ensureRoomFree(g);
        guests.add(g);
        files.saveGuests(guests);
        return g;
    }

    public Guest updateGuest(int id, int room, String name, String contact, String email, String address,
                             LocalDate checkIn, LocalDate checkOut)
            throws InvalidGuestDataException, RoomConflictException, GuestNotFoundException {
        int index = indexOf(id);
        // Build and check a replacement first, so a bad edit never leaves a half-changed guest.
        Guest updated = build(id, room, name, contact, email, address, checkIn, checkOut);
        ensureRoomFree(updated);
        int oldRoom = guests.get(index).getRoomNumber();
        guests.set(index, updated);
        files.saveGuests(guests);
        LocalDate today = LocalDate.now();
        syncRoomStatus(oldRoom, today);
        syncRoomStatus(updated.getRoomNumber(), today);
        return updated;
    }

    /**
     * The "Save changes" button in the Edit guest dialog. Only the personal details change;
     * the room and stay dates stay exactly as they were, so no room check is needed.
     */
    public Guest updateGuestDetails(int id, String name, String contact, String email, String address)
            throws InvalidGuestDataException, GuestNotFoundException {
        int index = indexOf(id);
        Guest old = guests.get(index);
        // Build a replacement first, so a bad edit never leaves a half-changed guest.
        Guest updated = build(id, old.getRoomNumber(), name, contact, email, address,
                old.getCheckIn(), old.getCheckOut());
        guests.set(index, updated);
        files.saveGuests(guests);
        return updated;
    }

    /**
     * The "Delete guest" button. Removes the profile, then recalculates the guest's room:
     * with nobody else staying or arriving it becomes Available again. Returns the removed guest.
     * Booking and receipt records are kept as payment history.
     */
    public Guest deleteGuest(int id) throws GuestNotFoundException {
        Guest removed = guests.remove(indexOf(id));
        files.saveGuests(guests);
        syncRoomStatus(removed.getRoomNumber(), LocalDate.now());
        return removed;
    }

    /** Joins the dialog's First name + Last name boxes into the single stored name. */
    public static String joinName(String first, String last) throws InvalidGuestDataException {
        String f = first == null ? "" : first.trim();
        String l = last == null ? "" : last.trim();
        if (f.isEmpty()) throw new InvalidGuestDataException("First name cannot be empty.");
        if (l.isEmpty()) throw new InvalidGuestDataException("Last name cannot be empty.");
        return f + " " + l;
    }

    /** Bookings (and so receipts) linked to this guest. Read from the saved file, so it is always current. */
    public List<Booking> getBookingsFor(int guestId) {
        List<Booking> result = new ArrayList<>();
        for (Booking b : files.loadBookings()) {
            if (b.getGuestId() == guestId) result.add(b);
        }
        return result;
    }

    /** The second line of the "Delete guest?" dialog, depending on what the guest has. */
    public String describeDeletionImpact(Guest g) {
        LocalDate today = LocalDate.now();
        int bookings = getBookingsFor(g.getId()).size();
        String payments = bookings == 0 ? ""
                : " " + bookings + (bookings == 1 ? " booking" : " bookings")
                + " and payment history stay on record.";
        switch (g.getStatus(today)) {
            case UPCOMING:
            case IN_HOUSE:
                return "Room " + g.getRoomNumber() + " will be released and marked available." + payments;
            default:
                return bookings == 0
                        ? "This guest has no reservations or payment history."
                        : "This guest has no active reservation." + payments;
        }
    }

    // ---------------- room availability ----------------

    /**
     * Frees every room whose guest has reached their check-out date (a stay is [check-in, check-out),
     * so on the check-out day the room is available again). Call it when the app starts and each
     * time the guest table refreshes. Returns how many rooms changed, so the screen knows
     * whether anything needs redrawing.
     */
    public int releaseCheckedOutRooms() {
        LocalDate today = LocalDate.now();
        Set<Integer> rooms = new LinkedHashSet<>();
        for (Guest g : guests) {
            if (g.getStatus(today) == Guest.StayStatus.CHECKED_OUT) rooms.add(g.getRoomNumber());
        }
        int changed = 0;
        for (int roomNumber : rooms) {
            if (syncRoomStatus(roomNumber, today)) changed++;
        }
        return changed;
    }

    // ---------------- view / search / filter ----------------

    public Guest findById(int id) throws GuestNotFoundException {
        return guests.get(indexOf(id));
    }

    public int getTotalCount() {
        return guests.size();
    }

    /** Every guest, sorted by room number then check-in date. */
    public List<Guest> getAll() {
        return search("", GuestFilter.ALL);
    }

    /**
     * The one call the table needs: text search + dropdown filter, sorted by room.
     * An empty query returns everyone that passes the filter.
     */
    public List<Guest> search(String query, GuestFilter filter) {
        GuestFilter f = (filter == null) ? GuestFilter.ALL : filter;
        LocalDate today = LocalDate.now();
        List<Guest> result = new ArrayList<>();
        for (Guest g : guests) {
            if (g.matches(query) && f.accepts(g, today)) result.add(g);
        }
        result.sort(BY_ROOM_THEN_DATE);
        return result;
    }

    // ---------------- pagination ----------------

    /** Number of pages for a result list (always at least 1, so the pager shows "1"). */
    public static int pageCount(int totalItems) {
        return Math.max(1, (int) Math.ceil(totalItems / (double) PAGE_SIZE));
    }

    /** Page numbers start at 1. Out-of-range pages are clamped instead of crashing. */
    public static List<Guest> getPage(List<Guest> results, int page) {
        int safePage = Math.min(Math.max(1, page), pageCount(results.size()));
        int from = (safePage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, results.size());
        return from >= to ? Collections.<Guest>emptyList() : results.subList(from, to);
    }

    /** Text for the footer label, e.g. "Showing 1–8 of 50 guests". */
    public static String showingText(int totalItems, int page) {
        if (totalItems == 0) return "Showing 0 of 0 guests";
        int safePage = Math.min(Math.max(1, page), pageCount(totalItems));
        int from = (safePage - 1) * PAGE_SIZE + 1;
        int to = Math.min(safePage * PAGE_SIZE, totalItems);
        return "Showing " + from + "–" + to + " of " + totalItems + " guests";
    }

    // ---------------- report ----------------

    public GuestSummary getSummary() {
        LocalDate today = LocalDate.now();
        int up = 0, in = 0, out = 0, noContact = 0;
        for (Guest g : guests) {
            switch (g.getStatus(today)) {
                case UPCOMING: up++; break;
                case IN_HOUSE: in++; break;
                default: out++; break;
            }
            if (g.getContact().isEmpty()) noContact++;
        }
        return new GuestSummary(guests.size(), up, in, out, noContact);
    }

    /** Writes the guest report to data/guest_report.txt and returns its path. */
    public String exportReport(List<Guest> shown) {
        return files.saveGuestReport(shown, getSummary());
    }

    // ---------------- parsing helpers for the form ----------------

    /** Turns the Room No. text box into a number, with a friendly error. */
    public static int parseRoom(String text) throws InvalidGuestDataException {
        if (text == null || text.isBlank()) throw new InvalidGuestDataException("Room number is required.");
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new InvalidGuestDataException("Room number must be a whole number.");
        }
    }

    /** Accepts "18 Nov 2026" (as shown in the table) or "2026-11-18". */
    public static LocalDate parseDate(String text) throws InvalidGuestDataException {
        if (text == null || text.isBlank()) throw new InvalidGuestDataException("Please enter a date.");
        String t = text.trim();
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException ignored) {
            // fall through to the display format
        }
        try {
            // Normalises "18 nov 2026" / "8 Nov 2026" into the dd MMM yyyy pattern.
            String[] parts = t.split("\\s+");
            if (parts.length == 3) {
                String day = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                String mon = parts[1].isEmpty() ? "" : Character.toUpperCase(parts[1].charAt(0))
                        + parts[1].substring(1).toLowerCase();
                return LocalDate.parse(day + " " + mon + " " + parts[2], Guest.DISPLAY_DATE);
            }
        } catch (DateTimeParseException ignored) {
            // handled below
        }
        throw new InvalidGuestDataException("Date must look like 18 Nov 2026 or 2026-11-18.");
    }

    // ---------------- internals ----------------

    /** Wraps the model's IllegalArgumentException so the GUI only has to catch one checked type. */
    private Guest build(int id, int room, String name, String contact, String email, String address,
                        LocalDate in, LocalDate out) throws InvalidGuestDataException {
        try {
            return new Guest(id, room, name, contact, email, address, in, out);
        } catch (IllegalArgumentException e) {
            throw new InvalidGuestDataException(e.getMessage());
        }
    }

    /**
     * Sets a room's status from the guests still attached to it: someone staying today = Occupied,
     * otherwise someone arriving later = Reserved, otherwise Available. Saves only when it changes.
     * Returns true if the status changed. A room that no longer exists is ignored.
     */
    private boolean syncRoomStatus(int roomNumber, LocalDate today) {
        RoomStatus target = RoomStatus.AVAILABLE;
        for (Guest g : guests) {
            if (g.getRoomNumber() != roomNumber) continue;
            Guest.StayStatus s = g.getStatus(today);
            if (s == Guest.StayStatus.IN_HOUSE) {
                target = RoomStatus.OCCUPIED;
                break;
            }
            if (s == Guest.StayStatus.UPCOMING) target = RoomStatus.RESERVED;
        }
        try {
            Room room = roomService.findByNumber(roomNumber);
            if (room.getStatus() == target) return false;
            roomService.setStatus(roomNumber, target);
            return true;
        } catch (RoomNotFoundException e) {
            return false;
        }
    }

    private void ensureRoomFree(Guest candidate) throws RoomConflictException {
        for (Guest other : guests) {
            if (other.getId() == candidate.getId()) continue; // ignore itself when editing
            if (other.getRoomNumber() == candidate.getRoomNumber()
                    && other.overlaps(candidate.getCheckIn(), candidate.getCheckOut())) {
                throw new RoomConflictException("Room " + candidate.getRoomNumber()
                        + " is already booked by " + other.getName()
                        + " (" + other.getCheckInText() + " to " + other.getCheckOutText() + ").");
            }
        }
    }

    private int indexOf(int id) throws GuestNotFoundException {
        for (int i = 0; i < guests.size(); i++) {
            if (guests.get(i).getId() == id) return i;
        }
        throw new GuestNotFoundException(id);
    }

    private int nextId() {
        int max = 0;
        for (Guest g : guests) max = Math.max(max, g.getId());
        return max + 1;
    }
}
