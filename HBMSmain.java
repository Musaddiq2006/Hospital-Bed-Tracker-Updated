import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Dimension;
import javax.swing.*;
import java.sql.*;
import java.util.ArrayList;
public class HBMSmain {
public static Connection connectDB() throws SQLException {
    // Replace 'your_password' with your actual MySQL password
    String url = "jdbc:mysql://localhost:3306/hbms_project";
    String user = "root";
    String pass = "SMAhmed160424733468"; 
    return DriverManager.getConnection(url, user, pass);
}
public static void updateBedCount(String type) {
    String sql = "UPDATE rooms SET vacant_beds = vacant_beds - 1 WHERE room_type = ? AND vacant_beds > 0";

    try (Connection conn = connectDB(); 
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, type); // This replaces the first '?' with 'ICU'
        int rowsUpdated = pstmt.executeUpdate(); // This actually sends the command to MySQL
        if (rowsUpdated > 0) {
            System.out.println("Success: Database updated!");
        } else {
            System.out.println("Alert: No beds left in " + type);
        }
    } catch (SQLException e) {
        System.out.println("SQL Error: " + e.getMessage());
    }
}
public static int getLiveVacancy(String type) {
    String sql = "SELECT vacant_beds FROM rooms WHERE room_type = ?";
    try (Connection conn = connectDB(); 
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, type);
        ResultSet rs = pstmt.executeQuery();
        if (rs.next()) {
            return rs.getInt("vacant_beds"); // Return the REAL number from SQL
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
    return 0; 
}
public static void releaseBedCount(String type) {
    String sql = "UPDATE rooms SET vacant_beds = vacant_beds + 1 WHERE room_type = ? AND vacant_beds < total_beds";
    
    try (Connection conn = connectDB(); 
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        pstmt.setString(1, type);
        pstmt.executeUpdate();
        System.out.println("Bed released in Database!");
        
    } catch (SQLException e) {
        e.printStackTrace();
    }
}
public static boolean isAlreadyCheckedIn(String patientName) {
    // Look for the LATEST action for this specific patient
    String sql = "SELECT action_type FROM patient_records WHERE patient_name = ? ORDER BY log_time DESC LIMIT 1";
    try (Connection conn = connectDB();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, patientName);
        ResultSet rs = pstmt.executeQuery();
        if (rs.next()) {
            // If their last action was "Check-in", they are still in the hospital!
            return rs.getString("action_type").equals("Check-in");
        }
    } catch (SQLException e) { e.printStackTrace(); }
    return false; // If no records found, they are new or already out
}
static ArrayList<Room> roomList = new ArrayList<>();
public static void main(String[] args) {
    refreshRoomData();
    
    JFrame frame = new JFrame("HBMS Project");
    frame.setSize(700, 500); 
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); 

    // Using a simple GridLayout to stack everything and fill the space
    frame.setLayout(new GridLayout(5, 1, 10, 20)); 

    // --- TITLE ---
    JLabel title = new JLabel("Welcome to\n Hospital Bed Availability Tracker!", SwingConstants.CENTER);
    title.setFont(new Font("Arial", Font.BOLD, 24));
    
    // --- BUTTONS ---
    JButton patientbtn = new JButton("Patient Access");
    JButton adminbtn = new JButton("Admin Access");

    // Making buttons look a bit bigger
    patientbtn.setFont(new Font("Arial", Font.PLAIN, 16));
    adminbtn.setFont(new Font("Arial", Font.PLAIN, 16));

    // Existing ActionListeners (Keep these exactly as they are in your code)
    patientbtn.addActionListener(e -> {
        refreshRoomData();
        patientportal();
    });

    adminbtn.addActionListener(e -> {
        JPasswordField pf = new JPasswordField();
        int okCxl = JOptionPane.showConfirmDialog(frame, pf, "Enter Admin Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (okCxl == JOptionPane.OK_OPTION) {
            String password = new String(pf.getPassword());
            if (password.equals("1234")) {
                adminPortal.adminDb();
            } else {
                JOptionPane.showMessageDialog(frame, "Access Denied", "Security Alert", JOptionPane.ERROR_MESSAGE);
            }
        }
    });

    // Add spacers and components to the frame
    frame.add(new JLabel("")); // Top Spacer
    frame.add(title);
    frame.add(patientbtn);
    frame.add(adminbtn);
    frame.add(new JLabel("")); // Bottom Spacer

    frame.setVisible(true);
}

