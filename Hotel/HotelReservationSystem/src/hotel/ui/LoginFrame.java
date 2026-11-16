package hotel.ui;

import hotel.service.AuthService;
import hotel.service.GuestService;

import javax.swing.*;
import java.awt.*;

/** The window shown when the app starts. Opens MainFrame after a successful login. */
public class LoginFrame extends JFrame {

    public LoginFrame(AuthService auth, GuestService guests) {
        setTitle("Herz Hotel – Admin Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));
        setSize(1100, 700);
        setLocationRelativeTo(null);

        LoginPanel panel = new LoginPanel(auth, account -> {
            new MainFrame(account, auth, guests).setVisible(true);
            dispose();
        });
        setContentPane(panel);
        getRootPane().setDefaultButton(panel.getDefaultButton()); // Enter = Sign in
    }
}