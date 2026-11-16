package hotel.model;

/** A front-desk / admin account. Passwords are stored only as SHA-256 hashes. */
public class StaffAccount {
    private String username;
    private String passwordHash;
    private String role;

    public StaffAccount(String username, String passwordHash, String role) {
        setUsername(username);
        setPasswordHash(passwordHash);
        setRole(role);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Username cannot be empty.");
        if (username.contains(","))
            throw new IllegalArgumentException("Username cannot contain commas.");
        this.username = username.trim();
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank())
            throw new IllegalArgumentException("Password hash cannot be empty.");
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = (role == null || role.isBlank()) ? "ADMIN" : role.trim().toUpperCase();
    }

    /** One CSV line for staff.txt: username,hash,role */
    public String toFileString() {
        return username + "," + passwordHash + "," + role;
    }
}