public static void patientportal() {
    JFrame patframe = new JFrame("Live Bed Tracker");
    patframe.setSize(500, 600);
    patframe.setLayout(new BorderLayout(10, 10));

    // --- TOP: PATIENT INFO ---
    JPanel topPanel = new JPanel(new GridLayout(2, 1));
    JTextField nameField = new JTextField();
    nameField.setBorder(BorderFactory.createTitledBorder("Patient Full Name"));
    topPanel.add(nameField);
    
    JComboBox<String> roomDropdown = new JComboBox<>();
    for(Room r : roomList) { roomDropdown.addItem(r.type); }
    topPanel.add(roomDropdown);

    // --- CENTER: THE BIG DISPLAY (Detailing) ---
    JPanel displayPanel = new JPanel(new GridLayout(2, 1));
    JLabel bigNumber = new JLabel("--", SwingConstants.CENTER);
    bigNumber.setFont(new Font("Arial", Font.BOLD, 120)); // HUGE NUMBER
    
    JLabel detailLabel = new JLabel("Select Department", SwingConstants.CENTER);
    detailLabel.setFont(new Font("Monospaced", Font.PLAIN, 16));
    
    displayPanel.add(bigNumber);
    displayPanel.add(detailLabel);

    // --- LOGIC: UPDATE DISPLAY ---
    roomDropdown.addActionListener(e -> {
        String selected = (String) roomDropdown.getSelectedItem();
        int vacant = getLiveVacancy(selected);
        
        // Detailing: Calculate percentage
        bigNumber.setText(String.valueOf(vacant));
        
        // Color coding for urgency
        if (vacant == 0) bigNumber.setForeground(Color.RED);
        else if (vacant < 3) bigNumber.setForeground(new Color(255, 140, 0)); // Orange
        else bigNumber.setForeground(new Color(34, 139, 34)); // Forest Green
        
        detailLabel.setText("AVAILABLE BEDS IN " + selected);
    });

    // --- BOTTOM: ACTIONS ---
    JButton checkinBtn = new JButton("CONFIRM BOOKING");
    checkinBtn.setPreferredSize(new Dimension(0, 50));
    checkinBtn.addActionListener(e -> {
    String pName = nameField.getText().trim();
    String selectedRoom = (String) roomDropdown.getSelectedItem();

    // STEP 1: Check if Name is empty
    if (pName.isEmpty()) {
        JOptionPane.showMessageDialog(patframe, "Please enter a patient name!");
        return;
    }

    // STEP 2: The Judge-Proof Check
    if (isAlreadyCheckedIn(pName)) {
        JOptionPane.showMessageDialog(patframe, "Access Denied: " + pName + " is already admitted to the hospital!");
        return; // Stops the code here
    }

    // STEP 3: Proceed with booking if they passed the check
    int currentBeds = getLiveVacancy(selectedRoom);
    if (currentBeds > 0) {
        updateBedCount(selectedRoom);
        login(pName, selectedRoom, "Check-in");
        JOptionPane.showMessageDialog(patframe, "Bed Booked Successfully for " + pName);
    } else {
        JOptionPane.showMessageDialog(patframe, "Sorry, " + selectedRoom + " is full!");
    }
});
    patframe.add(topPanel, BorderLayout.NORTH);
    patframe.add(displayPanel, BorderLayout.CENTER);
    patframe.add(checkinBtn, BorderLayout.SOUTH);
    patframe.setVisible(true);
}
        public static void login(String name,String room,String action){
        String sql="INSERT into patient_records (patient_name,room_type,action_type) values (?,?,?)"; 
            try(Connection conn= connectDB();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, name);
        pstmt.setString(2, room);
        pstmt.setString(3, action);
        pstmt.executeUpdate();
    } catch (SQLException e) {
        e.printStackTrace();
    }
}
public static void refreshRoomData(){
    roomList.clear(); // Wipe the old RAM data
    try (Connection conn = connectDB();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT * FROM rooms")) {
        
        while (rs.next()) {
            roomList.add(new Room(rs.getString("room_type"), rs.getInt("vacant_beds")));
        }
        System.out.println("Room list synced with Database.");
    } catch (SQLException e) {
        e.printStackTrace();
    }
}
}
class Room{
    String type;
    int totalbeds;
    int occupied;

    public Room(String type,int totalbeds){
        this.type=type;
        this.totalbeds=totalbeds;
        this.occupied=0;
    }
}

