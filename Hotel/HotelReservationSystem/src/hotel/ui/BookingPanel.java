package hotel.ui;

import hotel.exception.InvalidBookingDataException;
import hotel.exception.InvalidGuestDataException;
import hotel.exception.RoomConflictException;
import hotel.exception.RoomNotFoundException;
import hotel.model.Booking;
import hotel.model.Booking.BookingType;
import hotel.model.Guest;
import hotel.model.Room;
import hotel.service.BookingService;
import hotel.service.GuestService;
import hotel.service.RoomService;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The Bookings screen: reservation form on the left, live payment receipt on the right.
 * Only the content area - the sidebar and top bar belong to MainFrame.
 * All rules live in BookingService; this class only reads the form, shows messages and paints.
 */
public class BookingPanel extends JPanel {

    private static final Color BG = new Color(0xF3F1EE);
    private static final Color MAROON = new Color(0x3F0505);
    private static final Color MAUVE = new Color(0x6B3A4F);
    private static final Color TEXT = new Color(0x1F2937);
    private static final Color MUTED = new Color(0x6B7280);
    private static final Color LINE = new Color(0xDADADA);
    private static final Color RULE = new Color(0xE4E4E4);

    private final BookingService bookingService;
    private final RoomService roomService;
    private final String receivedBy;
    private final Runnable onDataChanged;

    private BookingType mode = BookingType.ADVANCE;
    private Booking shown;          // the booking whose receipt is on screen (null = live draft preview)
    private boolean suppress;       // true while the code (not the user) is changing the form

    // form
    private JButton advanceBtn, walkInBtn;
    private JTextField nameField, contactField, checkInField, checkOutField;
    private JComboBox<RoomItem> roomBox;
    private JComboBox<String> adultsBox;
    private JLabel footerLabel;

    // receipt
    private JPanel receiptCard;
    private JLabel badge, guestLabel, roomLine, dateLine;
    private JLabel calcLeft, calcRight, totalRight, chargeRight, paymentRight, paidLine;
    private JButton printBtn;

    /**
     * @param receivedBy     name printed as "Received by" (e.g. the logged-in staff username)
     * @param onDataChanged  optional; called after a booking is saved so MainFrame can refresh the
     *                       Guests and Rooms tabs. Pass null if not needed.
     */
    public BookingPanel(BookingService bookingService, RoomService roomService,
                        String receivedBy, Runnable onDataChanged) {
        this.bookingService = bookingService;
        this.roomService = roomService;
        this.receivedBy = receivedBy == null ? "" : receivedBy;
        this.onDataChanged = onDataChanged;

        setLayout(new BorderLayout(0, 16));
        setBackground(BG);
        setBorder(new EmptyBorder(24, 24, 24, 24));

        add(buildTopBar(), BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridx = 0;
        c.weightx = 1;
        body.add(buildFormCard(), c);
        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 16, 0, 0);
        body.add(buildReceiptCard(), c);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        refresh();
        suppress = true;
        applyMode(BookingType.ADVANCE);
        suppress = false;
        refreshFooter();
        refreshReceipt();
    }

    /** Reloads the room dropdown. Call this when the Bookings tab is opened (rooms may have changed). */
    public void refresh() {
        boolean old = suppress;
        suppress = true;
        int keep = roomBox.getSelectedItem() == null ? -1 : ((RoomItem) roomBox.getSelectedItem()).room.getRoomNumber();
        roomBox.removeAllItems();
        RoomItem toSelect = null;
        for (Room r : roomService.getAll()) {
            RoomItem item = new RoomItem(r);
            roomBox.addItem(item);
            if (r.getRoomNumber() == keep) toSelect = item;
        }
        if (toSelect != null) roomBox.setSelectedItem(toSelect);
        updateAdultsForRoom();
        suppress = old;
        if (!suppress) {
            refreshFooter();
            refreshReceipt();
        }
    }

    // =====================================================================================
    // building the screen
    // =====================================================================================

    private JComponent buildTopBar() {
        JButton record = button("+ Record payment", MAROON);
        record.setPreferredSize(new Dimension(130, 34));
        record.addActionListener(e -> onRecordPayment());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bar.setOpaque(false);
        bar.add(record);
        return bar;
    }

