package hotel.service;

import hotel.model.Guest;

import java.time.LocalDate;

/** The choices in the "All guests" dropdown. toString() is the label shown in the combo box. */
public enum GuestFilter {
    ALL("All guests", null),
    UPCOMING("Upcoming arrivals", Guest.StayStatus.UPCOMING),
    IN_HOUSE("Currently staying", Guest.StayStatus.IN_HOUSE),
    CHECKED_OUT("Checked out", Guest.StayStatus.CHECKED_OUT);

    private final String label;
    private final Guest.StayStatus status; // null = no restriction

    GuestFilter(String label, Guest.StayStatus status) {
        this.label = label;
        this.status = status;
    }

    public boolean accepts(Guest g, LocalDate today) {
        return status == null || g.getStatus(today) == status;
    }

    @Override
    public String toString() {
        return label;
    }
}
