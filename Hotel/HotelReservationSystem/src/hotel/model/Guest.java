package hotel.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A hotel guest and their stay. All validation lives in the setters (same style as StaffAccount),
 * which throw IllegalArgumentException; GuestService turns those into InvalidGuestDataException.
 */
public class Guest {

    /** Where a guest is in their stay, relative to a given day. */
    public enum StayStatus { UPCOMING, IN_HOUSE, CHECKED_OUT }

    /** Display format used by the table, e.g. "18 Nov 2026". */
    public static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private int id;
    private int roomNumber;
    private String name;
    private String contact;   // optional
    private String email;     // optional
    private String address;
    private LocalDate checkIn;
    private LocalDate checkOut;

    public Guest(int id, int roomNumber, String name, String contact, String email,
                 String address, LocalDate checkIn, LocalDate checkOut) {
        setId(id);
        setRoomNumber(roomNumber);
        setName(name);
        setContact(contact);
        setEmail(email);
        setAddress(address);
        setStay(checkIn, checkOut);
    }

    // ---------------- getters / validating setters ----------------

    public int getId() {
        return id;
    }

    public void setId(int id) {
        if (id <= 0) throw new IllegalArgumentException("Guest ID must be positive.");
        this.id = id;
    }

    public int getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(int roomNumber) {
        if (roomNumber <= 0 || roomNumber > 9999)
            throw new IllegalArgumentException("Room number must be between 1 and 9999.");
        this.roomNumber = roomNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Guest name cannot be empty.");
        if (name.trim().length() < 2)
            throw new IllegalArgumentException("Guest name is too short.");
        this.name = name.trim();
    }

    public String getContact() {
        return contact;
    }

    /** Optional. If given it must look like a phone number, e.g. "+63 912 098 978". */
    public void setContact(String contact) {
        if (contact == null || contact.isBlank()) {
            this.contact = "";
            return;
        }
        String digitsOnly = contact.replaceAll("[\\s\\-()]", "");
        if (!digitsOnly.matches("\\+?\\d{7,15}"))
            throw new IllegalArgumentException("Contact number must have 7 to 15 digits (e.g. +63 912 098 978).");
        this.contact = contact.trim();
    }

    public String getEmail() {
        return email;
    }

    /** Optional. If given it must look like an email address. */
    public void setEmail(String email) {
        if (email == null || email.isBlank()) {
            this.email = "";
            return;
        }
        if (!email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
            throw new IllegalArgumentException("Email address is not valid.");
        this.email = email.trim();
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        if (address == null || address.isBlank())
            throw new IllegalArgumentException("Address cannot be empty.");
        this.address = address.trim();
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    /** Both dates are validated together so they can never be out of order. */
    public void setStay(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null) throw new IllegalArgumentException("Check-in date is required.");
        if (checkOut == null) throw new IllegalArgumentException("Check-out date is required.");
        if (!checkOut.isAfter(checkIn))
            throw new IllegalArgumentException("Check-out date must be after the check-in date.");
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }

    // ---------------- behaviour ----------------

    public StayStatus getStatus(LocalDate today) {
        if (today.isBefore(checkIn)) return StayStatus.UPCOMING;
        if (!today.isBefore(checkOut)) return StayStatus.CHECKED_OUT;
        return StayStatus.IN_HOUSE;
    }

    /** Stays are half-open [checkIn, checkOut) so one guest can leave the day another arrives. */
    public boolean overlaps(LocalDate otherIn, LocalDate otherOut) {
        return checkIn.isBefore(otherOut) && otherIn.isBefore(checkOut);
    }

    public int getNights() {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    /** "G-0241" style code shown under the title of the Edit guest dialog. */
    public String getCode() {
        return String.format("G-%04d", id);
    }

    /** Everything before the last space, e.g. "Trisha Mae" for "Trisha Mae Dajac" (whole name if one word). */
    public String getFirstName() {
        int i = name.lastIndexOf(' ');
        return i < 0 ? name : name.substring(0, i).trim();
    }

    /** The last word of the name, e.g. "Dajac" (empty if the name is one word). */
    public String getLastName() {
        int i = name.lastIndexOf(' ');
        return i < 0 ? "" : name.substring(i + 1).trim();
    }

    /** Subtitle for the Edit guest dialog: "No active reservations", "Currently staying · Room 205", ... */
    public String getReservationText(LocalDate today) {
        switch (getStatus(today)) {
            case UPCOMING: return "Upcoming reservation \u00b7 Room " + roomNumber;
            case IN_HOUSE: return "Currently staying \u00b7 Room " + roomNumber;
            default:       return "No active reservations";
        }
    }

    public String getCheckInText() {
        return checkIn.format(DISPLAY_DATE);
    }

    public String getCheckOutText() {
        return checkOut.format(DISPLAY_DATE);
    }

    /** Case-insensitive match on name, contact (ignoring spaces/dashes), email, address or room number. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase();
        String qDigits = q.replaceAll("[\\s\\-()]", "");
        return name.toLowerCase().contains(q)
                || email.toLowerCase().contains(q)
                || address.toLowerCase().contains(q)
                || String.valueOf(roomNumber).contains(q)
                || (!qDigits.isEmpty() && contact.replaceAll("[\\s\\-()]", "").contains(qDigits));
    }

    // ---------------- file format ----------------
    // One line per guest: id|room|name|contact|email|address|checkIn|checkOut
    // '|' and '\' inside text are escaped so addresses like "Daraga, Albay" are safe.

    public String toFileString() {
        return id + "|" + roomNumber + "|" + esc(name) + "|" + esc(contact) + "|" + esc(email)
                + "|" + esc(address) + "|" + checkIn + "|" + checkOut;
    }

    /** Rebuilds a Guest from one line. Throws a RuntimeException on any bad line (caller skips it). */
    public static Guest fromFileString(String line) {
        List<String> p = splitFields(line);
        if (p.size() != 8) throw new IllegalArgumentException("Expected 8 fields but found " + p.size());
        return new Guest(
                Integer.parseInt(p.get(0)),
                Integer.parseInt(p.get(1)),
                p.get(2), p.get(3), p.get(4), p.get(5),
                LocalDate.parse(p.get(6)),
                LocalDate.parse(p.get(7)));
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n").replace("\r", "");
    }

    private static List<String> splitFields(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\\' && i + 1 < line.length()) {
                char next = line.charAt(++i);
                cur.append(next == 'p' ? '|' : next == 'n' ? '\n' : next);
            } else if (ch == '|') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    @Override
    public String toString() {
        return "Room " + roomNumber + " - " + name;
    }
}
