package hotel.ui;

import hotel.exception.GuestNotFoundException;
import hotel.exception.InvalidGuestDataException;
import hotel.model.Guest;
import hotel.service.GuestFilter;
import hotel.service.GuestService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The "Guest Management" page: Guest Directory card with search, filter, a paged table and a pager.
 * Click a guest row to open the Edit guest dialog; from there the guest can be saved or deleted.
 * Deleting a guest frees their room, and rooms whose check-out date has passed are freed
 * automatically (both rules live in GuestService; this class only calls it).
 */
public class GuestPanel extends JPanel {

    // ---- palette (shared with the login screen) ----
    private static final Color PAPER = LoginPanel.PAPER;
    private static final Color NAVY = LoginPanel.NAVY;
    private static final Color GOLD_DARK = LoginPanel.GOLD_DARK;
    private static final Color MAROON = LoginPanel.MAROON;
    private static final Color MAROON_HOVER = LoginPanel.MAROON_HOVER;
    private static final Color BORDER = LoginPanel.BORDER;
    private static final Color MUTED = LoginPanel.MUTED;
    private static final Color ERROR = LoginPanel.ERROR;
    private static final Color SOFT_GREY = new Color(0xF3F4F2);
    private static final Color SELECTED_ROW = new Color(0xF2E4E4);
    private static final Color DANGER_TEXT = new Color(0xA8453A);
    private static final Color DANGER_LINE = new Color(0xE9D3CF);

    private static final String[] COLUMNS = {"Room No.", "Guest name", "Contact number", "Address",
            "Check In Date", "Check Out Date"};

    private final GuestService guests;

    private final HintField searchField = new HintField("Search by name, email or phone...");
    private final JComboBox<GuestFilter> filterBox = new JComboBox<>();
    private final JLabel countLabel = new JLabel();
    private final JLabel showingLabel = new JLabel();
    private final JPanel pagerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    private final JTable table;

    private int page = 1;
    private List<Guest> results = new ArrayList<>();
    private List<Guest> pageRows = new ArrayList<>();

    /** Re-checks check-out dates every minute while the page is showing. */
    private final Timer autoRelease = new Timer(60_000, e -> refresh());

    public GuestPanel(GuestService guests) {
        this.guests = guests;

        setBackground(PAPER);
        setLayout(new BorderLayout(0, 20));
        setBorder(new EmptyBorder(24, 32, 24, 32));

        table = buildTable();
        add(buildHeader(), BorderLayout.NORTH);
        add(buildCard(), BorderLayout.CENTER);

        refresh();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        autoRelease.start();
    }

    @Override
    public void removeNotify() {
        autoRelease.stop();
        super.removeNotify();
    }

    // =====================================================================
    //  Layout
    // =====================================================================

    private JPanel buildHeader() {
        JLabel eyebrow = new JLabel("HOTEL OPERATIONS");
        eyebrow.setFont(sans(Font.PLAIN, 10));
        eyebrow.setForeground(GOLD_DARK);

        JLabel title = new JLabel("Guest Management");
        title.setFont(LoginPanel.font(Font.SERIF, Font.PLAIN, 32));
        title.setForeground(NAVY);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(eyebrow);
        left.add(Box.createVerticalStrut(6));
        left.add(title);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(left, BorderLayout.WEST);
        return header;
    }

