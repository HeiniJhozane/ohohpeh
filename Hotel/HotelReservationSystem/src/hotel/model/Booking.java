package hotel.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * A reservation plus its payment. Validation lives in the setters (same style as Guest and Room),
 * which throw IllegalArgumentException; BookingService turns those into InvalidBookingDataException.
 * The room type and nightly rate are copied into the booking so old receipts never change
 * if the room's rate is edited later.
 */
public class Booking {

    /** Advance = reserved for a future date. Walk-in = checking in today. toString() is the button label. */
    public enum BookingType {
        ADVANCE("Advance booking"), WALK_IN("Walk-in booking");

        private final String label;

        BookingType(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    private int id;
    private int guestId;          // 0 until the matching Guest record has been created
    private BookingType type;
    private int roomNumber;
    private Room.RoomType roomType;
    private int rate;             // pesos per night at the time of booking
    private int adults;
    private String guestName;
    private String contact;       // optional
    private LocalDate checkIn;
    private LocalDate checkOut;
    private int amountPaid;
    private LocalDate paidOn;     // null = not paid yet
    private String receivedBy;

    public Booking(int id, int guestId, BookingType type, int roomNumber, Room.RoomType roomType,
                   int rate, int adults, String guestName, String contact,
                   LocalDate checkIn, LocalDate checkOut,
                   int amountPaid, LocalDate paidOn, String receivedBy) {
        setId(id);
        setGuestId(guestId);
        setType(type);
        setRoomNumber(roomNumber);
        setRoomType(roomType);
        setRate(rate);
        setAdults(adults);
        setGuestName(guestName);
        setContact(contact);
        setStay(checkIn, checkOut);
        setAmountPaid(amountPaid);
        this.paidOn = paidOn;
        setReceivedBy(receivedBy);
    }

    // ---------------- getters / validating setters ----------------

    public int getId() { return id; }

    public void setId(int id) {
        if (id <= 0) throw new IllegalArgumentException("Booking ID must be positive.");
        this.id = id;
    }

    public int getGuestId() { return guestId; }

    public void setGuestId(int guestId) {
        if (guestId < 0) throw new IllegalArgumentException("Guest ID cannot be negative.");
        this.guestId = guestId;
    }

    public BookingType getType() { return type; }

    public void setType(BookingType type) {
        if (type == null) throw new IllegalArgumentException("Choose Advance booking or Walk-in booking.");
        this.type = type;
    }

    public int getRoomNumber() { return roomNumber; }

    public void setRoomNumber(int roomNumber) {
        if (roomNumber <= 0 || roomNumber > 9999)
            throw new IllegalArgumentException("Room number must be between 1 and 9999.");
        this.roomNumber = roomNumber;
    }

    public Room.RoomType getRoomType() { return roomType; }

    public void setRoomType(Room.RoomType roomType) {
        if (roomType == null) throw new IllegalArgumentException("Please choose a room.");
        this.roomType = roomType;
    }

    public int getRate() { return rate; }

    public void setRate(int rate) {
        if (rate <= 0 || rate > 1_000_000)
            throw new IllegalArgumentException("Rate per night must be between 1 and 1,000,000.");
        this.rate = rate;
    }

    public int getAdults() { return adults; }

    public void setAdults(int adults) {
        if (adults < 1 || adults > 10)
            throw new IllegalArgumentException("Number of guests must be between 1 and 10.");
        this.adults = adults;
    }

    public String getGuestName() { return guestName; }

    public void setGuestName(String guestName) {
        if (guestName == null || guestName.isBlank())
            throw new IllegalArgumentException("Guest name cannot be empty.");
        if (guestName.trim().length() < 2)
            throw new IllegalArgumentException("Guest name is too short.");
        this.guestName = guestName.trim();
    }

    public String getContact() { return contact; }

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

    public LocalDate getCheckIn() { return checkIn; }

    public LocalDate getCheckOut() { return checkOut; }

    /** Both dates are validated together so they can never be out of order. */
    public void setStay(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null) throw new IllegalArgumentException("Check-in date is required.");
        if (checkOut == null) throw new IllegalArgumentException("Check-out date is required.");
        if (!checkOut.isAfter(checkIn))
            throw new IllegalArgumentException("Check-out date must be after the check-in date.");
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }

