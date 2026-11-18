package nourishnet;

import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("NourishNet - Food shouldn't go to waste");
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Donor", new DonorPanel());
            tabs.addTab("Volunteer / NGO", new NGOPanel());
            f.add(tabs);
            f.setSize(700, 420);
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
        });
    }
}
