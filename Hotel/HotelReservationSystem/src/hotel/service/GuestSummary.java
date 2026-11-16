package hotel.service;

/** Totals for the guest report. Built by GuestService.getSummary(). */
public class GuestSummary {
    private final int total;
    private final int upcoming;
    private final int inHouse;
    private final int checkedOut;
    private final int missingContact;

    public GuestSummary(int total, int upcoming, int inHouse, int checkedOut, int missingContact) {
        this.total = total;
        this.upcoming = upcoming;
        this.inHouse = inHouse;
        this.checkedOut = checkedOut;
        this.missingContact = missingContact;
    }

    public int getTotal() { return total; }
    public int getUpcoming() { return upcoming; }
    public int getInHouse() { return inHouse; }
    public int getCheckedOut() { return checkedOut; }
    public int getMissingContact() { return missingContact; }

    @Override
    public String toString() {
        return "Guests: " + total
                + " | Upcoming: " + upcoming
                + " | Staying now: " + inHouse
                + " | Checked out: " + checkedOut
                + " | Missing contact number: " + missingContact;
    }
}