    public int getAmountPaid() { return amountPaid; }

    public void setAmountPaid(int amountPaid) {
        if (amountPaid < 0) throw new IllegalArgumentException("Amount paid cannot be negative.");
        this.amountPaid = amountPaid;
    }

    public LocalDate getPaidOn() { return paidOn; }

    public String getReceivedBy() { return receivedBy; }

    public void setReceivedBy(String receivedBy) {
        this.receivedBy = receivedBy == null ? "" : receivedBy.trim();
    }

    // ---------------- behaviour ----------------

    public int getNights() {
        return (int) ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    /** Total stay cost = nightly rate x nights. */
    public int getTotal() {
        return rate * getNights();
    }

    public int getBalance() {
        return Math.max(0, getTotal() - amountPaid);
    }

    public boolean isPaid() {
        return paidOn != null && amountPaid >= getTotal();
    }

    /** Marks the booking as paid. Called when staff press "Record payment". */
    public void recordPayment(int amount, LocalDate date, String staffName) {
        if (amount <= 0) throw new IllegalArgumentException("Payment must be more than zero.");
        if (date == null) throw new IllegalArgumentException("Payment date is required.");
        this.amountPaid = amount;
        this.paidOn = date;
        setReceivedBy(staffName);
    }

    public String getStayRangeText() {
        return rangeText(checkIn, checkOut);
    }

    // ---------------- display helpers (shared with the receipt on screen) ----------------

    /** "16–18 Nov 2026" when both dates are in the same month, otherwise "30 Nov 2026 – 2 Dec 2026". */
    public static String rangeText(LocalDate in, LocalDate out) {
        if (in.getYear() == out.getYear() && in.getMonth() == out.getMonth())
            return in.getDayOfMonth() + "–" + out.format(Guest.DISPLAY_DATE);
        return in.format(Guest.DISPLAY_DATE) + " – " + out.format(Guest.DISPLAY_DATE);
    }

    public static String nightsText(int nights) {
        return nights + (nights == 1 ? " night" : " nights");
    }

    /** 15000 -> "15,000" */
    public static String money(int pesos) {
        return String.format("%,d", pesos);
    }

    // ---------------- file format ----------------
    // One line per booking:
    // id|guestId|TYPE|room|ROOMTYPE|rate|adults|name|contact|checkIn|checkOut|paid|paidOn|receivedBy
    // '|' and '\' inside text are escaped (same scheme as Guest).

    public String toFileString() {
        return id + "|" + guestId + "|" + type.name() + "|" + roomNumber + "|" + roomType.name()
                + "|" + rate + "|" + adults + "|" + esc(guestName) + "|" + esc(contact)
                + "|" + checkIn + "|" + checkOut + "|" + amountPaid
                + "|" + (paidOn == null ? "" : paidOn.toString()) + "|" + esc(receivedBy);
    }

    /** Rebuilds a Booking from one line. Throws a RuntimeException on any bad line (caller skips it). */
    public static Booking fromFileString(String line) {
        List<String> p = splitFields(line);
        if (p.size() != 14) throw new IllegalArgumentException("Expected 14 fields but found " + p.size());
        return new Booking(
                Integer.parseInt(p.get(0)),
                Integer.parseInt(p.get(1)),
                BookingType.valueOf(p.get(2)),
                Integer.parseInt(p.get(3)),
                Room.RoomType.valueOf(p.get(4)),
                Integer.parseInt(p.get(5)),
                Integer.parseInt(p.get(6)),
                p.get(7), p.get(8),
                LocalDate.parse(p.get(9)),
                LocalDate.parse(p.get(10)),
                Integer.parseInt(p.get(11)),
                p.get(12).isEmpty() ? null : LocalDate.parse(p.get(12)),
                p.get(13));
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
        return "Booking #" + id + " - Room " + roomNumber + " - " + guestName;
    }
}
