package hotel.service;

import hotel.exception.DuplicateRoomException;
import hotel.exception.InvalidRoomDataException;
import hotel.exception.RoomNotFoundException;
import hotel.model.Room;
import hotel.model.Room.RoomStatus;
import hotel.model.Room.RoomType;
import hotel.storage.FileManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Room business logic: add / update / delete / search / filter / paginate.
 * Contains no GUI code. Every change is saved straight away through FileManager.
 */
public class RoomService {
    /** Rows per page in the Room Inventory table ("Showing 1-8 of 50 rooms"). */
    public static final int PAGE_SIZE = 8;

    private static final Comparator<Room> BY_ROOM_NUMBER = Comparator.comparingInt(Room::getRoomNumber);

    private final List<Room> rooms;
    private final FileManager files;

    public RoomService(List<Room> rooms, FileManager files) {
        this.rooms = new ArrayList<>(rooms);
        this.files = files;
    }

    // ---------------- create / update / delete ----------------

    public Room addRoom(int number, RoomType type, int floor, int rate, int capacity, RoomStatus status)
            throws InvalidRoomDataException, DuplicateRoomException {
        Room r = build(number, type, floor, rate, capacity, status);
        ensureUnique(r, -1);
        rooms.add(r);
        files.saveRooms(rooms);
        return r;
    }

    /** originalNumber is the room being edited (the number may be changed by the edit). */
    public Room updateRoom(int originalNumber, int number, RoomType type, int floor, int rate,
                           int capacity, RoomStatus status)
            throws InvalidRoomDataException, DuplicateRoomException, RoomNotFoundException {
        int index = indexOf(originalNumber);
        // Build and check a replacement first, so a bad edit never leaves a half-changed room.
        Room updated = build(number, type, floor, rate, capacity, status);
        ensureUnique(updated, index);
        rooms.set(index, updated);
        files.saveRooms(rooms);
        return updated;
    }

    public void deleteRoom(int roomNumber) throws RoomNotFoundException {
        rooms.remove(indexOf(roomNumber));
        files.saveRooms(rooms);
    }

    // ---------------- view / search / filter ----------------

    public int getTotalCount() {
        return rooms.size();
    }

    /** Every room, sorted by room number (used by the booking form's room dropdown). */
    public List<Room> getAll() {
        return search("", null, null);
    }

    public Room findByNumber(int roomNumber) throws RoomNotFoundException {
        return rooms.get(indexOf(roomNumber));
    }

    /** Changes only the status (e.g. Reserved/Occupied after a booking) and saves straight away. */
    public void setStatus(int roomNumber, RoomStatus status) throws RoomNotFoundException {
        rooms.get(indexOf(roomNumber)).setStatus(status);
        files.saveRooms(rooms);
    }

    /** How many rooms have this status. Pass null to count every room. */
    public int countByStatus(RoomStatus status) {
        if (status == null) return rooms.size();
        int n = 0;
        for (Room r : rooms) if (r.getStatus() == status) n++;
        return n;
    }

    /** Text search + type filter + status filter, sorted by room number. null type/status = no restriction. */
    public List<Room> search(String query, RoomType type, RoomStatus status) {
        List<Room> result = new ArrayList<>();
        for (Room r : rooms) {
            if (r.matches(query)
                    && (type == null || r.getType() == type)
                    && (status == null || r.getStatus() == status)) {
                result.add(r);
            }
        }
        result.sort(BY_ROOM_NUMBER);
        return result;
    }

    // ---------------- pagination ----------------

    /** Number of pages for a result list (always at least 1). */
    public static int pageCount(int totalItems) {
        return Math.max(1, (int) Math.ceil(totalItems / (double) PAGE_SIZE));
    }

    /** Page numbers start at 1. Out-of-range pages are clamped instead of crashing. */
    public static List<Room> getPage(List<Room> results, int page) {
        int safePage = Math.min(Math.max(1, page), pageCount(results.size()));
        int from = (safePage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, results.size());
        return from >= to ? Collections.<Room>emptyList() : results.subList(from, to);
    }

    /** Text for the footer label, e.g. "Showing 1–8 of 50 rooms". */
    public static String showingText(int totalItems, int page) {
        if (totalItems == 0) return "Showing 0 of 0 rooms";
        int safePage = Math.min(Math.max(1, page), pageCount(totalItems));
        int from = (safePage - 1) * PAGE_SIZE + 1;
        int to = Math.min(safePage * PAGE_SIZE, totalItems);
        return "Showing " + from + "–" + to + " of " + totalItems + " rooms";
    }

    // ---------------- parsing helper for the form ----------------

    /** Turns a text box value into a whole number, with a friendly error naming the field. */
    public static int parseWholeNumber(String text, String fieldName) throws InvalidRoomDataException {
        if (text == null || text.isBlank()) throw new InvalidRoomDataException(fieldName + " is required.");
        try {
            return Integer.parseInt(text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new InvalidRoomDataException(fieldName + " must be a whole number.");
        }
    }

    // ---------------- internals ----------------

    /** Wraps the model's IllegalArgumentException so the GUI only has to catch one checked type. */
    private Room build(int number, RoomType type, int floor, int rate, int capacity, RoomStatus status)
            throws InvalidRoomDataException {
        try {
            return new Room(number, type, floor, rate, capacity, status);
        } catch (IllegalArgumentException e) {
            throw new InvalidRoomDataException(e.getMessage());
        }
    }

    /** Room numbers must be unique. ignoreIndex is the room being edited (-1 when adding). */
    private void ensureUnique(Room candidate, int ignoreIndex) throws DuplicateRoomException {
        for (int i = 0; i < rooms.size(); i++) {
            if (i == ignoreIndex) continue;
            if (rooms.get(i).getRoomNumber() == candidate.getRoomNumber()) {
                throw new DuplicateRoomException("Room " + candidate.getRoomNumber() + " already exists.");
            }
        }
    }

    private int indexOf(int roomNumber) throws RoomNotFoundException {
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).getRoomNumber() == roomNumber) return i;
        }
        throw new RoomNotFoundException(roomNumber);
    }
}