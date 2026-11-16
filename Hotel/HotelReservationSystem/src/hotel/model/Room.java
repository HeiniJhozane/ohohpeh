package hotel.model;

/**
 * A hotel room. Validation lives in the setters (same style as Guest),
 * which throw IllegalArgumentException; RoomService turns those into InvalidRoomDataException.
 */
public class Room {

    /** The kinds of room shown in the "All types" dropdown. toString() is the label. */
    public enum RoomType {
        STANDARD("Standard"), DELUXE("Deluxe"), SUITE("Suite");

        private final String label;

        RoomType(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    /** Room status shown as the coloured badge and used by the tabs. */
    public enum RoomStatus {
        AVAILABLE("Available"), OCCUPIED("Occupied"), RESERVED("Reserved");

        private final String label;

        RoomStatus(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    private int roomNumber;
    private RoomType type;
    private int floor;
    private int rate;       // price per night in pesos
    private int capacity;   // max number of guests
    private RoomStatus status;

    public Room(int roomNumber, RoomType type, int floor, int rate, int capacity, RoomStatus status) {
        setRoomNumber(roomNumber);
        setType(type);
        setFloor(floor);
        setRate(rate);
        setCapacity(capacity);
        setStatus(status);
    }

    // ---------------- getters / validating setters ----------------

    public int getRoomNumber() { return roomNumber; }

    public void setRoomNumber(int roomNumber) {
        if (roomNumber <= 0 || roomNumber > 9999)
            throw new IllegalArgumentException("Room number must be between 1 and 9999.");
        this.roomNumber = roomNumber;
    }

    public RoomType getType() { return type; }

    public void setType(RoomType type) {
        if (type == null) throw new IllegalArgumentException("Please choose a room type.");
        this.type = type;
    }

    public int getFloor() { return floor; }

    public void setFloor(int floor) {
        if (floor < 1 || floor > 99)
            throw new IllegalArgumentException("Floor must be between 1 and 99.");
        this.floor = floor;
    }

    public int getRate() { return rate; }

    public void setRate(int rate) {
        if (rate <= 0 || rate > 1_000_000)
            throw new IllegalArgumentException("Rate per night must be between 1 and 1,000,000.");
        this.rate = rate;
    }

    public int getCapacity() { return capacity; }

    public void setCapacity(int capacity) {
        if (capacity < 1 || capacity > 10)
            throw new IllegalArgumentException("Capacity must be between 1 and 10 guests.");
        this.capacity = capacity;
    }

    public RoomStatus getStatus() { return status; }

    public void setStatus(RoomStatus status) {
        if (status == null) throw new IllegalArgumentException("Please choose a room status.");
        this.status = status;
    }

    // ---------------- behaviour ----------------

    /** Case-insensitive match on room number or room type (used by the search box). */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase();
        return String.valueOf(roomNumber).contains(q)
                || type.toString().toLowerCase().contains(q);
    }

    // ---------------- file format ----------------
    // One line per room: roomNumber,TYPE,floor,rate,STATUS,capacity  (no free text, so commas are safe)

    public String toFileString() {
        return roomNumber + "," + type.name() + "," + floor + "," + rate + "," + status.name() + "," + capacity;
    }

    /** Rebuilds a Room from one line. Throws a RuntimeException on any bad line (caller skips it). */
    public static Room fromFileString(String line) {
        String[] p = line.split(",");
        if (p.length != 6) throw new IllegalArgumentException("Expected 6 fields but found " + p.length);
        return new Room(
                Integer.parseInt(p[0].trim()),
                RoomType.valueOf(p[1].trim()),
                Integer.parseInt(p[2].trim()),
                Integer.parseInt(p[3].trim()),
                Integer.parseInt(p[5].trim()),
                RoomStatus.valueOf(p[4].trim()));
    }

    @Override
    public String toString() {
        return "Room " + roomNumber + " (" + type + ")";
    }
}
