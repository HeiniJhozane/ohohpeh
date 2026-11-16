package hotel.storage;

import hotel.model.StaffAccount;
import hotel.service.AuthService;

import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** All file reading/writing lives here. (Guests, rooms and bookings get added later.) */
public class FileManager {
    private static final String DATA_DIR = "data";
    private static final String STAFF_FILE = DATA_DIR + "/staff.txt";

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
}