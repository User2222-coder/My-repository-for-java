package databaseproject;

import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.time.YearMonth;
import javax.swing.*;

public class Gui {
    // Global state parameters tracking current active dates
    private static int currentYear = 2026;
    private static int currentMonth = 12; // Default to starting on December
    private static int selectedDay = 1;   // Default to the first day of the month
    
    // Global references to Swing layout containers
    private static JTextArea reportArea;  
    private static JLabel feedbackLabel;  
    private static JLabel currentMonthLabel;
    private static JLabel selectedDayLabel;
    private static JPanel calendarPanel;   // Panel to hold our absolute day buttons
    private static JFrame frame;

    public static void main(String[] args) {
        // 1. Initializing the SQLite database table schemas right away
        setupStorageBackend();

        // 2. Setting up the base master window container layout parameters
        frame = new JFrame("Mom's Dynamic Shift Manager");
        frame.setBounds(500, 100, 850, 520); // Width expanded to fit calendar and readout side-by-side
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(null); // ABSOLUTE PIXEL POSITIONING ACTIVE

        // --- LEFT COLUMN: CONTROL & INPUT INTERFACES ---
        currentMonthLabel = new JLabel("Month: 12 / 2026", JLabel.CENTER);
        currentMonthLabel.setFont(new Font("Arial", Font.BOLD, 16));
        currentMonthLabel.setBounds(50, 20, 380, 25);
        frame.add(currentMonthLabel);

        JButton prevMonthBtn = new JButton("< Prev Month");
        prevMonthBtn.setBounds(50, 60, 180, 35);
        frame.add(prevMonthBtn);

        JButton nextMonthBtn = new JButton("Next Month >");
        nextMonthBtn.setBounds(250, 60, 180, 35);
        frame.add(nextMonthBtn);

        selectedDayLabel = new JLabel("Selected Day: 1", JLabel.LEFT);
        selectedDayLabel.setFont(new Font("Arial", Font.BOLD, 13));
        selectedDayLabel.setForeground(Color.RED);
        selectedDayLabel.setBounds(50, 115, 380, 20);
        frame.add(selectedDayLabel);

        JTextField hoursInput = new JTextField();
        hoursInput.setBounds(50, 140, 380, 30);
        frame.add(hoursInput);

        JButton commitButton = new JButton("Log Shift Hours");
        commitButton.setBounds(50, 185, 380, 40);
        frame.add(commitButton);

        feedbackLabel = new JLabel("System ready. Select a day and type hours.");
        feedbackLabel.setBounds(50, 235, 400, 100);
        frame.add(feedbackLabel);

        // --- BOTTOM LEFT: DYNAMIC MONTHLY SUMMARY DISPLAY ---
        reportArea = new JTextArea();
        reportArea.setEditable(false);
        reportArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        reportArea.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        reportArea.setBounds(50, 275, 380, 180);
        frame.add(reportArea);

        // --- RIGHT COLUMN: INTERACTIVE DAY BUTTONS AREA ---
        calendarPanel = new JPanel();
        calendarPanel.setLayout(null); // Nested absolute positioning
        calendarPanel.setBounds(460, 20, 340, 435);
        frame.add(calendarPanel);

        // --- ACTION BUTTON LISTENERS ---
        prevMonthBtn.addActionListener(e -> {
            if (--currentMonth < 1) { currentMonth = 12; currentYear--; } 
            selectedDay = 1; // Reset back to first day on shift transition
            updateCalendarButtons();
            refreshMonthlyReport();
        });

        nextMonthBtn.addActionListener(e -> {
            if (++currentMonth > 12) { currentMonth = 1; currentYear++; }
            selectedDay = 1;
            updateCalendarButtons();
            refreshMonthlyReport();
        });

        commitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String inputPayload = hoursInput.getText().trim();
                
                if (inputPayload.isEmpty()) {
                    feedbackLabel.setText("Please type a valid number first!");
                    return;
                }
                
                // Commits data matching exact user date selections
                boolean success = writeHoursToDisk(currentYear, currentMonth, selectedDay, inputPayload);
                
                if (success) {
                    feedbackLabel.setText("Saved Day " + selectedDay + " (" + inputPayload + " hrs) successfully!");
                    hoursInput.setText(""); 
                    refreshMonthlyReport(); // Triggers running math sums refresh for current month only
                } else {
                    feedbackLabel.setText("Error writing metrics down to storage file.");
                }
            }
        });

        // 3. Render starting interface views based on initial parameters
        updateCalendarButtons();
        refreshMonthlyReport();

        frame.setVisible(true);
    }

    /**
     * DYNAMIC GRID GENERATOR: Manually loops, calculates positions, and drops 
     * precise clickable buttons matching the length of the currently selected month.
     */
    private static void updateCalendarButtons() {
        calendarPanel.removeAll(); // Wipe out old day button configurations

        // Rewrite top tracker banners dynamically
        currentMonthLabel.setText("Month: " + currentMonth + " / " + currentYear);
        selectedDayLabel.setText("Selected Day: " + selectedDay);

        // Calculate maximum day counts for targeted active timeline slots
        YearMonth ym = YearMonth.of(currentYear, currentMonth);
        int daysInMonth = ym.lengthOfMonth();

        int buttonWidth = 55;
        int buttonHeight = 45;
        int horizontalGap = 10;
        int verticalGap = 10;

        // Loop dynamically to build a custom absolute grid array coordinates matrix (6 columns wide)
        for (int i = 0; i < daysInMonth; i++) {
            int dayNumber = i + 1;
            
            int column = i % 6; // Wraps rows down cleanly after every 6 buttons
            int row = i / 6;

            int x = column * (buttonWidth + horizontalGap);
            int y = row * (buttonHeight + verticalGap);

            JButton dayButton = new JButton(String.valueOf(dayNumber));
            dayButton.setBounds(x, y, buttonWidth, buttonHeight);
            dayButton.setFont(new Font("Arial", Font.PLAIN, 11));

            // Connect button clicks to capture selection coordinates
            dayButton.addActionListener(e -> {
                selectedDay = dayNumber;
                selectedDayLabel.setText("Selected Day: " + selectedDay);
                feedbackLabel.setText("Ready to record shift entry data for day " + selectedDay + ".");
            });

            calendarPanel.add(dayButton);
        }

        calendarPanel.revalidate();
        calendarPanel.repaint();
    }

    // ========================================================
    //  STANDALONE BACKEND PERSISTENT SQL METHOD STRATIFIED BLOCKS
    // ========================================================

    private static void setupStorageBackend() {
        String url = "jdbc:sqlite:appdata.db";
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            // RENAMED TO shift_records TO Sidestep Outdated File Constraints
            stmt.execute("CREATE TABLE IF NOT EXISTS shift_records (" +
                         "year INTEGER, month INTEGER, day INTEGER, logged_hours REAL, " +
                         "PRIMARY KEY(year, month, day));");
        } catch (Exception ex) { 
            ex.printStackTrace();
        }
    }

    private static boolean writeHoursToDisk(int yr, int mo, int dy, String hoursValue) {
        String url = "jdbc:sqlite:appdata.db";
        String insertSQL = "INSERT OR REPLACE INTO shift_records VALUES (?, ?, ?, ?);";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            
            pstmt.setInt(1, yr);
            pstmt.setInt(2, mo);
            pstmt.setInt(3, dy);
            pstmt.setDouble(4, Double.parseDouble(hoursValue));
            
            pstmt.executeUpdate();
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private static void refreshMonthlyReport() {
        String url = "jdbc:sqlite:appdata.db";
        String selectSQL = "SELECT day, logged_hours FROM shift_records WHERE year = ? AND month = ? ORDER BY day ASC;";
        
        StringBuilder reportBuilder = new StringBuilder();
        reportBuilder.append("--- Monthly Report: ").append(currentMonth).append("/").append(currentYear).append(" ---\n");
        
        double totalHoursAccumulator = 0.0;
        
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            
            pstmt.setInt(1, currentYear);
            pstmt.setInt(2, currentMonth);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int day = rs.getInt("day");
                    double hours = rs.getDouble("logged_hours");
                    
                    reportBuilder.append(" Day ").append(day).append(" -> ").append(hours).append(" hours\n");
                    totalHoursAccumulator += hours;
                }
            }
            
            reportBuilder.append("-------------------------------------\n");
            reportBuilder.append(" TOTAL CALCULATED HOURS: ").append(totalHoursAccumulator).append(" hrs");
            
            reportArea.setText(reportBuilder.toString());
            
        } catch (Exception ex) {reportArea.setText("Error reading logs from database file.");
        ex.printStackTrace();
        }
        }
        }
        
        

