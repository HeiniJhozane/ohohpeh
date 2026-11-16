package hotel.ui;

import hotel.exception.DuplicateRoomException;
import hotel.exception.InvalidRoomDataException;
import hotel.exception.RoomNotFoundException;
import hotel.model.Room;
import hotel.service.RoomService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * The "Room management" page: title, then a Room Inventory card with
 * search, type/status filters, status tabs, a paged table and a pager.
 * Holds no business logic; it only calls RoomService.
 * Tip: double-click a row to edit it, right-click a row for Edit / Delete.
 */
public class RoomPanel extends JPanel {

    // ---- palette (shared with the login screen) ----
    private static final Color PAPER = LoginPanel.PAPER;
    private static final Color NAVY = LoginPanel.NAVY;
    private static final Color GOLD_DARK = LoginPanel.GOLD_DARK;
    private static final Color MAROON = LoginPanel.MAROON;
    private static final Color MAROON_HOVER = LoginPanel.MAROON_HOVER;
    private static final Color BORDER = LoginPanel.BORDER;
    private static final Color MUTED = LoginPanel.MUTED;
    private static final Color SOFT_GREY = new Color(0xF3F4F2);     // search box + table header
    private static final Color SELECTED_ROW = new Color(0xF2E4E4);  // highlighted table row

    private static final String[] COLUMNS = {"Room", "Type", "Floor", "Rate / night", "Status", "Capacity"};
    private static final String[] TAB_NAMES = {"All rooms", "Available", "Occupied", "Reserved"};

    private final RoomService rooms;

    // ---- controls ----
    private final HintField searchField = new HintField("Search room number or type...");
    private final JComboBox<Object> typeBox = new JComboBox<>();
    private final JComboBox<Object> statusBox = new JComboBox<>();
    private final JButton[] tabs = new JButton[TAB_NAMES.length];
    private final JLabel countLabel = new JLabel();
    private final JLabel showingLabel = new JLabel();
    private final JPanel pagerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    private final JTable table;

    // ---- current state ----
    private int page = 1;
    private List<Room> results = new java.util.ArrayList<>();   // everything that matches the filters
    private List<Room> pageRows = new java.util.ArrayList<>();  // only the rows on this page

    public RoomPanel(RoomService rooms) {
        this.rooms = rooms;

        setBackground(PAPER);
        setLayout(new BorderLayout(0, 20));
        setBorder(new EmptyBorder(24, 32, 24, 32));

        table = buildTable();
        add(buildHeader(), BorderLayout.NORTH);
        add(buildCard(), BorderLayout.CENTER);

        refresh();
    }

    // =====================================================================
    //  Layout
    // =====================================================================

    /** "HOTEL OPERATIONS / Room management" title. */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel eyebrow = new JLabel("HOTEL OPERATIONS");
        eyebrow.setFont(sans(Font.PLAIN, 10));
        eyebrow.setForeground(GOLD_DARK);

        JLabel title = new JLabel("Room management");
        title.setFont(LoginPanel.font(Font.SERIF, Font.PLAIN, 32));
        title.setForeground(NAVY);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(eyebrow);
        left.add(Box.createVerticalStrut(6));
        left.add(title);

