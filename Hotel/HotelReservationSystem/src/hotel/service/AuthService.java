package hotel.service;

import hotel.exception.InvalidCredentialsException;
import hotel.model.StaffAccount;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/** Checks staff credentials. Contains no GUI code. */
public class AuthService {
    private final Map<String, StaffAccount> accounts; // keyed by username

    public AuthService(Map<String, StaffAccount> accounts) {
        this.accounts = accounts;
    }

    public StaffAccount login(String username, String password) throws InvalidCredentialsException {
        if (username == null || username.isBlank() || password == null || password.isEmpty())
            throw new InvalidCredentialsException("Enter your username and password.");

        StaffAccount acc = accounts.get(username.trim());
        // Same message for unknown user and wrong password on purpose.
        if (acc == null || !acc.getPasswordHash().equals(hash(password)))
            throw new InvalidCredentialsException("Incorrect username or password.");
        return acc;
    }

    public static String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}