package hotel.ui;

import hotel.exception.InvalidCredentialsException;
import hotel.model.StaffAccount;
import hotel.service.AuthService;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * The first screen: split layout with a scenic hero on the left and the
 * admin sign-in form on the right. Holds no business logic; it only calls AuthService.
 */
public class LoginPanel extends JPanel {

    // ---- palette ----
    static final Color PAPER = new Color(0xF7F5F0);
    static final Color NAVY = new Color(0x1B2A41);
    static final Color GOLD = new Color(0xE3C77A);
    static final Color GOLD_DARK = new Color(0x8A6A2F);
    static final Color MAROON = new Color(0x450404);
    static final Color MAROON_HOVER = new Color(0x5E0A0A);
    static final Color BORDER = new Color(0xE2DED5);
    static final Color MUTED = new Color(0x6B7280);
    static final Color ERROR = new Color(0xB42318);

    private static final int FORM_WIDTH = 380;
    private static final int MAX_ATTEMPTS = 3;
    private static final int LOCKOUT_SECONDS = 10;
    private static final String PREF_USER = "rememberedUser";

    private static final String SERIF = pickFont(Font.SERIF, "Playfair Display", "Georgia", "Times New Roman");
    private static final String SANS = pickFont(Font.SANS_SERIF, "Inter", "Segoe UI", "Helvetica Neue", "Arial");

    private final AuthService auth;
    private final Consumer<StaffAccount> onSuccess;
    private final Preferences prefs = Preferences.userNodeForPackage(LoginPanel.class);

    private final JTextField userField = new JTextField();
    private final JPasswordField passField = new JPasswordField();
    private final JCheckBox rememberBox = new JCheckBox("Keep me signed in");
    private final JLabel noteLabel = new JLabel("Secure access for authorized hotel staff only.");
    private final PrimaryButton signInButton = new PrimaryButton("Sign in  →");

    private int failedAttempts = 0;
    private Timer lockTimer;

    public LoginPanel(AuthService auth, Consumer<StaffAccount> onSuccess) {
        this.auth = auth;
        this.onSuccess = onSuccess;

        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridy = 0;

        c.gridx = 0;
        c.weightx = 0.528;
        add(new HeroPanel(), c);

        c.gridx = 1;
        c.weightx = 0.472;
        add(buildFormSide(), c);

        String saved = prefs.get(PREF_USER, "");
        if (!saved.isEmpty()) {
            userField.setText(saved);
            rememberBox.setSelected(true);
        }
    }

    /** Lets the window make Enter trigger Sign in. */
    public JButton getDefaultButton() {