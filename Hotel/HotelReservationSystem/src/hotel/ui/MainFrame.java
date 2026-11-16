package hotel.ui;

import hotel.model.StaffAccount;
import hotel.service.AuthService;
import hotel.service.GuestService;
import hotel.service.RoomService;

import javax.swing.*;
import java.awt.*;

/**
 * Temporary main window: shows the Room management page so you can try it.
 * Later, replace the CENTER with the CardLayout + sidebar (Home, Guests, Rooms, Bookings).
 */
public class MainFrame extends JFrame {

    private final GuestService guests;
    private final RoomService rooms;

    public MainFrame(StaffAccount account, AuthService auth, GuestService guests, RoomService rooms) {
        this.guests = guests;
        this.rooms = rooms;
        setTitle("Herz Hotel");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // the Room management page
        add(new RoomPanel(rooms), BorderLayout.CENTER);

        // simple log out bar at the bottom
        JButton logout = new JButton("Log out (" + account.getUsername() + ")");
        logout.addActionListener(e -> {
            new LoginFrame(auth, guests, rooms).setVisible(true);
            dispose();
        });
        JPanel bottom = new JPanel();
        bottom.add(logout);
        add(bottom, BorderLayout.SOUTH);
    }
}
