package hotel.ui;

import hotel.service.AuthService;

import javax.swing.*;
import java.awt.*;

/** The window shown when the app starts. Opens MainFrame after a successful login. */
public class LoginFrame extends JFrame {

    public LoginFrame(AuthService auth) {
        setTitle("Herz Hotel – Admin Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));
        setSize(1100, 700);
        setLocationRelativeTo(null);

        LoginPanel panel = new LoginPanel(auth, account -> {
            new MainFrame(account, auth).setVisible(true);
            dispose();
        });
        setContentPane(panel);
        getRootPane().setDefaultButton(panel.getDefaultButton()); // Enter = Sign in
    }
}