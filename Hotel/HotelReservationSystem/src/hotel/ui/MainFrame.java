package hotel.ui;

import hotel.model.StaffAccount;
import hotel.service.AuthService;
import hotel.service.GuestService;
import hotel.service.RoomService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Main window: maroon sidebar (Guests, Rooms, Log out) and a CardLayout for the pages.
 * Home and Bookings can be added later as more cards.
 *
 * Rooms are freed when a guest is deleted or has checked out. The Guests page does this itself
 * whenever it refreshes; the Rooms page is rebuilt each time it is opened so it always shows the
 * latest statuses.
 */
public class MainFrame extends JFrame {

    private static final String GUESTS = "guests";
    private static final String ROOMS = "rooms";

    private final GuestService guests;
    private final RoomService rooms;

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JPanel roomsHolder = new JPanel(new BorderLayout());
    private final GuestPanel guestPanel;

    private final JButton guestsNav;
    private final JButton roomsNav;

    public MainFrame(StaffAccount account, AuthService auth, GuestService guests, RoomService rooms) {
        this.guests = guests;
        this.rooms = rooms;
        setTitle("Herz Hotel");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));
        setSize(1200, 760);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // free rooms of guests who already checked out while the app was closed
        guests.releaseCheckedOutRooms();

        guestPanel = new GuestPanel(guests);
        roomsHolder.setOpaque(false);
        content.add(guestPanel, GUESTS);
        content.add(roomsHolder, ROOMS);

        guestsNav = navButton("Guests", () -> showPage(GUESTS));
        roomsNav = navButton("Rooms", () -> showPage(ROOMS));

        JButton logout = navButton("Log out (" + account.getUsername() + ")", () -> {
            new LoginFrame(auth, guests, rooms).setVisible(true);
            dispose();
        });

        add(buildSidebar(logout), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);

        showPage(GUESTS);
    }

    private JPanel buildSidebar(JButton logout) {
        JPanel side = new JPanel();
        side.setBackground(LoginPanel.MAROON);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(new EmptyBorder(28, 16, 20, 16));
        side.setPreferredSize(new Dimension(210, 0));

        JLabel logo = new JLabel("Herz");
        logo.setFont(LoginPanel.font(Font.SERIF, Font.PLAIN, 30));
        logo.setForeground(LoginPanel.PAPER);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sub = new JLabel("HOTEL");
        sub.setFont(LoginPanel.font(Font.SANS_SERIF, Font.PLAIN, 9));
        sub.setForeground(LoginPanel.PAPER);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel workspace = new JLabel("WORKSPACE");
        workspace.setFont(LoginPanel.font(Font.SANS_SERIF, Font.PLAIN, 10));
        workspace.setForeground(new Color(0xD9C9C9));
        workspace.setAlignmentX(Component.LEFT_ALIGNMENT);

        side.add(logo);
        side.add(sub);
        side.add(Box.createVerticalStrut(36));
        side.add(workspace);
        side.add(Box.createVerticalStrut(14));
        side.add(guestsNav);
        side.add(Box.createVerticalStrut(10));
        side.add(roomsNav);
        side.add(Box.createVerticalGlue());
        side.add(logout);
        return side;
    }

    private void showPage(String name) {
        if (GUESTS.equals(name)) {
            guestPanel.refresh();                       // also frees checked-out rooms
        } else {
            roomsHolder.removeAll();
            roomsHolder.add(new RoomPanel(rooms), BorderLayout.CENTER);   // fresh room statuses
            roomsHolder.revalidate();
            roomsHolder.repaint();
        }
        cards.show(content, name);
        highlight(guestsNav, GUESTS.equals(name));
        highlight(roomsNav, ROOMS.equals(name));
    }

    private static void highlight(JButton b, boolean active) {
        b.setBackground(active ? new Color(0xF3F4F2) : Color.WHITE);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, active ? LoginPanel.GOLD : Color.WHITE),
                new EmptyBorder(11, 13, 11, 13)));
    }

    private static JButton navButton(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(LoginPanel.font(Font.SANS_SERIF, Font.PLAIN, 13));
        b.setForeground(LoginPanel.MAROON);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setFocusPainted(false);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        highlight(b, false);
        b.addActionListener(e -> action.run());
        return b;
    }
}
