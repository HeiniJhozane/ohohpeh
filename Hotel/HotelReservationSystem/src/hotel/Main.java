package hotel;

import hotel.service.AuthService;
import hotel.service.GuestService;
import hotel.service.RoomService;
import hotel.storage.FileManager;
import hotel.ui.LoginFrame;

import javax.swing.*;

/** Launches the app only: load data from files, then show the login screen. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            FileManager files = new FileManager();
            AuthService auth = new AuthService(files.loadStaff());
            GuestService guests = new GuestService(files.loadGuests(), files);
            RoomService rooms = new RoomService(files.loadRooms(), files);
            new LoginFrame(auth, guests, rooms).setVisible(true);
        });
    }
}
