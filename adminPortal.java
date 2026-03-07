import java.sql.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
public class adminPortal {
    public static void adminDb() {
        JFrame AdminFrame= new JFrame("Admin Portal");
        AdminFrame.setSize(400,400); 
        AdminFrame.setLayout(new GridLayout(8, 2, 10, 10));
        JButton totalStatus = new JButton("Hospital Capacity Overview");
        totalStatus.addActionListener(e -> {
            int grandTotal = 0;
            int grandVacant = 0;
            try (Connection conn = HBMSmain.connectDB();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT total_beds, vacant_beds FROM rooms")) {
                while(rs.next()) {
                    grandTotal += rs.getInt("total_beds");
                    grandVacant += rs.getInt("vacant_beds");
                }
        JOptionPane.showMessageDialog(null, 
            "TOTAL HOSPITAL CAPACITY\n" +
            "----------------------------\n" +
            "Total Beds: " + grandTotal + "\n" +
            "Total Vacant: " + grandVacant + "\n" +
            "Occupancy Rate: " + (int)(((double)(grandTotal-grandVacant)/grandTotal)*100) + "%");
            } catch (Exception ex) { ex.printStackTrace(); }
        });
AdminFrame.add(totalStatus);
        JButton view = new JButton("View Patient's Live Record");
        view.addActionListener(e->{
            PatientsTable();
        });
        AdminFrame.add(view);
        JButton edit = new JButton("Edit Rooms");
        AdminFrame.add(edit); 
        edit.addActionListener(e->{manageRooms();});   
        JButton fileBtn = new JButton("Import External Hospital Data");
        fileBtn.addActionListener(e -> Files());
        AdminFrame.add(fileBtn);
        JButton graphBtn = new JButton("Analysis");
        graphBtn.addActionListener(e-> Analysis());
        AdminFrame.add(graphBtn);
        AdminFrame.setVisible(true);
        }
    public static void PatientsTable() {
    JFrame table = new JFrame("Patient's Record");
    table.setSize(800, 400);
    // This layout makes sure the button stays at the bottom and doesn't float around
    table.setLayout(new BorderLayout(10, 10));

    String[] columns = {"ID", "NAME", "ROOM", "ACTION", "TIMESTAMP"};
    DefaultTableModel model = new DefaultTableModel(columns, 0);
    JTable Ptable = new JTable(model);

    // --- 1. THE SEARCH LOGIC (Top of the Sandwich) ---
    TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    Ptable.setRowSorter(sorter);
    JButton searchBtn = new JButton("Search 🔍");
    JButton clearBtn = new JButton("Show All");
    JPanel searchPanel = new JPanel();
    searchPanel.add(searchBtn);
    searchPanel.add(clearBtn);

    searchBtn.addActionListener(e -> {
        String name = JOptionPane.showInputDialog(table, "Enter Patient Name:");
        if (name != null) sorter.setRowFilter(RowFilter.regexFilter("(?i)" + name, 1)); 
    });
    clearBtn.addActionListener(e -> sorter.setRowFilter(null));

    // --- 2. THE DISCHARGE LOGIC (Bottom of the Sandwich) ---
    JButton dischargeBtn = new JButton("DISCHARGE SELECTED PATIENT");
    dischargeBtn.addActionListener(e -> {
        int selectedRow = Ptable.getSelectedRow();
        if (selectedRow != -1) {
            int modelRow = Ptable.convertRowIndexToModel(selectedRow);
            String name = (String) model.getValueAt(modelRow, 1);
            String room = (String) model.getValueAt(modelRow, 2);
            String currentAction = (String) model.getValueAt(modelRow, 3);

            // Safety check: Don't discharge if they already checked out
            if (currentAction.contains("Check-out")) {
                JOptionPane.showMessageDialog(table, "Patient is already discharged!");
                return;
            }

            HBMSmain.releaseBedCount(room); // Free the bed in SQL
            HBMSmain.login(name, room, "Check-out (Admin)"); // Log the exit
            JOptionPane.showMessageDialog(table, name + " has been discharged!");
            
            table.dispose(); // Close window
            PatientsTable(); // Re-open window to see the new data
        } else {
            JOptionPane.showMessageDialog(table, "Please select a patient row first!");
        }
    });

    // --- 3. THE DATABASE FETCH (Filling the middle) ---
    try (Connection conn = HBMSmain.connectDB();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT * FROM patient_records ORDER BY log_time DESC")) {
        while (rs.next()) {
            model.addRow(new Object[]{
                rs.getInt("id"), rs.getString("patient_name"),
                rs.getString("room_type"), rs.getString("action_type"),
                rs.getTimestamp("log_time")
            });
        }
    } catch (SQLException e) {
        JOptionPane.showMessageDialog(null, "Error: " + e.getMessage());
    }

    // --- 4. ASSEMBLING THE UI ---
    table.add(searchPanel, BorderLayout.NORTH);       // Search goes TOP
    table.add(new JScrollPane(Ptable), BorderLayout.CENTER); // Table goes MIDDLE (with scrollbar!)
    table.add(dischargeBtn, BorderLayout.SOUTH);      // Discharge goes BOTTOM

    table.setVisible(true);
}
    public static void manageRooms() {
        JFrame editFrame = new JFrame("Department Manager");
        editFrame.setSize(400, 300);
        editFrame.setLayout(new GridLayout(4, 2, 10, 10));

        JTextField typeInput = new JTextField();
        JTextField totalInput = new JTextField();
        JButton addBtn = new JButton("Add/Update Room");
        JButton deleteBtn = new JButton("Delete Room");

        editFrame.add(new JLabel("Room Name:"));
        editFrame.add(typeInput);
        editFrame.add(new JLabel("Total Beds:"));
        editFrame.add(totalInput);
        editFrame.add(addBtn);
        editFrame.add(deleteBtn);

        // LOGIC TO ADD OR UPDATE
        addBtn.addActionListener(e -> {
            String type = typeInput.getText().toUpperCase();
            String total = totalInput.getText();
            
            String sql = "INSERT INTO rooms (room_type, total_beds, vacant_beds) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE total_beds = ?, vacant_beds = ?";
            
            try (Connection conn = HBMSmain.connectDB();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, type);
                pstmt.setInt(2, Integer.parseInt(total));
                pstmt.setInt(3, Integer.parseInt(total)); 
                pstmt.setInt(4, Integer.parseInt(total));
                pstmt.setInt(5, Integer.parseInt(total));
                pstmt.executeUpdate();
                JOptionPane.showMessageDialog(editFrame, "Room " + type + " Updated!");
            } catch (Exception ex) { ex.printStackTrace(); }
        });

