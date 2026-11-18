package nourishnet;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

public class DonorPanel extends JPanel {
    private final JTextField foodType = new JTextField(18);
    private final JTextField quantity = new JTextField(18);
    private final JTextField pickup = new JTextField(18);
    private final JTextField location = new JTextField(18);
    private final JTextField bestBy = new JTextField(18);
    private final int donorId = 1; // demo: logged-in donor

    public DonorPanel() {
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.anchor = GridBagConstraints.WEST;

        String[] labels = {"Food Type:", "Quantity (meals):", "Pickup Time:", "Location:", "Expiry / Best By:"};
        JTextField[] fields = {foodType, quantity, pickup, location, bestBy};
        for (int i = 0; i < labels.length; i++) {
            c.gridx = 0; c.gridy = i; add(new JLabel(labels[i]), c);
            c.gridx = 1; add(fields[i], c);
        }
        JButton post = new JButton("POST DONATION");
        post.setBackground(new Color(46, 125, 50));
        post.setForeground(Color.WHITE);
        c.gridx = 0; c.gridy = labels.length; c.gridwidth = 2;
        c.anchor = GridBagConstraints.CENTER;
        add(post, c);

        post.addActionListener(e -> {
            try {
                int qty = Integer.parseInt(quantity.getText().trim());
                DonationValidator.validateDonation(foodType.getText().trim(), qty,
                        location.getText().trim(), pickup.getText().trim(), bestBy.getText().trim());
                boolean ok = new DonationDAO().addDonation(donorId, foodType.getText().trim(), qty,
                        location.getText().trim(), pickup.getText().trim(), bestBy.getText().trim());
                JOptionPane.showMessageDialog(this, ok ? "Donation posted!" : "Could not save donation");
                if (ok) for (JTextField f : fields) f.setText("");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Quantity must be a number");
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Could not connect to the database. Check the database settings.",
                        "Database error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
