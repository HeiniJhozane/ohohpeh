package hotel.ui;

import hotel.model.StaffAccount;
import hotel.service.AuthService;

import javax.swing.*;
import java.awt.*;

/** Placeholder main window. Replace with the CardLayout + sidebar (Dashboard, Rooms, ...). */
public class MainFrame extends JFrame {

    public MainFrame(StaffAccount account, AuthService auth) {
        setTitle("Herz Hotel");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JLabel welcome = new JLabel("Welcome, " + account.getUsername() + " (" + account.getRole() + ")",
                SwingConstants.CENTER);
        welcome.setFont(LoginPanel.font(Font.SERIF, Font.PLAIN, 28));
        add(welcome, BorderLayout.CENTER);

        JButton logout = new JButton("Log out");
        logout.addActionListener(e -> {
            new LoginFrame(auth).setVisible(true);
            dispose();
        });
        JPanel bottom = new JPanel();
        bottom.add(logout);
        add(bottom, BorderLayout.SOUTH);
    }
}