package nourishnet;

import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class NGOPanel extends JPanel {
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Food", "Quantity", "Location", "Pickup", "Best By"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final int volunteerId = 1; // demo: logged-in volunteer
    private final DonationDAO dao = new DonationDAO();

    public NGOPanel() {
        setLayout(new BorderLayout(8, 8));
        add(new JLabel("AVAILABLE DONATIONS", SwingConstants.CENTER), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton refresh = new JButton("Refresh");
        JButton accept = new JButton("ACCEPT");
        accept.setBackground(new Color(46, 125, 50));
        accept.setForeground(Color.WHITE);
        JPanel bottom = new JPanel();
        bottom.add(refresh);
        bottom.add(accept);
        add(bottom, BorderLayout.SOUTH);

        refresh.addActionListener(e -> load());
        accept.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Select a donation first");
                return;
            }
            int id = (int) model.getValueAt(row, 0);
            try {
                boolean ok = dao.acceptDonation(id, volunteerId);
                JOptionPane.showMessageDialog(this, ok ? "Pickup accepted!" : "Already taken or error");
                load();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Could not connect to the database. Check the database settings.",
                        "Database error", JOptionPane.ERROR_MESSAGE);
            }
        });
        load();
    }

    private void load() {
        model.setRowCount(0);
        try {
            List<Donation> list = dao.getAvailableDonations();
            for (Donation d : list)
                model.addRow(new Object[]{d.getId(), d.getFoodType(), d.getQuantity(),
                        d.getLocation(), d.getPickupTime(), d.getBestBy()});
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Could not load donations. Check the database settings.",
                    "Database error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