        header.add(left, BorderLayout.WEST);
        return header;
    }

    /** The white "Room Inventory" card that holds filters, table and pager. */
    private JPanel buildCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(18, 20, 18, 20)));

        // top part: title row, filter row, tabs
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(buildTitleRow());
        top.add(Box.createVerticalStrut(14));
        top.add(buildFilterRow());
        top.add(Box.createVerticalStrut(14));
        top.add(buildTabs());

        // middle: the table
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);

        card.add(top, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        card.add(buildFooter(), BorderLayout.SOUTH);
        return card;
    }

    /** "Room Inventory" on the left, "50 rooms" on the right. */
    private JPanel buildTitleRow() {
        JLabel title = new JLabel("Room Inventory");
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

    /** Search box + "All types" + "All statuses". Any change goes back to page 1 and refreshes. */
    private JPanel buildFilterRow() {
        typeBox.addItem("All types");
        for (Room.RoomType t : Room.RoomType.values()) typeBox.addItem(t);

        statusBox.addItem("All statuses");
        for (Room.RoomStatus s : Room.RoomStatus.values()) statusBox.addItem(s);

        for (JComboBox<Object> box : java.util.List.of(typeBox, statusBox)) {
            box.setFont(sans(Font.PLAIN, 12));
            box.setBackground(Color.WHITE);
            box.setPreferredSize(new Dimension(150, 38));
            box.addActionListener(e -> {
                page = 1;
                refresh();
            });
        }

        // typing in the search box filters live
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
        row.add(typeBox, c);
        c.gridx = 2;
        row.add(statusBox, c);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    /** The four tabs. Clicking one just picks the matching item in the status dropdown. */
    private JPanel buildTabs() {
        JPanel row = new JPanel(new GridLayout(1, TAB_NAMES.length));
        row.setOpaque(false);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int index = i;
            JButton tab = new JButton();
            tab.setContentAreaFilled(false);
            tab.setFocusPainted(false);
            tab.setOpaque(false);
            tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            tab.addActionListener(e -> statusBox.setSelectedIndex(index)); // listener refreshes for us
            tabs[i] = tab;
            row.add(tab);
        }
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    /** "Showing 1–8 of 50 rooms" on the left, page buttons in the middle. */
    private JPanel buildFooter() {
        showingLabel.setFont(sans(Font.PLAIN, 11));
        showingLabel.setForeground(MUTED);
        pagerPanel.setOpaque(false);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(8, 0, 0, 0));
        footer.add(showingLabel, BorderLayout.WEST);
        footer.add(pagerPanel, BorderLayout.CENTER);
        // invisible spacer on the right so the pager stays visually centred
        footer.add(Box.createHorizontalStrut(120), BorderLayout.EAST);
        return footer;
    }

    // =====================================================================
    //  Table
    // =====================================================================

    private JTable buildTable() {
        // The model just reads from pageRows, so it only ever shows the current page.
        AbstractTableModel model = new AbstractTableModel() {
            public int getRowCount() { return pageRows.size(); }
            public int getColumnCount() { return COLUMNS.length; }
            public String getColumnName(int col) { return COLUMNS[col]; }
            public boolean isCellEditable(int row, int col) { return false; }

            public Object getValueAt(int row, int col) {
                Room r = pageRows.get(row);
                switch (col) {
                    case 0: return r.getRoomNumber();
                    case 1: return r.getType().toString();
                    case 2: return r.getFloor();
                    case 3: return String.format("%,d", r.getRate());
                    case 4: return r.getStatus();
                    default: return r.getCapacity() + (r.getCapacity() == 1 ? " guest" : " guests");
                }
            }
        };

        JTable t = new JTable(model);
        t.setRowHeight(40);
        t.setFont(sans(Font.PLAIN, 12));
        t.setForeground(MUTED);
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

        // header: light grey background, muted centred text
        DefaultTableCellRenderer header = new DefaultTableCellRenderer();
        header.setHorizontalAlignment(SwingConstants.CENTER);
        header.setBackground(SOFT_GREY);
        header.setForeground(MUTED);
        header.setFont(sans(Font.PLAIN, 11));
        header.setBorder(new EmptyBorder(10, 0, 10, 0));
        t.getTableHeader().setDefaultRenderer(header);

        // body cells: centred text; room number is bold + dark; status is a coloured badge
        DefaultTableCellRenderer centered = new DefaultTableCellRenderer();
        centered.setHorizontalAlignment(SwingConstants.CENTER);
        t.setDefaultRenderer(Object.class, centered);

        DefaultTableCellRenderer roomNumber = new DefaultTableCellRenderer();
        roomNumber.setHorizontalAlignment(SwingConstants.CENTER);
        roomNumber.setFont(sans(Font.BOLD, 12));
        roomNumber.setForeground(NAVY);
        t.getColumnModel().getColumn(0).setCellRenderer(roomNumber);
        t.getColumnModel().getColumn(4).setCellRenderer(new StatusCell());

        // double-click = edit, right-click = Edit / Delete menu
        JPopupMenu menu = new JPopupMenu();
        JMenuItem editItem = new JMenuItem("Edit room");
        JMenuItem deleteItem = new JMenuItem("Delete room");
        editItem.addActionListener(e -> editSelected());
        deleteItem.addActionListener(e -> deleteSelected());
        menu.add(editItem);
        menu.add(deleteItem);

        t.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) editSelected();
            }
            @Override public void mousePressed(MouseEvent e) { showMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { showMenu(e); }

            private void showMenu(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int row = t.rowAtPoint(e.getPoint());
                if (row < 0) return;
                t.setRowSelectionInterval(row, row); // select the row that was right-clicked
                menu.show(t, e.getX(), e.getY());
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

    /** Re-runs the search and redraws the table, tabs, labels and pager. */
    private void refresh() {
        results = rooms.search(searchField.getText(), selectedType(), selectedStatus());
        page = Math.min(Math.max(1, page), RoomService.pageCount(results.size()));
        pageRows = RoomService.getPage(results, page);

        ((AbstractTableModel) table.getModel()).fireTableDataChanged();
        countLabel.setText(rooms.getTotalCount() + " rooms");
        showingLabel.setText(RoomService.showingText(results.size(), page));
        updateTabs();
        buildPager();
    }

    /** Tab text shows the totals, e.g. "Available  15". The active tab is bold with a gold underline. */
    private void updateTabs() {
        int active = statusBox.getSelectedIndex();
        for (int i = 0; i < tabs.length; i++) {
            Room.RoomStatus status = (i == 0) ? null : Room.RoomStatus.values()[i - 1];
            boolean selected = (i == active);

            tabs[i].setText(TAB_NAMES[i] + "  " + rooms.countByStatus(status));
            tabs[i].setFont(sans(selected ? Font.BOLD : Font.PLAIN, 12));
            tabs[i].setForeground(selected ? NAVY : MUTED);
            tabs[i].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 2, 0, selected ? GOLD_DARK : Color.WHITE),
                    new EmptyBorder(8, 0, 8, 0)));
        }
    }

    /** Draws  ‹ 1 2 3 ›  showing at most 5 page numbers around the current page. */
    private void buildPager() {
        pagerPanel.removeAll();
        int total = RoomService.pageCount(results.size());

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
    //  Add / edit / delete
    // =====================================================================

    private Room selectedRoom() {
        int row = table.getSelectedRow();
        return (row < 0 || row >= pageRows.size()) ? null : pageRows.get(row);
    }

    private void editSelected() {
        Room r = selectedRoom();
        if (r != null) showRoomForm(r);
    }

    private void deleteSelected() {
        Room r = selectedRoom();
        if (r == null) return;
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete room " + r.getRoomNumber() + "? This cannot be undone.",
                "Delete room", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            rooms.deleteRoom(r.getRoomNumber());
            refresh();
        } catch (RoomNotFoundException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot delete room", JOptionPane.ERROR_MESSAGE);
            refresh();
        }
    }

    /** Shows the Add / Edit form. Pass null to add a new room, or a room to edit it. */
    private void showRoomForm(Room existing) {
        boolean editing = existing != null;

        JTextField numberField = new JTextField(editing ? String.valueOf(existing.getRoomNumber()) : "", 12);
        JComboBox<Room.RoomType> typeField = new JComboBox<>(Room.RoomType.values());
        JTextField floorField = new JTextField(editing ? String.valueOf(existing.getFloor()) : "", 12);
        JTextField rateField = new JTextField(editing ? String.valueOf(existing.getRate()) : "", 12);
        JTextField capacityField = new JTextField(editing ? String.valueOf(existing.getCapacity()) : "", 12);
        JComboBox<Room.RoomStatus> statusField = new JComboBox<>(Room.RoomStatus.values());
        if (editing) {
            typeField.setSelectedItem(existing.getType());
            statusField.setSelectedItem(existing.getStatus());
        }

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        form.add(new JLabel("Room number"));
        form.add(numberField);
        form.add(new JLabel("Type"));
        form.add(typeField);
        form.add(new JLabel("Floor"));
        form.add(floorField);
        form.add(new JLabel("Rate per night"));
        form.add(rateField);
        form.add(new JLabel("Capacity (guests)"));
        form.add(capacityField);
        form.add(new JLabel("Status"));
        form.add(statusField);

        // Keep re-showing the form until the room saves or the user cancels,
        // so a validation error never loses what was typed.
        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, form, editing ? "Edit room" : "Add room",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) return;

            try {
                int number = RoomService.parseWholeNumber(numberField.getText(), "Room number");
                int floor = RoomService.parseWholeNumber(floorField.getText(), "Floor");
                int rate = RoomService.parseWholeNumber(rateField.getText(), "Rate per night");
                int capacity = RoomService.parseWholeNumber(capacityField.getText(), "Capacity");
                Room.RoomType type = (Room.RoomType) typeField.getSelectedItem();
                Room.RoomStatus status = (Room.RoomStatus) statusField.getSelectedItem();

                if (editing) {
                    rooms.updateRoom(existing.getRoomNumber(), number, type, floor, rate, capacity, status);
                } else {
                    rooms.addRoom(number, type, floor, rate, capacity, status);
                }
                refresh();
                return;
            } catch (InvalidRoomDataException | DuplicateRoomException | RoomNotFoundException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot save room", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // =====================================================================
    //  Small helpers
    // =====================================================================

    /** null = "All types". */
    private Room.RoomType selectedType() {
        Object item = typeBox.getSelectedItem();
        return (item instanceof Room.RoomType) ? (Room.RoomType) item : null;
    }

    /** null = "All statuses". */
    private Room.RoomStatus selectedStatus() {
        Object item = statusBox.getSelectedItem();
        return (item instanceof Room.RoomStatus) ? (Room.RoomStatus) item : null;
    }

    private static Font sans(int style, int size) {
        return LoginPanel.font(Font.SANS_SERIF, style, size);
    }

    /** The maroon "+ Add room" style button, with a hover colour. */
    private JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.setFont(sans(Font.BOLD, 12));
        b.setForeground(Color.WHITE);
        b.setBackground(MAROON);
        b.setOpaque(true);
        b.setContentAreaFilled(false);
        b.setBorder(new EmptyBorder(12, 22, 12, 22));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(MAROON_HOVER); }
            @Override public void mouseExited(MouseEvent e) { b.setBackground(MAROON); }
        });
        return b;
    }

    /** One square pager button (‹, a page number, or ›). The current page has a navy outline. */
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

    /** Draws the status as a small rounded badge: green = Available, blue-grey = Occupied, amber = Reserved. */
    private static class StatusCell extends JPanel implements TableCellRenderer {
        private Room.RoomStatus status;
        private Color rowColor = Color.WHITE;

        StatusCell() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            status = (Room.RoomStatus) value;
            rowColor = isSelected ? table.getSelectionBackground() : table.getBackground();
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // row background
            g2.setColor(rowColor);
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (status == null) { g2.dispose(); return; }

            // badge colours for each status
            Color bg, fg;
            switch (status) {
                case AVAILABLE: bg = new Color(0xE3F4EA); fg = new Color(0x1E7B45); break;
                case RESERVED:  bg = new Color(0xFBEBCB); fg = GOLD_DARK;           break;
                default:        bg = new Color(0xE8EEF5); fg = new Color(0x3B4C66); break; // OCCUPIED
            }

            int w = 64, h = 24;
            int x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
            g2.setColor(bg);
            g2.fillRoundRect(x, y, w, h, 6, 6);

            g2.setColor(fg);
            g2.setFont(sans(Font.PLAIN, 10));
            FontMetrics fm = g2.getFontMetrics();
            String text = status.toString();
            g2.drawString(text, x + (w - fm.stringWidth(text)) / 2,
                    y + (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }
}