        // LOGIC TO DELETE
        deleteBtn.addActionListener(e -> {
            String type = typeInput.getText().toUpperCase();
            try (Connection conn = HBMSmain.connectDB();
                PreparedStatement pstmt = conn.prepareStatement("DELETE FROM rooms WHERE room_type = ?")) {
                pstmt.setString(1, type);
                pstmt.executeUpdate();
                JOptionPane.showMessageDialog(editFrame, "Room Deleted!");
            } catch (Exception ex) { ex.printStackTrace(); }
        });

        editFrame.setVisible(true);
    } 
    public static void Files() {
    JFileChooser fileChooser = new JFileChooser();
    int response = fileChooser.showOpenDialog(null);
    if (response == JFileChooser.APPROVE_OPTION) {
        File file = fileChooser.getSelectedFile();
        
        // We need TWO instructions for the database
        String sqlLog = "INSERT INTO patient_records (patient_name, room_type, action_type) values (?,?,?)";
        String sqlUpdate = "UPDATE rooms SET vacant_beds = vacant_beds + ? WHERE room_type = ?";

        try (Connection conn = HBMSmain.connectDB();
             PreparedStatement logPst = conn.prepareStatement(sqlLog);
             PreparedStatement roomPst = conn.prepareStatement(sqlUpdate);
             BufferedReader br = new BufferedReader(new FileReader(file))) {

            conn.setAutoCommit(false); // "All or Nothing" - don't save until we are totally finished
            String line;

            while ((line = br.readLine()) != null) {
                String[] data = line.split(",");
                if (data.length == 3) {
                    String name = data[0].trim();
                    String room = data[1].trim().toUpperCase();
                    String action = data[2].trim();

                    // STEP A: Prepare the history record
                    logPst.setString(1, name);
                    logPst.setString(2, room);
                    logPst.setString(3, action);
                    logPst.addBatch(); // Put it on the first conveyor belt

                    // STEP B: Prepare the bed count update
                    // If they check-in, we add -1. If they check-out, we add +1.
                    int change = action.equalsIgnoreCase("Check-in") ? -1 : 1;
                    roomPst.setInt(1, change);
                    roomPst.setString(2, room);
                    roomPst.addBatch(); // Put it on the second conveyor belt
                }
            }

            // STEP C: Run both conveyor belts at once!
            logPst.executeBatch();
            roomPst.executeBatch();
            
            conn.commit(); // Now save everything to the database permanently
            JOptionPane.showMessageDialog(null, "Import Successful: Records added and Beds updated!");

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
     public static void Analysis(){
            JFrame graph = new JFrame("ANALYSIS");
            graph.setSize(400,800);
            graph.setLayout(new GridLayout(0,1));
            try(Connection conn = HBMSmain.connectDB();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT room_type,total_beds,vacant_beds from rooms"))
            {
                while (rs.next()) {
                String room = rs.getString("room_type");
                int total = rs.getInt("total_beds");
                int vacant = rs.getInt("vacant_beds");
                int occupied = total- vacant;
                JProgressBar bar = new JProgressBar(0,total);
                bar.setValue(occupied);
                bar.setStringPainted(true);
                double usage = ((double)occupied / total) * 100;
                if (usage >= 90) {
                    bar.setForeground(Color.RED);   
                } else if (usage >= 70) {
                    bar.setForeground(Color.ORANGE); 
                } else {
                    bar.setForeground(new Color(0, 150, 0)); 
                }
                bar.setString(room+":"+occupied+"/"+total+"Beds Filled");  
                graph.add(bar);  
                }      
            }
        catch(SQLException e){
            JOptionPane.showMessageDialog(null,"DataBase Error");
        }
        JButton refreshBtn = new JButton("Refresh Data 🔄");
refreshBtn.addActionListener(e -> {
    graph.dispose(); // Close the current window
    Analysis();      // Run the method again to get fresh SQL data
});
graph.add(refreshBtn);
        graph.setVisible(true); 
       }
    }


