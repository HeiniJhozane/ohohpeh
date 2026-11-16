package hotel;

import hotel.service.AuthService;
import hotel.storage.FileManager;
import hotel.ui.LoginFrame;

import javax.swing.*;

/** Launches the app only: load staff data, then show the login screen. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            AuthService auth = new AuthService(new FileManager().loadStaff());
            new LoginFrame(auth).setVisible(true);
        });
    }
}