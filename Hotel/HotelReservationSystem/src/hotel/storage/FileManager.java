package hotel.storage;

import hotel.model.Booking;
import hotel.model.Guest;
import hotel.model.Room;
import hotel.model.StaffAccount;
import hotel.service.AuthService;
import hotel.service.GuestSummary;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All file reading/writing lives here: staff, guests, rooms and bookings (plus the text reports/receipts). */
public class FileManager {
    private static final String DATA_DIR = "data";
    private static final String STAFF_FILE = DATA_DIR + "/staff.txt";
    private static final String GUEST_FILE = DATA_DIR + "/guests.txt";
    private static final String GUEST_REPORT_FILE = DATA_DIR + "/guest_report.txt";
    private static final String ROOM_FILE = DATA_DIR + "/rooms.txt";
    private static final String BOOKING_FILE = DATA_DIR + "/bookings.txt";

    /** Loads staff accounts. A missing file is recreated with a default admin; bad lines are skipped. */
    public Map<String, StaffAccount> loadStaff() {
        Map<String, StaffAccount> map = new HashMap<>();
        File file = new File(STAFF_FILE);

        if (!file.exists()) {
            StaffAccount admin = new StaffAccount("admin", AuthService.hash("admin123"), "ADMIN");
            map.put(admin.getUsername(), admin);
            saveStaff(map.values());
            return map;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    String[] p = line.split(",");
                    StaffAccount acc = new StaffAccount(p[0], p[1], p[2]);
                    map.put(acc.getUsername(), acc);
                } catch (RuntimeException e) { // bad / corrupted line
                    System.err.println("Skipped bad staff line: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read " + STAFF_FILE + ": " + e.getMessage());
        }

        if (map.isEmpty()) { // file existed but was empty or fully corrupted
            StaffAccount admin = new StaffAccount("admin", AuthService.hash("admin123"), "ADMIN");
            map.put(admin.getUsername(), admin);
            saveStaff(map.values());
        }
        return map;
    }