    private JComponent buildFormCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(new LineBorder(RULE), new EmptyBorder(24, 34, 24, 34)));

        JLabel title = label("Reservation details", Font.BOLD, 15, TEXT);

        advanceBtn = button(BookingType.ADVANCE.toString(), MAUVE);
        walkInBtn = button(BookingType.WALK_IN.toString(), MAUVE);
        advanceBtn.setPreferredSize(new Dimension(120, 30));
        walkInBtn.setPreferredSize(new Dimension(120, 30));
        advanceBtn.addActionListener(e -> applyMode(BookingType.ADVANCE));
        walkInBtn.addActionListener(e -> applyMode(BookingType.WALK_IN));
        JPanel toggles = new JPanel(new FlowLayout(FlowLayout.CENTER, 70, 0));
        toggles.setOpaque(false);
        toggles.add(advanceBtn);
        toggles.add(walkInBtn);

        nameField = field();
        contactField = field();
        checkInField = field();
        checkOutField = field();
        for (JTextField f : new JTextField[]{nameField, contactField, checkInField, checkOutField}) watch(f);
        checkInField.setToolTipText("e.g. 18 Nov 2026 or 2026-11-18");
        checkOutField.setToolTipText("e.g. 20 Nov 2026 or 2026-11-20");

        roomBox = new JComboBox<>();
        roomBox.setPreferredSize(new Dimension(300, 38));
        roomBox.addActionListener(e -> {
            if (suppress) return;
            updateAdultsForRoom();
            onFormEdited();
        });

        adultsBox = new JComboBox<>();
        adultsBox.setPreferredSize(new Dimension(160, 38));
        adultsBox.addActionListener(e -> onFormEdited());

        footerLabel = label("", Font.PLAIN, 11, MUTED);
        footerLabel.setHorizontalAlignment(SwingConstants.CENTER);

        int y = 0;
        put(card, title, 0, y++, 2, new Insets(0, 0, 18, 0));
        put(card, toggles, 0, y++, 2, new Insets(0, 0, 22, 0));
        put(card, small("Guest name"), 0, y, 1, new Insets(0, 0, 4, 12));
        put(card, small("Contact number"), 1, y++, 1, new Insets(0, 12, 4, 0));
        put(card, nameField, 0, y, 1, new Insets(0, 0, 16, 12));
        put(card, contactField, 1, y++, 1, new Insets(0, 12, 16, 0));
        put(card, small("Room selection"), 0, y, 1, new Insets(0, 0, 4, 12));
        put(card, small("Guests"), 1, y++, 1, new Insets(0, 12, 4, 0));
        put(card, roomBox, 0, y, 1, new Insets(0, 0, 16, 12));
        put(card, adultsBox, 1, y++, 1, new Insets(0, 12, 16, 0));
        put(card, small("Check-in date"), 0, y, 1, new Insets(0, 0, 4, 12));
        put(card, small("Check-out date"), 1, y++, 1, new Insets(0, 12, 4, 0));
        put(card, checkInField, 0, y, 1, new Insets(0, 0, 18, 12));
        put(card, checkOutField, 1, y++, 1, new Insets(0, 12, 18, 0));
        put(card, footerLabel, 0, y++, 2, new Insets(8, 0, 0, 0));

        // filler so everything stays at the top when the window is tall
        GridBagConstraints fill = new GridBagConstraints();
        fill.gridx = 0;
        fill.gridy = y;
        fill.weighty = 1;
        card.add(Box.createVerticalGlue(), fill);
        return card;
    }

    private JComponent buildReceiptCard() {
        receiptCard = new JPanel();
        receiptCard.setLayout(new BoxLayout(receiptCard, BoxLayout.Y_AXIS));
        receiptCard.setBackground(Color.WHITE);
        receiptCard.setBorder(BorderFactory.createCompoundBorder(new LineBorder(RULE), new EmptyBorder(14, 16, 16, 16)));
        receiptCard.setPreferredSize(new Dimension(290, 560));

        JLabel logo = new JLabel("<html><span style='font-family:serif;font-size:20px;color:#3F0505'><i>Herz</i></span>"
                + "<br><span style='font-size:8px;color:#3F0505'>HOTEL</span></html>");

        badge = new JLabel("UNPAID", SwingConstants.CENTER);
        badge.setOpaque(true);
        badge.setFont(badge.getFont().deriveFont(Font.PLAIN, 10f));
        badge.setBorder(new EmptyBorder(4, 14, 4, 14));
        JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        badgeWrap.setOpaque(false);
        badgeWrap.add(badge);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(logo, BorderLayout.WEST);
        top.add(badgeWrap, BorderLayout.EAST);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, top.getPreferredSize().height));

        guestLabel = label("Guest name", Font.BOLD, 18, TEXT);
        roomLine = label("-", Font.PLAIN, 12, TEXT);
        dateLine = label("-", Font.PLAIN, 12, MUTED);
        calcLeft = label("-", Font.PLAIN, 12, MUTED);
        calcRight = label("0", Font.PLAIN, 12, TEXT);
        JLabel totalLeft = label("Total stay cost", Font.BOLD, 13, TEXT);
        totalRight = label("0", Font.BOLD, 13, TEXT);
        JLabel chargeLeft = label("Room charge", Font.PLAIN, 12, MUTED);
        chargeRight = label("0", Font.PLAIN, 12, MUTED);
        JLabel paymentLeft = label("Payment received", Font.PLAIN, 12, MUTED);
        paymentRight = label("0", Font.PLAIN, 12, MUTED);
        paidLine = label("-", Font.PLAIN, 11, MUTED);

        printBtn = button("Print receipt", MAROON);
        printBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        printBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        printBtn.addActionListener(e -> onPrintReceipt());

        JLabel thanks = label("Thank you for choosing Herz", Font.PLAIN, 10, MAROON);
        thanks.setHorizontalAlignment(SwingConstants.CENTER);
        thanks.setMaximumSize(new Dimension(Integer.MAX_VALUE, thanks.getPreferredSize().height));

        receiptCard.add(top);
        receiptCard.add(Box.createVerticalStrut(8));
        receiptCard.add(bar(1));
        receiptCard.add(Box.createVerticalStrut(10));
        receiptCard.add(left(label("PAYMENT RECEIPT", Font.PLAIN, 12, MUTED)));
        receiptCard.add(Box.createVerticalStrut(4));
        receiptCard.add(left(guestLabel));
        receiptCard.add(Box.createVerticalStrut(8));
        receiptCard.add(bar(3));
        receiptCard.add(Box.createVerticalStrut(10));
        receiptCard.add(left(label("Stay Summary", Font.BOLD, 14, TEXT)));
        receiptCard.add(Box.createVerticalStrut(8));
        receiptCard.add(left(roomLine));
        receiptCard.add(Box.createVerticalStrut(6));
        receiptCard.add(left(dateLine));
        receiptCard.add(Box.createVerticalStrut(10));
        receiptCard.add(bar(3));
        receiptCard.add(Box.createVerticalStrut(10));
        receiptCard.add(row(calcLeft, calcRight));
        receiptCard.add(Box.createVerticalStrut(6));
        receiptCard.add(row(totalLeft, totalRight));
        receiptCard.add(Box.createVerticalStrut(24));
        receiptCard.add(row(chargeLeft, chargeRight));
        receiptCard.add(Box.createVerticalStrut(2));
        receiptCard.add(row(paymentLeft, paymentRight));
        receiptCard.add(Box.createVerticalStrut(12));
        receiptCard.add(bar(3));
        receiptCard.add(Box.createVerticalStrut(10));
        receiptCard.add(left(paidLine));
        receiptCard.add(Box.createVerticalGlue());
        receiptCard.add(Box.createVerticalStrut(16));
        receiptCard.add(printBtn);
        receiptCard.add(Box.createVerticalStrut(10));
        receiptCard.add(left(thanks));
        return receiptCard;
    }

    // =====================================================================================
    // behaviour
    // =====================================================================================

    /** Switches between Advance and Walk-in. Walk-in locks the check-in date to today. */
    private void applyMode(BookingType t) {
        mode = t;
        advanceBtn.setBackground(t == BookingType.ADVANCE ? MAROON : MAUVE);
        walkInBtn.setBackground(t == BookingType.WALK_IN ? MAROON : MAUVE);

        LocalDate today = LocalDate.now();
        boolean old = suppress;
        suppress = true;                       // set several fields, then react once below
        if (t == BookingType.WALK_IN) {
            checkInField.setText(fmt(today));
            checkInField.setEditable(false);
        } else {
            checkInField.setEditable(true);
            LocalDate cur = tryDate(checkInField);
            if (cur == null || !cur.isAfter(today)) checkInField.setText(fmt(today.plusDays(1)));
        }
        LocalDate in = tryDate(checkInField);
        LocalDate out = tryDate(checkOutField);
        if (in != null && (out == null || !out.isAfter(in))) checkOutField.setText(fmt(in.plusDays(1)));
        suppress = old;

        onFormEdited();
    }

    /** Guests dropdown only offers up to the selected room's capacity. */
    private void updateAdultsForRoom() {
        RoomItem item = (RoomItem) roomBox.getSelectedItem();
        int cap = item == null ? 2 : item.room.getCapacity();
        int keep = adultsBox.getSelectedIndex();
        boolean old = suppress;
        suppress = true;
        adultsBox.removeAllItems();
        for (int i = 1; i <= cap; i++) adultsBox.addItem(i == 1 ? "1 adult" : i + " adults");
        int idx = keep < 0 ? Math.min(1, cap - 1) : Math.min(keep, cap - 1);
        adultsBox.setSelectedIndex(idx);
        suppress = old;
    }

    /** Any edit by the user starts a new draft, so the receipt goes back to a live preview. */
    private void onFormEdited() {
        if (suppress) return;
        shown = null;
        refreshFooter();
        refreshReceipt();
    }

    private void onRecordPayment() {
        try {
            RoomItem item = (RoomItem) roomBox.getSelectedItem();
            if (item == null) throw new InvalidBookingDataException("Please select a room.");
            LocalDate in = GuestService.parseDate(checkInField.getText());
            LocalDate out = GuestService.parseDate(checkOutField.getText());
            int adults = adultsBox.getSelectedIndex() + 1;

            Booking b = bookingService.recordBooking(mode, nameField.getText(), contactField.getText(),
                    item.room.getRoomNumber(), adults, in, out, receivedBy);
            String path = bookingService.exportReceipt(b);

            shown = b;
            clearForm();
            refresh();
            refreshReceipt();
            if (onDataChanged != null) onDataChanged.run();

            JOptionPane.showMessageDialog(this,
                    "Payment of PHP " + Booking.money(b.getAmountPaid()) + " recorded for " + b.getGuestName() + "."
                            + (path != null ? "\nReceipt saved to " + path : ""),
                    "Payment recorded", JOptionPane.INFORMATION_MESSAGE);
        } catch (InvalidBookingDataException | InvalidGuestDataException
                 | RoomConflictException | RoomNotFoundException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot record payment", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Empties the person fields and picks fresh dates, ready for the next booking. */
    private void clearForm() {
        suppress = true;
        nameField.setText("");
        contactField.setText("");
        checkOutField.setText("");
        if (mode == BookingType.ADVANCE) checkInField.setText("");
        suppress = false;
        suppress = true;
        applyMode(mode);
        suppress = false;
        refreshFooter();
    }

    private void onPrintReceipt() {
        if (shown == null) {
            JOptionPane.showMessageDialog(this, "Record the payment first, then print the receipt.",
                    "Nothing to print", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Herz receipt " + shown.getId());
        job.setPrintable((g, pf, page) -> {
            if (page > 0) return Printable.NO_SUCH_PAGE;
            Graphics2D g2 = (Graphics2D) g;
            g2.translate(pf.getImageableX(), pf.getImageableY());
            double scale = Math.min(1.0, pf.getImageableWidth() / receiptCard.getWidth());
            g2.scale(scale, scale);
            printBtn.setVisible(false);            // keep the button off the paper
            try {
                receiptCard.printAll(g2);
            } finally {
                printBtn.setVisible(true);
            }
            return Printable.PAGE_EXISTS;
        });
        if (!job.printDialog()) return;
        try {
            job.print();
        } catch (PrinterException ex) {
            JOptionPane.showMessageDialog(this, "Could not print: " + ex.getMessage(),
                    "Print failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =====================================================================================
    // keeping the screen text up to date
    // =====================================================================================

    private void refreshFooter() {
        LocalDate in = tryDate(checkInField);
        LocalDate out = tryDate(checkOutField);
        int nights = nightsBetween(in, out);
        footerLabel.setText("Check-in from " + BookingService.CHECK_IN_TIME
                + "  ·  Check-out by " + BookingService.CHECK_OUT_TIME
                + (nights > 0 ? "  ·  " + Booking.nightsText(nights) : ""));
    }

    private void refreshReceipt() {
        if (shown != null) {
            showBooking(shown);
            return;
        }
        // live preview of what is typed into the form
        String name = nameField.getText().trim();
        guestLabel.setText(name.isEmpty() ? "Guest name" : name);
        guestLabel.setForeground(name.isEmpty() ? MUTED : TEXT);

        RoomItem item = (RoomItem) roomBox.getSelectedItem();
        LocalDate in = tryDate(checkInField);
        LocalDate out = tryDate(checkOutField);
        int nights = nightsBetween(in, out);
        int rate = item == null ? 0 : item.room.getRate();
        int total = rate * nights;

        roomLine.setText(item == null ? "-" : item.room.getType() + " · Room " + item.room.getRoomNumber());
        dateLine.setText(nights > 0 ? Booking.rangeText(in, out) + "  ·  " + Booking.nightsText(nights)
                : "Enter valid check-in and check-out dates");
        calcLeft.setText(Booking.money(rate) + " × " + Booking.nightsText(nights));
        calcRight.setText(Booking.money(total));
        totalRight.setText(Booking.money(total));
        chargeRight.setText(Booking.money(total));
        paymentRight.setText("0");
        paidLine.setText("Payment not recorded yet");
        setBadge(false);
    }

    private void showBooking(Booking b) {
        guestLabel.setText(b.getGuestName());
        guestLabel.setForeground(TEXT);
        roomLine.setText(b.getRoomType() + " · Room " + b.getRoomNumber());
        dateLine.setText(b.getStayRangeText() + "  ·  " + Booking.nightsText(b.getNights()));
        calcLeft.setText(Booking.money(b.getRate()) + " × " + Booking.nightsText(b.getNights()));
        calcRight.setText(Booking.money(b.getTotal()));
        totalRight.setText(Booking.money(b.getTotal()));
        chargeRight.setText(Booking.money(b.getTotal()));
        paymentRight.setText(Booking.money(b.getAmountPaid()));
        paidLine.setText(b.getPaidOn() == null ? "Payment not recorded yet"
                : "Paid " + b.getPaidOn().format(Guest.DISPLAY_DATE) + "  ·  Received by " + b.getReceivedBy());
        setBadge(b.isPaid());
    }

    private void setBadge(boolean paid) {
        badge.setText(paid ? "PAID" : "UNPAID");
        badge.setBackground(paid ? new Color(0xE3EFE9) : new Color(0xFBEFD9));
        badge.setForeground(paid ? new Color(0x2E6B4F) : new Color(0x8A5A00));
    }

    // =====================================================================================
    // small helpers
    // =====================================================================================

    private static String fmt(LocalDate d) {
        return d.format(Guest.DISPLAY_DATE);
    }

    /** Reads a date box without showing errors (used for the live preview). Null if blank or invalid. */
    private static LocalDate tryDate(JTextField f) {
        try {
            return GuestService.parseDate(f.getText());
        } catch (InvalidGuestDataException e) {
            return null;
        }
    }

    private static int nightsBetween(LocalDate in, LocalDate out) {
        return (in != null && out != null && out.isAfter(in)) ? (int) ChronoUnit.DAYS.between(in, out) : 0;
    }

    private void watch(JTextField f) {
        f.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onFormEdited(); }
            @Override public void removeUpdate(DocumentEvent e) { onFormEdited(); }
            @Override public void changedUpdate(DocumentEvent e) { onFormEdited(); }
        });
    }

    private static JLabel label(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(style, size));
        l.setForeground(color);
        return l;
    }

    private static JLabel small(String text) {
        return label(text, Font.PLAIN, 11, MUTED);
    }

    private static JTextField field() {
        JTextField f = new JTextField();
        Border pad = new EmptyBorder(6, 10, 6, 10);
        f.setBorder(BorderFactory.createCompoundBorder(new LineBorder(LINE), pad));
        f.setPreferredSize(new Dimension(200, 38));
        return f;
    }

    private static JButton button(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 12f));
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static void put(JPanel p, Component c, int x, int y, int w, Insets insets) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = x;
        g.gridy = y;
        g.gridwidth = w;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.NORTHWEST;
        g.insets = insets;
        p.add(c, g);
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JPanel row(JLabel l, JLabel r) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(l, BorderLayout.WEST);
        p.add(r, BorderLayout.EAST);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    /** A thin grey divider line of the given height. */
    private static JComponent bar(int height) {
        JPanel p = new JPanel();
        p.setBackground(RULE);
        p.setPreferredSize(new Dimension(10, height));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /** One row of the room dropdown: "402 · Suite · PhP 5,000 / night". */
    private static class RoomItem {
        final Room room;

        RoomItem(Room room) {
            this.room = room;
        }

        @Override
        public String toString() {
            return room.getRoomNumber() + " · " + room.getType() + " · PhP "
                    + Booking.money(room.getRate()) + " / night";
        }
    }
}