    private JPanel buildCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(18, 20, 18, 20)));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(buildTitleRow());
        top.add(Box.createVerticalStrut(14));
        top.add(buildFilterRow());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);

        card.add(top, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        card.add(buildFooter(), BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildTitleRow() {
        JLabel title = new JLabel("Guest Directory");
        title.setFont(sans(Font.BOLD, 16));
        title.setForeground(NAVY);

        countLabel.setFont(sans(Font.PLAIN, 11));
        countLabel.setForeground(MUTED);

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(title, BorderLayout.WEST);
        row.add(countLabel, BorderLayout.EAST);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private JPanel buildFilterRow() {
        GuestFilter[] all = GuestFilter.class.getEnumConstants();
        if (all == null) all = new GuestFilter[]{GuestFilter.ALL};
        for (GuestFilter f : all) filterBox.addItem(f);
        filterBox.setFont(sans(Font.PLAIN, 12));
        filterBox.setBackground(Color.WHITE);
        filterBox.setPreferredSize(new Dimension(170, 38));
        filterBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, prettyFilter(value), index, selected, focus);
            }
        });
        filterBox.addActionListener(e -> {
            page = 1;
            refresh();
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { onSearchChanged(); }
            public void removeUpdate(DocumentEvent e) { onSearchChanged(); }
            public void changedUpdate(DocumentEvent e) { onSearchChanged(); }
        });

        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 1;
        row.add(searchField, c);
        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 16, 0, 0);
        row.add(filterBox, c);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private JPanel buildFooter() {
        showingLabel.setFont(sans(Font.PLAIN, 11));
        showingLabel.setForeground(MUTED);
        pagerPanel.setOpaque(false);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(8, 0, 0, 0));
        footer.add(showingLabel, BorderLayout.WEST);
        footer.add(pagerPanel, BorderLayout.CENTER);
        footer.add(Box.createHorizontalStrut(120), BorderLayout.EAST);
        return footer;
    }

    // =====================================================================
    //  Table
    // =====================================================================

    private JTable buildTable() {
        AbstractTableModel model = new AbstractTableModel() {
            public int getRowCount() { return pageRows.size(); }
            public int getColumnCount() { return COLUMNS.length; }
            public String getColumnName(int col) { return COLUMNS[col]; }
            public boolean isCellEditable(int row, int col) { return false; }

            public Object getValueAt(int row, int col) {
                Guest g = pageRows.get(row);
                switch (col) {
                    case 0: return g.getRoomNumber();
                    case 1: return g.getName();
                    case 2: return g.getContact();
                    case 3: return g.getAddress();
                    case 4: return g.getCheckInText();
                    default: return g.getCheckOutText();
                }
            }
        };

        JTable t = new JTable(model);
        t.setRowHeight(44);
        t.setFont(sans(Font.PLAIN, 12));
        t.setForeground(NAVY);
        t.setBackground(Color.WHITE);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(new Color(0xEDEBE5));
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setFillsViewportHeight(true);
        t.setSelectionBackground(SELECTED_ROW);
        t.setSelectionForeground(NAVY);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.getTableHeader().setReorderingAllowed(false);

        DefaultTableCellRenderer header = new DefaultTableCellRenderer();
        header.setHorizontalAlignment(SwingConstants.CENTER);
        header.setBackground(SOFT_GREY);
        header.setForeground(MUTED);
        header.setFont(sans(Font.PLAIN, 11));
        header.setBorder(new EmptyBorder(10, 0, 10, 0));
        t.getTableHeader().setDefaultRenderer(header);

        DefaultTableCellRenderer centered = new DefaultTableCellRenderer();
        centered.setHorizontalAlignment(SwingConstants.CENTER);
        t.setDefaultRenderer(Object.class, centered);

        DefaultTableCellRenderer roomNumber = new DefaultTableCellRenderer();
        roomNumber.setHorizontalAlignment(SwingConstants.CENTER);
        roomNumber.setForeground(GOLD_DARK);
        t.getColumnModel().getColumn(0).setCellRenderer(roomNumber);

        // click a row = open the Edit guest dialog
        t.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        t.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int row = t.rowAtPoint(e.getPoint());
                if (row < 0 || row >= pageRows.size()) return;
                t.setRowSelectionInterval(row, row);
                showEditDialog(pageRows.get(row));
            }
        });
        return t;
    }

    // =====================================================================
    //  Refreshing the screen
    // =====================================================================

    private void onSearchChanged() {
        page = 1;
        refresh();
    }

    /**
     * Frees rooms of guests who have checked out, then re-runs the search and redraws.
     * MainFrame calls this when the Guests page is opened.
     */
    public void refresh() {
        guests.releaseCheckedOutRooms();

        results = guests.search(searchField.getText(), selectedFilter());
        page = Math.min(Math.max(1, page), GuestService.pageCount(results.size()));
        pageRows = GuestService.getPage(results, page);

        ((AbstractTableModel) table.getModel()).fireTableDataChanged();
        countLabel.setText(guests.getTotalCount() + " guest profiles");
        showingLabel.setText(GuestService.showingText(results.size(), page));
        buildPager();
    }

    private void buildPager() {
        pagerPanel.removeAll();
        int total = GuestService.pageCount(results.size());

        pagerPanel.add(pagerButton("‹", false, page > 1, () -> goToPage(page - 1)));
        int start = Math.max(1, page - 2);
        int end = Math.min(total, start + 4);
        start = Math.max(1, end - 4);
        for (int p = start; p <= end; p++) {
            final int target = p;
            pagerPanel.add(pagerButton(String.valueOf(p), p == page, true, () -> goToPage(target)));
        }
        pagerPanel.add(pagerButton("›", false, page < total, () -> goToPage(page + 1)));
        pagerPanel.revalidate();
        pagerPanel.repaint();
    }

    private void goToPage(int newPage) {
        page = newPage;
        refresh();
    }

    // =====================================================================
    //  Edit guest dialog
    // =====================================================================

    private void showEditDialog(Guest g) {
        String fullName = g.getName().trim();
        int cut = fullName.lastIndexOf(' ');
        String first = cut < 0 ? fullName : fullName.substring(0, cut).trim();
        String last = cut < 0 ? "" : fullName.substring(cut + 1).trim();

        JTextField firstField = input(first);
        JTextField lastField = input(last);
        JTextField emailField = input(g.getEmail());
        JTextField contactField = input(g.getContact());
        JTextField addressField = input(g.getAddress());

        JLabel error = text(" ", Font.PLAIN, 12, ERROR);

        JPanel p = dialogPanel(400);
        p.add(left(text("Edit guest", Font.BOLD, 18, NAVY)));
        p.add(Box.createVerticalStrut(4));
        p.add(left(text(String.format("G-%04d  ·  %s", g.getId(), stayText(g)), Font.PLAIN, 12, MUTED)));
        p.add(Box.createVerticalStrut(14));
        p.add(divider());
        p.add(Box.createVerticalStrut(16));

        JPanel names = new JPanel(new GridLayout(1, 2, 14, 0));
        names.setOpaque(false);
        names.add(labeled("First name", firstField));
        names.add(labeled("Last name", lastField));
        p.add(fullWidth(names));
        p.add(Box.createVerticalStrut(14));
        p.add(fullWidth(labeled("Email address", emailField)));
        p.add(Box.createVerticalStrut(14));
        p.add(fullWidth(labeled("Contact number", contactField)));
        p.add(Box.createVerticalStrut(14));
        p.add(fullWidth(labeled("Address", addressField)));
        p.add(Box.createVerticalStrut(16));

        JLabel note = text("Guest details are used only for hotel operations.", Font.PLAIN, 11, MUTED);
        JPanel noteBox = new JPanel(new BorderLayout());
        noteBox.setBackground(SOFT_GREY);
        noteBox.setBorder(new EmptyBorder(12, 12, 12, 12));
        noteBox.add(note, BorderLayout.CENTER);
        p.add(fullWidth(noteBox));
        p.add(Box.createVerticalStrut(8));
        p.add(left(error));
        p.add(Box.createVerticalStrut(8));

        JDialog d = newDialog(p);

        Btn delete = new Btn("Delete guest", Color.WHITE, DANGER_LINE, DANGER_TEXT, new Color(0xFBF1EF));
        delete.addActionListener(e -> {
            d.dispose();
            showDeleteDialog(g);
        });
        p.add(fullWidth(delete));
        p.add(Box.createVerticalStrut(10));

        Btn cancel = new Btn("Cancel", Color.WHITE, BORDER, NAVY, SOFT_GREY);
        Btn save = new Btn("Save changes", MAROON, null, Color.WHITE, MAROON_HOVER);
        cancel.addActionListener(e -> d.dispose());
        save.addActionListener(e -> {
            try {
                String name = GuestService.joinName(firstField.getText(), lastField.getText());
                guests.updateGuestDetails(g.getId(), name, contactField.getText(),
                        emailField.getText(), addressField.getText());
                d.dispose();
                refresh();
            } catch (InvalidGuestDataException ex) {
                error.setText(ex.getMessage());
                d.pack();
            } catch (GuestNotFoundException ex) {
                d.dispose();
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot save guest", JOptionPane.ERROR_MESSAGE);
                refresh();
            }
        });
        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(save);
        p.add(fullWidth(buttons));

        d.getRootPane().setDefaultButton(save);
        show(d);
    }

    // =====================================================================
    //  Delete guest dialog
    // =====================================================================

    private void showDeleteDialog(Guest g) {
        JPanel p = dialogPanel(460);

        JLabel badge = new JLabel("CONFIRM DELETION");
        badge.setOpaque(true);
        badge.setBackground(new Color(0xF8E9E6));
        badge.setForeground(DANGER_TEXT);
        badge.setFont(sans(Font.BOLD, 11));
        badge.setBorder(new EmptyBorder(8, 14, 8, 14));
        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badgeRow.setOpaque(false);
        badgeRow.add(badge);

        p.add(fullWidth(badgeRow));
        p.add(Box.createVerticalStrut(18));
        p.add(left(wrapped("Delete " + g.getName() + "?", Font.BOLD, 22, NAVY, 400)));
        p.add(Box.createVerticalStrut(14));
        p.add(left(wrapped("This guest profile will be permanently removed.", Font.PLAIN, 14, MUTED, 400)));
        p.add(Box.createVerticalStrut(6));
        p.add(left(wrapped(guests.describeDeletionImpact(g), Font.PLAIN, 13, MUTED, 400)));
        p.add(Box.createVerticalStrut(16));
        p.add(left(wrapped("This action cannot be undone.", Font.PLAIN, 13, DANGER_TEXT, 400)));
        p.add(Box.createVerticalStrut(22));

        JDialog d = newDialog(p);

        Btn cancel = new Btn("Cancel", Color.WHITE, BORDER, NAVY, SOFT_GREY);
        Btn confirm = new Btn("Delete guest", MAROON, null, Color.WHITE, MAROON_HOVER);
        cancel.setPreferredSize(new Dimension(130, 44));
        confirm.setPreferredSize(new Dimension(150, 44));
        cancel.addActionListener(e -> d.dispose());
        confirm.addActionListener(e -> {
            d.dispose();
            try {
                guests.deleteGuest(g.getId());   // also frees the guest's room
            } catch (GuestNotFoundException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot delete guest", JOptionPane.ERROR_MESSAGE);
            }
            refresh();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(confirm);
        p.add(fullWidth(buttons));

        show(d);
    }

    // =====================================================================
    //  Dialog helpers
    // =====================================================================

    private JPanel dialogPanel(int width) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(24, 28, 24, 28)));
        Component spacer = Box.createRigidArea(new Dimension(width, 0));
        ((JComponent) spacer).setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(spacer);
        return p;
    }

    private JDialog newDialog(JPanel content) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog d = new JDialog(owner, Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        d.setContentPane(content);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        return d;
    }

    private void show(JDialog d) {
        d.pack();
        d.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        d.setVisible(true);
    }

    private static JPanel labeled(String caption, JTextField field) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel l = text(caption, Font.PLAIN, 12, MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(l);
        p.add(Box.createVerticalStrut(6));
        p.add(field);
        return p;
    }

    private static JTextField input(String value) {
        JTextField f = new JTextField(value == null ? "" : value);
        f.setFont(sans(Font.PLAIN, 13));
        f.setForeground(NAVY);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(10, 12, 10, 12)));
        f.setPreferredSize(new Dimension(120, 42));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        return f;
    }

    private static JComponent divider() {
        JPanel line = new JPanel();
        line.setBackground(BORDER);
        line.setPreferredSize(new Dimension(10, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    private static JComponent fullWidth(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JLabel text(String s, int style, int size, Color color) {
        JLabel l = new JLabel(s);
        l.setFont(sans(style, size));
        l.setForeground(color);
        return l;
    }

    /** A label whose text wraps at the given pixel width. */
    private static JLabel wrapped(String s, int style, int size, Color color, int width) {
        String safe = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        JLabel l = new JLabel("<html><body style='width:" + width + "px'>" + safe + "</body></html>");
        l.setFont(sans(style, size));
        l.setForeground(color);
        return l;
    }

    /** Subtitle under "Edit guest": "No active reservations", "Room 201 · In house", ... */
    private static String stayText(Guest g) {
        switch (g.getStatus(LocalDate.now())) {
            case IN_HOUSE: return "Room " + g.getRoomNumber() + " · In house";
            case UPCOMING: return "Room " + g.getRoomNumber() + " · Upcoming stay";
            default:       return "No active reservations";
        }
    }

    // =====================================================================
    //  Small helpers
    // =====================================================================

    private GuestFilter selectedFilter() {
        Object item = filterBox.getSelectedItem();
        return (item instanceof GuestFilter) ? (GuestFilter) item : GuestFilter.ALL;
    }

    /** ALL -> "All guests", IN_HOUSE -> "In house", anything already nicely named stays as is. */
    private static String prettyFilter(Object value) {
        String s = String.valueOf(value);
        if (s.equals("ALL")) return "All guests";
        if (!s.equals(s.toUpperCase())) return s;
        String spaced = s.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static Font sans(int style, int size) {
        return LoginPanel.font(Font.SANS_SERIF, style, size);
    }

    private JButton pagerButton(String text, boolean current, boolean enabled, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(sans(current ? Font.BOLD : Font.PLAIN, 12));
        b.setForeground(enabled ? NAVY : BORDER);
        b.setBackground(Color.WHITE);
        b.setOpaque(true);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setEnabled(enabled);
        b.setPreferredSize(new Dimension(36, 36));
        b.setBorder(BorderFactory.createLineBorder(current ? NAVY : BORDER));
        if (enabled) {
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> action.run());
        }
        return b;
    }

    // =====================================================================
    //  Inner classes
    // =====================================================================

    /** Rounded button: filled, or outlined when a line colour is given. */
    private static class Btn extends JButton {
        private final Color fill, line, hoverFill;
        private boolean over;

        Btn(String label, Color fill, Color line, Color textColor, Color hoverFill) {
            super(label);
            this.fill = fill;
            this.line = line;
            this.hoverFill = hoverFill;
            setFont(sans(Font.PLAIN, 13));
            setForeground(textColor);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(120, 42));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { over = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(over ? hoverFill : fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            if (line != null) {
                g2.setColor(line);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A text field that shows grey hint text while it is empty. */
    private static class HintField extends JTextField {
        private final String hint;

        HintField(String hint) {
            this.hint = hint;
            setFont(sans(Font.PLAIN, 12));
            setBackground(SOFT_GREY);
            setBorder(new EmptyBorder(10, 14, 10, 14));
            setPreferredSize(new Dimension(300, 38));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!getText().isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(new Color(0x9CA3AF));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(hint, getInsets().left, y);
            g2.dispose();
        }
    }
}