    public void saveStaff(Collection<StaffAccount> accounts) {
        new File(DATA_DIR).mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(STAFF_FILE))) {
            for (StaffAccount a : accounts) {
                bw.write(a.toFileString());
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Could not save " + STAFF_FILE + ": " + e.getMessage());
        }
    }

    // ---------------- guests ----------------

    /**
     * Loads guests from data/guests.txt. A missing file is created with a few sample guests so the
     * screen isn't empty on first run. Bad lines are skipped, and the original file is backed up
     * to guests.txt.bak first so nothing is lost if a line was only damaged.
     */
    public List<Guest> loadGuests() {
        List<Guest> list = new ArrayList<>();
        File file = new File(GUEST_FILE);

        if (!file.exists()) {
            list = sampleGuests();
            saveGuests(list);
            return list;
        }

        boolean skippedAny = false;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    list.add(Guest.fromFileString(line));
                } catch (RuntimeException e) { // bad / corrupted line
                    skippedAny = true;
                    System.err.println("Skipped bad guest line: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read " + GUEST_FILE + ": " + e.getMessage());
        }

        if (skippedAny) backUpGuestFile(file);
        return list;
    }

    public void saveGuests(Collection<Guest> guests) {
        new File(DATA_DIR).mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(GUEST_FILE))) {
            for (Guest g : guests) {
                bw.write(g.toFileString());
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Could not save " + GUEST_FILE + ": " + e.getMessage());
        }
    }

    /** Writes a plain-text guest list + totals to data/guest_report.txt. Returns the path, or null on failure. */
    public String saveGuestReport(List<Guest> guests, GuestSummary summary) {
        new File(DATA_DIR).mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(GUEST_REPORT_FILE))) {
            bw.write("HERZ HOTEL - GUEST REPORT (" + LocalDate.now().format(Guest.DISPLAY_DATE) + ")");
            bw.newLine();
            bw.write(summary.toString());
            bw.newLine();
            bw.newLine();
            for (Guest g : guests) {
                bw.write(String.format("Room %-5d %-28s %-18s %-22s %s to %s",
                        g.getRoomNumber(), g.getName(),
                        g.getContact().isEmpty() ? "(no contact)" : g.getContact(),
                        g.getAddress(), g.getCheckInText(), g.getCheckOutText()));
                bw.newLine();
            }
            return new File(GUEST_REPORT_FILE).getPath();
        } catch (IOException e) {
            System.err.println("Could not save " + GUEST_REPORT_FILE + ": " + e.getMessage());
            return null;
        }
    }

    private void backUpGuestFile(File file) {
        try {
            Files.copy(file.toPath(), new File(GUEST_FILE + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Could not back up " + GUEST_FILE + ": " + e.getMessage());
        }
    }

    /** Sample rows matching the Guest Directory design. Delete data/guests.txt to reset to these. */
    private List<Guest> sampleGuests() {
        LocalDate in = LocalDate.of(2026, 11, 18);
        LocalDate out = LocalDate.of(2026, 11, 20);
        String phone = "+63 912 098 978";
        List<Guest> list = new ArrayList<>();
        list.add(new Guest(1, 201, "Trisha Mae Dajac", phone, "", "Daraga, Albay", in, out));
        list.add(new Guest(2, 202, "Francis Jordan Nodalo", phone, "", "Tabaco City", in, out));
        list.add(new Guest(3, 203, "Lara Marie Nunez", phone, "", "Daraga, Albay", in, out));
        list.add(new Guest(4, 204, "Jasmine Bea", phone, "", "Legazpi City", in, out));
        list.add(new Guest(5, 205, "Khristine Tibor", phone, "", "Legazpi City", in, out));
        list.add(new Guest(6, 206, "Heini Jhozane Llaneta", phone, "", "Manito, Albay", in, out));
        list.add(new Guest(7, 207, "Rachelle Ortiz", "", "", "Guinobatan, Albay", in, out));
        list.add(new Guest(8, 208, "Ella Marie", phone, "", "Daraga, Albay", in, out));
        return list;
    }

    // ---------------- bookings ----------------

    /** Loads bookings from data/bookings.txt. A missing file just means no bookings yet. Bad lines are skipped. */
    public List<Booking> loadBookings() {
        List<Booking> list = new ArrayList<>();
        File file = new File(BOOKING_FILE);
        if (!file.exists()) return list;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    list.add(Booking.fromFileString(line));
                } catch (RuntimeException e) { // bad / corrupted line
                    System.err.println("Skipped bad booking line: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read " + BOOKING_FILE + ": " + e.getMessage());
        }
        return list;
    }

    public void saveBookings(Collection<Booking> bookings) {
        new File(DATA_DIR).mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(BOOKING_FILE))) {
            for (Booking b : bookings) {
                bw.write(b.toFileString());
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Could not save " + BOOKING_FILE + ": " + e.getMessage());
        }
    }

    /** Writes a plain-text payment receipt to data/receipt_000N.txt. Returns the path, or null on failure. */
    public String saveReceipt(Booking b) {
        new File(DATA_DIR).mkdirs();
        String path = DATA_DIR + "/receipt_" + String.format("%04d", b.getId()) + ".txt";
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
            String[] lines = {
                "HERZ HOTEL - PAYMENT RECEIPT",
                "Receipt no.   : " + String.format("%04d", b.getId()),
                "Status        : " + (b.isPaid() ? "PAID" : "UNPAID"),
                "",
                "Guest         : " + b.getGuestName(),
                "Booking type  : " + b.getType(),
                "",
                "STAY SUMMARY",
                b.getRoomType() + " - Room " + b.getRoomNumber(),
                b.getStayRangeText() + " (" + Booking.nightsText(b.getNights()) + ")",
                "",
                Booking.money(b.getRate()) + " x " + Booking.nightsText(b.getNights())
                        + " = " + Booking.money(b.getTotal()),
                "Total stay cost : " + Booking.money(b.getTotal()),
                "Room charge     : " + Booking.money(b.getTotal()),
                "Payment received: " + Booking.money(b.getAmountPaid()),
                "",
                b.getPaidOn() == null
                        ? "Payment not recorded yet"
                        : "Paid " + b.getPaidOn().format(Guest.DISPLAY_DATE)
                                + " - Received by " + b.getReceivedBy(),
                "",
                "Thank you for choosing Herz"
            };
            for (String line : lines) {
                bw.write(line);
                bw.newLine();
            }
            return new File(path).getPath();
        } catch (IOException e) {
            System.err.println("Could not save " + path + ": " + e.getMessage());
            return null;
        }
    }

    // ---------------- rooms ----------------

    /**
     * Loads rooms from data/rooms.txt. A missing file is created with 50 sample rooms so the
     * screen isn't empty on first run. Bad lines are skipped.
     */
    public List<Room> loadRooms() {
        List<Room> list = new ArrayList<>();
        File file = new File(ROOM_FILE);

        if (!file.exists()) {
            list = sampleRooms();
            saveRooms(list);
            return list;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    list.add(Room.fromFileString(line));
                } catch (RuntimeException e) { // bad / corrupted line
                    System.err.println("Skipped bad room line: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read " + ROOM_FILE + ": " + e.getMessage());
        }
        return list;
    }

    public void saveRooms(Collection<Room> rooms) {
        new File(DATA_DIR).mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ROOM_FILE))) {
            for (Room r : rooms) {
                bw.write(r.toFileString());
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Could not save " + ROOM_FILE + ": " + e.getMessage());
        }
    }

    /** 50 sample rooms: 5 floors x 10 rooms (15 available, 30 occupied, 5 reserved). Delete data/rooms.txt to reset. */
    private List<Room> sampleRooms() {
        List<Room> list = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            int floor = i / 10 + 1;
            int slot = i % 10;
            int number = floor * 100 + slot + 1;               // 101..110, 201..210, ...

            Room.RoomType type = floor == 1 ? Room.RoomType.STANDARD
                    : floor <= 3 ? Room.RoomType.DELUXE : Room.RoomType.SUITE;
            int rate = type == Room.RoomType.STANDARD ? 2000 : type == Room.RoomType.DELUXE ? 3500 : 5000;
            int capacity = type == Room.RoomType.STANDARD ? 2 : type == Room.RoomType.DELUXE ? 3 : 4;

            Room.RoomStatus status = slot < 3 ? Room.RoomStatus.AVAILABLE
                    : slot == 9 ? Room.RoomStatus.RESERVED : Room.RoomStatus.OCCUPIED;

            list.add(new Room(number, type, floor, rate, capacity, status));
        }
        return list;
    }
}