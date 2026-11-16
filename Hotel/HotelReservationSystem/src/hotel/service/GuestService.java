package hotel.service;

import hotel.exception.GuestNotFoundException;
import hotel.exception.InvalidGuestDataException;
import hotel.exception.RoomConflictException;
import hotel.model.Guest;
import hotel.storage.FileManager;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Guest business logic: add / update / delete / search / filter / paginate / report.
 * Contains no GUI code. Every change is saved straight away through FileManager.
 */
public class GuestService {
    /** Rows per page in the Guest Directory table ("Showing 1-8 of 50 guests"). */
    public static final int PAGE_SIZE = 8;

    private static final Comparator<Guest> BY_ROOM_THEN_DATE =
            Comparator.comparingInt(Guest::getRoomNumber).thenComparing(Guest::getCheckIn);

    private final List<Guest> guests;
    private final FileManager files;

    public GuestService(List<Guest> guests, FileManager files) {
        this.guests = new ArrayList<>(guests);
        this.files = files;
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
        guests.set(index, updated);
        files.saveGuests(guests);
        return updated;
    }

    public void deleteGuest(int id) throws GuestNotFoundException {
        guests.remove(indexOf(id));
        files.saveGuests(guests);
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
