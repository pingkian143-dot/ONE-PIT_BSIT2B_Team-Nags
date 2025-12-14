import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RideAssistGUIwithDriver {

    // Database connection
    static final String DB_URL = "jdbc:mysql://localhost:3306/ride_assist_db";
    static final String DB_USER = "root";
    static final String DB_PASSWORD = "";
    static Connection connection;

    // Passenger data
    static String savedFullName = "";
    static String savedNumber = "";
    static String savedPassword = "";
    static int passengerId = -1;

    // Admin data
    static String adminUsername = "admin";
    static String adminPassword = "admin123";
    static boolean isAdminLoggedIn = false;

    // Ride and driver data
    static List<String> rideHistory = new ArrayList<>();
    static List<RideRequest> pendingRideRequests = new ArrayList<>();
    static List<RideRequest> activeRides = new ArrayList<>();
    static Map<String, Driver> drivers = new HashMap<>();
    static String currentDriverLoggedIn = null;
    
    // Track rides pending rating
    static Map<String, RideRequest> ridesPendingRating = new HashMap<>();

    // Ride request class
    static class RideRequest {
        int id;
        String passengerName;
        String passengerNumber;
        String from;
        String to;
        String status; // "PENDING", "ACCEPTED", "COMPLETED"
        String driverAssigned;
        int fare;
        int rating;
        int passengerId;
        int driverId;
        
        public RideRequest(String passengerName, String passengerNumber, String from, String to, int passengerId) {
            this.passengerName = passengerName;
            this.passengerNumber = passengerNumber;
            this.from = from;
            this.to = to;
            this.status = "PENDING";
            this.driverAssigned = null;
            this.fare = calculateFare();
            this.rating = 0;
            this.passengerId = passengerId;
            this.driverId = -1;
        }
        
        public RideRequest(int id, String passengerName, String passengerNumber, String from, String to, 
                          String status, String driverAssigned, int fare, int rating, int passengerId, int driverId) {
            this.id = id;
            this.passengerName = passengerName;
            this.passengerNumber = passengerNumber;
            this.from = from;
            this.to = to;
            this.status = status;
            this.driverAssigned = driverAssigned;
            this.fare = fare;
            this.rating = rating;
            this.passengerId = passengerId;
            this.driverId = driverId;
        }
        
        private int calculateFare() {
            // Base fare + distance-based calculation (simulated)
            int baseFare = 40;
            int distanceFare = (int)(Math.random() * 30) + 10; // 10-40 pesos
            return baseFare + distanceFare; // Total 50-80 pesos
        }
        
        @Override
        public String toString() {
            String result = "From: " + from + " To: " + to + " - Status: " + status + 
                   (driverAssigned != null ? " (Driver: " + driverAssigned + ")" : "") +
                   " - Fare: ₱" + fare;
            if (rating > 0) {
                result += " - Rating: " + rating + "/5";
            }
            return result;
        }
    }

    // Driver class
    static class Driver {
        int id;
        String name;
        String vehicle;
        String priceRange;
        String username;
        String password;
        boolean isAvailable;
        double totalEarnings;
        int totalRides;
        double averageRating;
        
        public Driver(int id, String name, String vehicle, String priceRange, String username, String password) {
            this.id = id;
            this.name = name;
            this.vehicle = vehicle;
            this.priceRange = priceRange;
            this.username = username;
            this.password = password;
            this.isAvailable = true;
            this.totalEarnings = 0;
            this.totalRides = 0;
            this.averageRating = 0;
        }
        
        public void addRideEarnings(int fare) {
            this.totalEarnings += fare;
            this.totalRides++;
            updateDriverInDB();
        }
        
        public void addRating(int rating) {
            // Update average rating
            if (this.totalRides == 0) {
                this.averageRating = rating;
            } else {
                double totalRatingPoints = this.averageRating * this.totalRides;
                this.averageRating = (totalRatingPoints + rating) / (this.totalRides + 1);
            }
            updateDriverInDB();
        }
        
        private void updateDriverInDB() {
            try {
                String sql = "UPDATE drivers SET total_earnings = ?, total_rides = ?, average_rating = ? WHERE id = ?";
                PreparedStatement pstmt = connection.prepareStatement(sql);
                pstmt.setDouble(1, this.totalEarnings);
                pstmt.setInt(2, this.totalRides);
                pstmt.setDouble(3, this.averageRating);
                pstmt.setInt(4, this.id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        public String getEarningsSummary() {
            return String.format("Total Earnings: ₱%.2f\nTotal Rides: %d\nAverage Rating: %.1f/5", 
                totalEarnings, totalRides, averageRating);
        }
    }

    public static void main(String[] args) {
        // Initialize database
        initializeDatabase();
        // Initialize drivers from database
        initializeDrivers();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showMainMenu();
            }
        });
    }

    private static void initializeDatabase() {
        try {
            // Load MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // Create connection
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("Connected to database successfully!");
            
            // Create tables if they don't exist
            createTables();
            
            // Load ride history from database
            loadRideHistory();
            
            // Load pending and active rides
            loadActiveRides();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, 
                "Database connection failed!\n" +
                "Please make sure:\n" +
                "1. XAMPP is running\n" +
                "2. MySQL service is started\n" +
                "3. Database 'ride_assist_db' exists\n\n" +
                "Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void createTables() throws SQLException {
        Statement stmt = connection.createStatement();
        
        // Create passengers table
        String createPassengersTable = "CREATE TABLE IF NOT EXISTS passengers (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "full_name VARCHAR(100) NOT NULL, " +
                "phone_number VARCHAR(20) UNIQUE NOT NULL, " +
                "password VARCHAR(100) NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
        stmt.execute(createPassengersTable);
        
        // Create drivers table
        String createDriversTable = "CREATE TABLE IF NOT EXISTS drivers (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "name VARCHAR(100) NOT NULL, " +
                "vehicle VARCHAR(100), " +
                "price_range VARCHAR(50), " +
                "username VARCHAR(50) UNIQUE NOT NULL, " +
                "password VARCHAR(100) NOT NULL, " +
                "is_available BOOLEAN DEFAULT TRUE, " +
                "total_earnings DECIMAL(10,2) DEFAULT 0, " +
                "total_rides INT DEFAULT 0, " +
                "average_rating DECIMAL(3,2) DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
        stmt.execute(createDriversTable);
        
        // Create rides table
        String createRidesTable = "CREATE TABLE IF NOT EXISTS rides (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "passenger_id INT, " +
                "driver_id INT, " +
                "from_location VARCHAR(200) NOT NULL, " +
                "to_location VARCHAR(200) NOT NULL, " +
                "fare INT NOT NULL, " +
                "status VARCHAR(20) DEFAULT 'PENDING', " +
                "rating INT DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "completed_at TIMESTAMP NULL, " +
                "FOREIGN KEY (passenger_id) REFERENCES passengers(id), " +
                "FOREIGN KEY (driver_id) REFERENCES drivers(id)" +
                ")";
        stmt.execute(createRidesTable);
        
        // Create admin table
        String createAdminTable = "CREATE TABLE IF NOT EXISTS admin (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "username VARCHAR(50) UNIQUE NOT NULL, " +
                "password VARCHAR(100) NOT NULL" +
                ")";
        stmt.execute(createAdminTable);
        
        // Insert default admin if not exists
        String checkAdmin = "SELECT COUNT(*) FROM admin WHERE username = 'admin'";
        ResultSet rs = stmt.executeQuery(checkAdmin);
        rs.next();
        if (rs.getInt(1) == 0) {
            String insertAdmin = "INSERT INTO admin (username, password) VALUES ('admin', 'admin123')";
            stmt.execute(insertAdmin);
        }
        
        stmt.close();
    }

    private static void initializeDrivers() {
        try {
            String sql = "SELECT * FROM drivers";
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String vehicle = rs.getString("vehicle");
                String priceRange = rs.getString("price_range");
                String username = rs.getString("username");
                String password = rs.getString("password");
                boolean isAvailable = rs.getBoolean("is_available");
                double totalEarnings = rs.getDouble("total_earnings");
                int totalRides = rs.getInt("total_rides");
                double averageRating = rs.getDouble("average_rating");
                
                Driver driver = new Driver(id, name, vehicle, priceRange, username, password);
                driver.isAvailable = isAvailable;
                driver.totalEarnings = totalEarnings;
                driver.totalRides = totalRides;
                driver.averageRating = averageRating;
                
                drivers.put(username, driver);
            }
            
            // If no drivers in database, add default ones
            if (drivers.isEmpty()) {
                addDefaultDrivers();
            }
            
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void addDefaultDrivers() throws SQLException {
        String[][] defaultDrivers = {
            {"Juan Dela Cruz", "Yamaha Mio 125", "₱45-70", "driver1", "pass1"},
            {"Ana Marie Campos", "Honda Beat 110", "₱50-75", "driver2", "pass2"},
            {"Carlos Reyes", "Suzuki Skydrive", "₱40-65", "driver3", "pass3"}
        };
        
        String sql = "INSERT INTO drivers (name, vehicle, price_range, username, password) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        
        for (String[] driverData : defaultDrivers) {
            pstmt.setString(1, driverData[0]);
            pstmt.setString(2, driverData[1]);
            pstmt.setString(3, driverData[2]);
            pstmt.setString(4, driverData[3]);
            pstmt.setString(5, driverData[4]);
            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                int id = rs.getInt(1);
                Driver driver = new Driver(id, driverData[0], driverData[1], driverData[2], driverData[3], driverData[4]);
                drivers.put(driverData[3], driver);
            }
        }
        pstmt.close();
    }

    private static void loadRideHistory() {
        try {
            String sql = "SELECT r.*, p.full_name, p.phone_number, d.name as driver_name " +
                        "FROM rides r " +
                        "LEFT JOIN passengers p ON r.passenger_id = p.id " +
                        "LEFT JOIN drivers d ON r.driver_id = d.id " +
                        "WHERE r.status = 'COMPLETED' " +
                        "ORDER BY r.created_at DESC";
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                String rideInfo = "From: " + rs.getString("from_location") + 
                                 " To: " + rs.getString("to_location") + 
                                 " - Status: COMPLETED" +
                                 " (Driver: " + rs.getString("driver_name") + ")" +
                                 " - Fare: ₱" + rs.getInt("fare");
                
                int rating = rs.getInt("rating");
                if (rating > 0) {
                    rideInfo += " - Rating: " + rating + "/5";
                }
                
                rideHistory.add(rideInfo);
            }
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void loadActiveRides() {
        try {
            // Load pending rides
            String pendingSql = "SELECT r.*, p.full_name, p.phone_number " +
                              "FROM rides r " +
                              "JOIN passengers p ON r.passenger_id = p.id " +
                              "WHERE r.status = 'PENDING'";
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(pendingSql);
            
            while (rs.next()) {
                RideRequest ride = new RideRequest(
                    rs.getInt("id"),
                    rs.getString("full_name"),
                    rs.getString("phone_number"),
                    rs.getString("from_location"),
                    rs.getString("to_location"),
                    "PENDING",
                    null,
                    rs.getInt("fare"),
                    rs.getInt("rating"),
                    rs.getInt("passenger_id"),
                    -1
                );
                pendingRideRequests.add(ride);
            }
            
            // Load accepted rides
            String acceptedSql = "SELECT r.*, p.full_name, p.phone_number, d.name as driver_name " +
                               "FROM rides r " +
                               "JOIN passengers p ON r.passenger_id = p.id " +
                               "JOIN drivers d ON r.driver_id = d.id " +
                               "WHERE r.status = 'ACCEPTED'";
            rs = stmt.executeQuery(acceptedSql);
            
            while (rs.next()) {
                RideRequest ride = new RideRequest(
                    rs.getInt("id"),
                    rs.getString("full_name"),
                    rs.getString("phone_number"),
                    rs.getString("from_location"),
                    rs.getString("to_location"),
                    "ACCEPTED",
                    rs.getString("driver_name"),
                    rs.getInt("fare"),
                    rs.getInt("rating"),
                    rs.getInt("passenger_id"),
                    rs.getInt("driver_id")
                );
                activeRides.add(ride);
                
                // Make driver unavailable
                Driver driver = getDriverById(rs.getInt("driver_id"));
                if (driver != null) {
                    driver.isAvailable = false;
                }
            }
            
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static Driver getDriverById(int driverId) {
        for (Driver driver : drivers.values()) {
            if (driver.id == driverId) {
                return driver;
            }
        }
        return null;
    }

    // Add this new method to handle database operations for accepting rides
    private static void acceptRideRequestInDB(RideRequest request, Driver driver) {
        try {
            String sql = "UPDATE rides SET driver_id = ?, status = 'ACCEPTED' WHERE id = ?";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setInt(1, driver.id);
            pstmt.setInt(2, request.id);
            pstmt.executeUpdate();
            pstmt.close();
            
            // Update driver availability in database
            String updateDriverSql = "UPDATE drivers SET is_available = FALSE WHERE id = ?";
            pstmt = connection.prepareStatement(updateDriverSql);
            pstmt.setInt(1, driver.id);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Add this method for completing rides
    private static void completeRideInDB(RideRequest ride) {
        try {
            String sql = "UPDATE rides SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP WHERE id = ?";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setInt(1, ride.id);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Add this method for rating rides
    private static void rateRideInDB(RideRequest ride) {
        try {
            String sql = "UPDATE rides SET rating = ?, status = 'RATED' WHERE id = ?";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setInt(1, ride.rating);
            pstmt.setInt(2, ride.id);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void showMainMenu() {
        JFrame frame = new JFrame("OROQUIETA RIDE ASSIST");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 350);
        frame.setLayout(new GridLayout(5, 1));

        JButton createAccountBtn = new JButton("Create Account");
        JButton loginBtn = new JButton("Passenger Login");
        JButton driverLoginBtn = new JButton("Driver Login");
        JButton adminLoginBtn = new JButton("Admin Login");
        JButton exitBtn = new JButton("Exit");

        frame.add(createAccountBtn);
        frame.add(loginBtn);
        frame.add(driverLoginBtn);
        frame.add(adminLoginBtn);
        frame.add(exitBtn);

        createAccountBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
                createAccountGUI();
            }
        });

        loginBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
                loginGUI();
            }
        });

        driverLoginBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
                driverLoginGUI();
            }
        });

        adminLoginBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
                adminLoginGUI();
            }
        });

        exitBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(frame, "Thank you for using Oroquieta Ride Assist!");
                frame.dispose();
            }
        });

        frame.setVisible(true);
    }

    public static void createAccountGUI() {
        JFrame frame = new JFrame("Create Account");
        frame.setSize(400, 300);
        frame.setLayout(new GridLayout(5, 2));

        JLabel nameLabel = new JLabel("Full Name:");
        JTextField nameField = new JTextField();

        JLabel numberLabel = new JLabel("Phone Number:");
        JTextField numberField = new JTextField();

        JLabel passwordLabel = new JLabel("Password:");
        JPasswordField passwordField = new JPasswordField();

        JButton submitBtn = new JButton("Submit");
        JButton backBtn = new JButton("Back");

        frame.add(nameLabel);
        frame.add(nameField);
        frame.add(numberLabel);
        frame.add(numberField);
        frame.add(passwordLabel);
        frame.add(passwordField);
        frame.add(backBtn);
        frame.add(submitBtn);

        submitBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String fullName = nameField.getText();
                String phoneNumber = numberField.getText();
                String password = new String(passwordField.getPassword());

                if(fullName.isEmpty() || phoneNumber.isEmpty() || password.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Please fill in all fields!");
                    return;
                }

                try {
                    // Check if phone number already exists
                    String checkSql = "SELECT COUNT(*) FROM passengers WHERE phone_number = ?";
                    PreparedStatement checkStmt = connection.prepareStatement(checkSql);
                    checkStmt.setString(1, phoneNumber);
                    ResultSet rs = checkStmt.executeQuery();
                    rs.next();
                    
                    if (rs.getInt(1) > 0) {
                        JOptionPane.showMessageDialog(frame, "Phone number already registered!");
                        return;
                    }
                    
                    // Insert new passenger
                    String insertSql = "INSERT INTO passengers (full_name, phone_number, password) VALUES (?, ?, ?)";
                    PreparedStatement pstmt = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                    pstmt.setString(1, fullName);
                    pstmt.setString(2, phoneNumber);
                    pstmt.setString(3, password);
                    pstmt.executeUpdate();
                    
                    // Get generated ID
                    ResultSet generatedKeys = pstmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        passengerId = generatedKeys.getInt(1);
                    }
                    
                    savedFullName = fullName;
                    savedNumber = phoneNumber;
                    savedPassword = password;
                    
                    JOptionPane.showMessageDialog(frame, "Account created successfully!");
                    frame.dispose();
                    showMainMenu();
                    
                    pstmt.close();
                    checkStmt.close();
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(frame, "Error creating account: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });

        backBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
                showMainMenu();
            }
        });

        frame.setVisible(true);
    }

    public static void loginGUI() {
        JFrame frame = new JFrame("Passenger Login");
        frame.setSize(400, 200);
        frame.setLayout(new GridLayout(4, 2));

        JLabel numberLabel = new JLabel("Phone Number:");
        JTextField numberField = new JTextField();

        JLabel passwordLabel = new JLabel("Password:");
        JPasswordField passwordField = new JPasswordField();

        JButton backBtn = new JButton("Back");
        JButton loginBtn = new JButton("Login");

        frame.add(numberLabel);
        frame.add(numberField);
        frame.add(passwordLabel);
        frame.add(passwordField);
        frame.add(backBtn);
        frame.add(loginBtn);

        loginBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String number = numberField.getText();
                String password = new String(passwordField.getPassword());

                try {
                    String sql = "SELECT * FROM passengers WHERE phone_number = ? AND password = ?";
                    PreparedStatement pstmt = connection.prepareStatement(sql);
                    pstmt.setString(1, number);
                    pstmt.setString(2, password);
                    ResultSet rs = pstmt.executeQuery();
                    
                    if (rs.next()) {
                        passengerId = rs.getInt("id");
                        savedFullName = rs.getString("full_name");
                        savedNumber = rs.getString("phone_number");
                        savedPassword = rs.getString("password");
                        
                        JOptionPane.showMessageDialog(frame, "Login successful. Welcome, " + savedFullName + "!");
                        frame.dispose();
                        passengerMenuGUI();
                    } else {
                        JOptionPane.showMessageDialog(frame, "Incorrect number or password.");
                    }
                    
                    pstmt.close();
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(frame, "Error during login: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });

        backBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
                showMainMenu();
            }
        });

        frame.setVisible(true);
    }

    public static void driverLoginGUI() {
        JFrame frame = new JFrame("Driver Login");
        frame.setSize(400, 200);
        frame.setLayout(new GridLayout(4, 2));

        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField();

        JLabel passLabel = new JLabel("Password:");
        JPasswordField passField = new JPasswordField();

        JButton backBtn = new JButton("Back");
        JButton loginBtn = new JButton("Login");

        frame.add(userLabel);
        frame.add(userField);
        frame.add(passLabel);
        frame.add(passField);
        frame.add(backBtn);
        frame.add(loginBtn);

        loginBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = userField.getText();
                String password = new String(passField.getPassword());

                if (drivers.containsKey(username)) {
                    Driver driver = drivers.get(username);
                    if (driver.password.equals(password)) {
                        currentDriverLoggedIn = username;
                        JOptionPane.showMessageDialog(frame, "Driver login successful. Welcome, " + driver.name + "!");
                        frame.dispose();
                        driverMenuGUI();
                    } else {
                        JOptionPane.showMessageDialog(frame, "Invalid password.");
                    }
                } else {
                    JOptionPane.showMessageDialog(frame, "Invalid username.");
                }
            }
        });

        backBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
                showMainMenu();
            }
        });

        frame.setVisible(true);
    }

    public static void adminLoginGUI() {
        JFrame frame = new JFrame("Admin Login");
        frame.setSize(400, 200);
        frame.setLayout(new GridLayout(4, 2));

        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField();

        JLabel passLabel = new JLabel("Password:");
        JPasswordField passField = new JPasswordField();

        JButton backBtn = new JButton("Back");
        JButton loginBtn = new JButton("Login");

        frame.add(userLabel);
        frame.add(userField);
        frame.add(passLabel);
        frame.add(passField);
        frame.add(backBtn);
        frame.add(loginBtn);

        loginBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = userField.getText();
                String password = new String(passField.getPassword());

                try {
                    String sql = "SELECT * FROM admin WHERE username = ? AND password = ?";
                    PreparedStatement pstmt = connection.prepareStatement(sql);
                    pstmt.setString(1, username);
                    pstmt.setString(2, password);
                    ResultSet rs = pstmt.executeQuery();
                    
                    if (rs.next()) {
                        isAdminLoggedIn = true;
                        JOptionPane.showMessageDialog(frame, "Admin login successful!");
                        frame.dispose();
                        adminMenuGUI();
                    } else {
                        JOptionPane.showMessageDialog(frame, "Invalid admin credentials.");
                    }
                    
                    pstmt.close();
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(frame, "Error during admin login: " + ex.getMessage());
                }
            }
        });

        backBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
                showMainMenu();
            }
        });

        frame.setVisible(true);
    }

    public static void passengerMenuGUI() {
        JFrame frame = new JFrame("Passenger Menu");
        frame.setSize(400, 300);
        frame.setLayout(new GridLayout(5, 1));

        JButton requestRideBtn = new JButton("Request a Ride");
        JButton checkStatusBtn = new JButton("Check Ride Status");
        JButton rateRideBtn = new JButton("Rate Completed Ride");
        JButton logoutBtn = new JButton("Logout");

        frame.add(requestRideBtn);
        frame.add(checkStatusBtn);
        frame.add(rateRideBtn);
        frame.add(logoutBtn);

        requestRideBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
                rideRequestGUI();
            }
        });

        checkStatusBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                checkRideStatusGUI();
            }
        });

        rateRideBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rateRideGUI();
            }
        });

        logoutBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
                showMainMenu();
            }
        });

        frame.setVisible(true);
    }

    public static void rideRequestGUI() {
        JFrame frame = new JFrame("Request a Ride");
        frame.setSize(400, 250);
        frame.setLayout(new GridLayout(4, 2));

        JLabel fromLabel = new JLabel("Current Location:");
        JTextField fromField = new JTextField();

        JLabel toLabel = new JLabel("Destination:");
        JTextField toField = new JTextField();

        JButton backBtn = new JButton("Back");
        JButton requestBtn = new JButton("Request Ride");

        frame.add(fromLabel);
        frame.add(fromField);
        frame.add(toLabel);
        frame.add(toField);
        frame.add(backBtn);
        frame.add(requestBtn);

        requestBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String currentLocation = fromField.getText();
                String destination = toField.getText();

                if (currentLocation.isEmpty() || destination.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Please enter both locations!");
                    return;
                }

                try {
                    // Create ride request
                    RideRequest newRequest = new RideRequest(savedFullName, savedNumber, currentLocation, destination, passengerId);
                    
                    // Save to database
                    String sql = "INSERT INTO rides (passenger_id, from_location, to_location, fare, status) VALUES (?, ?, ?, ?, 'PENDING')";
                    PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    pstmt.setInt(1, passengerId);
                    pstmt.setString(2, currentLocation);
                    pstmt.setString(3, destination);
                    pstmt.setInt(4, newRequest.fare);
                    pstmt.executeUpdate();
                    
                    // Get generated ID
                    ResultSet generatedKeys = pstmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        newRequest.id = generatedKeys.getInt(1);
                    }
                    
                    pendingRideRequests.add(newRequest);
                    
                    JOptionPane.showMessageDialog(frame, 
                        "Ride requested!\n\nFrom: " + currentLocation + 
                        "\nTo: " + destination + 
                        "\nEstimated Fare: ₱" + newRequest.fare +
                        "\n\nStatus: PENDING\nWaiting for driver to accept...");

                    frame.dispose();
                    passengerMenuGUI();
                    
                    pstmt.close();
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(frame, "Error requesting ride: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });

        backBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
                passengerMenuGUI();
            }
        });

        frame.setVisible(true);
    }

    public static void checkRideStatusGUI() {
        StringBuilder sb = new StringBuilder();
        
        // Check if there are any rides for this passenger
        boolean found = false;
        for (RideRequest request : pendingRideRequests) {
            if (request.passengerNumber.equals(savedNumber)) {
                sb.append("Pending Request:\n");
                sb.append("From: ").append(request.from).append("\n");
                sb.append("To: ").append(request.to).append("\n");
                sb.append("Status: ").append(request.status).append("\n");
                sb.append("Estimated Fare: ₱").append(request.fare).append("\n\n");
                found = true;
            }
        }
        
        for (RideRequest request : activeRides) {
            if (request.passengerNumber.equals(savedNumber)) {
                sb.append("Active Ride:\n");
                sb.append("From: ").append(request.from).append("\n");
                sb.append("To: ").append(request.to).append("\n");
                sb.append("Status: ").append(request.status).append("\n");
                sb.append("Driver: ").append(request.driverAssigned).append("\n");
                sb.append("Fare: ₱").append(request.fare).append("\n\n");
                found = true;
            }
        }
        
        // Check for rides pending rating
        if (ridesPendingRating.containsKey(savedNumber)) {
            RideRequest ride = ridesPendingRating.get(savedNumber);
            sb.append("Ride Ready for Rating:\n");
            sb.append("From: ").append(ride.from).append("\n");
            sb.append("To: ").append(ride.to).append("\n");
            sb.append("Driver: ").append(ride.driverAssigned).append("\n");
            sb.append("Fare: ₱").append(ride.fare).append("\n");
            sb.append("Please rate this ride in the 'Rate Completed Ride' menu!\n\n");
            found = true;
        }
        
        if (!found) {
            sb.append("No active or pending rides found.");
        }
        
        JOptionPane.showMessageDialog(null, sb.toString(), "Ride Status", JOptionPane.INFORMATION_MESSAGE);
    }
    
    public static void rateRideGUI() {
        if (!ridesPendingRating.containsKey(savedNumber)) {
            JOptionPane.showMessageDialog(null, "No rides pending rating.");
            return;
        }
        
        RideRequest ride = ridesPendingRating.get(savedNumber);
        
        JFrame frame = new JFrame("Rate Your Driver");
        frame.setSize(400, 300);
        frame.setLayout(new GridLayout(7, 1));
        
        JLabel titleLabel = new JLabel("Rate Your Ride Experience", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        
        JLabel rideLabel = new JLabel("Ride: " + ride.from + " → " + ride.to, SwingConstants.CENTER);
        JLabel driverLabel = new JLabel("Driver: " + ride.driverAssigned, SwingConstants.CENTER);
        JLabel fareLabel = new JLabel("Total Fare: ₱" + ride.fare, SwingConstants.CENTER);
        fareLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        JPanel ratingPanel = new JPanel();
        ratingPanel.setLayout(new FlowLayout());
        JLabel ratingLabel = new JLabel("Rating (1-5 stars):");
        ButtonGroup ratingGroup = new ButtonGroup();
        JRadioButton[] stars = new JRadioButton[5];
        
        for (int i = 0; i < 5; i++) {
            stars[i] = new JRadioButton(String.valueOf(i + 1));
            ratingGroup.add(stars[i]);
            ratingPanel.add(stars[i]);
        }
        stars[4].setSelected(true); // Default to 5 stars
        
        JButton submitBtn = new JButton("Submit Rating");
        JButton cancelBtn = new JButton("Cancel");
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2));
        buttonPanel.add(submitBtn);
        buttonPanel.add(cancelBtn);
        
        frame.add(titleLabel);
        frame.add(rideLabel);
        frame.add(driverLabel);
        frame.add(fareLabel);
        frame.add(ratingLabel);
        frame.add(ratingPanel);
        frame.add(buttonPanel);
        
        submitBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int rating = 0;
                for (int i = 0; i < 5; i++) {
                    if (stars[i].isSelected()) {
                        rating = i + 1;
                        break;
                    }
                }
                
                if (rating > 0) {
                    ride.rating = rating;
                    ride.status = "COMPLETED";
                    
                    // Update in database
                    rateRideInDB(ride);
                    
                    // Update driver earnings and rating
                    Driver driver = null;
                    for (Driver d : drivers.values()) {
                        if (d.name.equals(ride.driverAssigned)) {
                            driver = d;
                            break;
                        }
                    }
                    
                    if (driver != null) {
                        driver.addRideEarnings(ride.fare);
                        driver.addRating(rating);
                        driver.isAvailable = true; // Make driver available again
                        
                        // Update driver availability in database
                        try {
                            String sql = "UPDATE drivers SET is_available = TRUE WHERE id = ?";
                            PreparedStatement pstmt = connection.prepareStatement(sql);
                            pstmt.setInt(1, driver.id);
                            pstmt.executeUpdate();
                            pstmt.close();
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    }
                    
                    // Add to ride history
                    rideHistory.add(ride.toString());
                    
                    // Remove from active rides and pending rating
                    activeRides.remove(ride);
                    ridesPendingRating.remove(savedNumber);
                    
                    JOptionPane.showMessageDialog(frame, 
                        "Thank you for your rating!\n" +
                        "You rated: " + rating + " stars\n" +
                        "Driver: " + ride.driverAssigned + "\n" +
                        "Fare: ₱" + ride.fare);
                    
                    frame.dispose();
                }
            }
        });
        
        cancelBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
            }
        });
        
        frame.setVisible(true);
    }

    public static void driverMenuGUI() {
        JFrame frame = new JFrame("Driver Menu");
        frame.setSize(400, 350);
        frame.setLayout(new GridLayout(6, 1));

        Driver driver = drivers.get(currentDriverLoggedIn);
        JLabel statusLabel = new JLabel("Status: " + (driver.isAvailable ? "AVAILABLE" : "ON RIDE"), SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusLabel.setForeground(driver.isAvailable ? Color.GREEN : Color.RED);
        
        JLabel earningsLabel = new JLabel("Total Earnings: ₱" + String.format("%.2f", driver.totalEarnings), SwingConstants.CENTER);
        earningsLabel.setFont(new Font("Arial", Font.BOLD, 12));

        JButton viewRequestsBtn = new JButton("View Ride Requests");
        JButton toggleStatusBtn = new JButton(driver.isAvailable ? "Go Offline" : "Go Online");
        JButton viewEarningsBtn = new JButton("View Earnings & Ratings");
        JButton completeRideBtn = new JButton("Complete Current Ride");
        JButton logoutBtn = new JButton("Logout");

        frame.add(statusLabel);
        frame.add(earningsLabel);
        frame.add(viewRequestsBtn);
        frame.add(toggleStatusBtn);
        frame.add(completeRideBtn);
        frame.add(viewEarningsBtn);
        frame.add(logoutBtn);

        viewRequestsBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!driver.isAvailable) {
                    JOptionPane.showMessageDialog(frame, "You must be available to view requests!");
                    return;
                }
                viewDriverRequestsGUI();
            }
        });

        toggleStatusBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                driver.isAvailable = !driver.isAvailable;
                statusLabel.setText("Status: " + (driver.isAvailable ? "AVAILABLE" : "ON RIDE"));
                statusLabel.setForeground(driver.isAvailable ? Color.GREEN : Color.RED);
                toggleStatusBtn.setText(driver.isAvailable ? "Go Offline" : "Go Online");
                JOptionPane.showMessageDialog(frame, driver.isAvailable ? "You are now available!" : "You are now offline.");
                
                // Update driver availability in database
                try {
                    String sql = "UPDATE drivers SET is_available = ? WHERE id = ?";
                    PreparedStatement pstmt = connection.prepareStatement(sql);
                    pstmt.setBoolean(1, driver.isAvailable);
                    pstmt.setInt(2, driver.id);
                    pstmt.executeUpdate();
                    pstmt.close();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        });

        completeRideBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                completeCurrentRideGUI();
            }
        });

        viewEarningsBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                viewDriverEarningsGUI();
            }
        });

        logoutBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                currentDriverLoggedIn = null;
                frame.dispose();
                showMainMenu();
            }
        });

        frame.setVisible(true);
    }
    
    public static void completeCurrentRideGUI() {
        // Find active ride for this driver
        RideRequest currentRide = null;
        for (RideRequest ride : activeRides) {
            if (ride.driverAssigned != null && 
                ride.driverAssigned.equals(drivers.get(currentDriverLoggedIn).name)) {
                currentRide = ride;
                break;
            }
        }
        
        if (currentRide == null) {
            JOptionPane.showMessageDialog(null, "No active ride to complete.");
            return;
        }
        
        // Update in database
        completeRideInDB(currentRide);
        
        // Mark ride as ready for rating
        ridesPendingRating.put(currentRide.passengerNumber, currentRide);
        
        JOptionPane.showMessageDialog(null,
            "Ride completed!\n\n" +
            "Passenger: " + currentRide.passengerName + "\n" +
            "From: " + currentRide.from + "\n" +
            "To: " + currentRide.to + "\n" +
            "Fare: ₱" + currentRide.fare + "\n\n" +
            "Waiting for passenger rating...");
    }
    
    public static void viewDriverEarningsGUI() {
        Driver driver = drivers.get(currentDriverLoggedIn);
        
        JFrame frame = new JFrame("Driver Earnings & Ratings");
        frame.setSize(500, 300);
        frame.setLayout(new BorderLayout());
        
        JPanel summaryPanel = new JPanel();
        summaryPanel.setLayout(new GridLayout(6, 1));
        summaryPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel titleLabel = new JLabel("Earnings Summary - " + driver.name, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        
        JLabel earningsLabel = new JLabel("Total Earnings: ₱" + String.format("%.2f", driver.totalEarnings));
        earningsLabel.setFont(new Font("Arial", Font.BOLD, 14));
        earningsLabel.setForeground(Color.BLUE);
        
        JLabel ridesLabel = new JLabel("Total Rides Completed: " + driver.totalRides);
        JLabel avgRatingLabel = new JLabel("Average Rating: " + String.format("%.1f", driver.averageRating) + " / 5");
        
        // Add star rating visual
        JPanel starPanel = new JPanel();
        int fullStars = (int) driver.averageRating;
        for (int i = 0; i < 5; i++) {
            JLabel star = new JLabel("★");
            star.setFont(new Font("Arial", Font.BOLD, 20));
            star.setForeground(i < fullStars ? Color.YELLOW : Color.GRAY);
            starPanel.add(star);
        }
        
        summaryPanel.add(titleLabel);
        summaryPanel.add(earningsLabel);
        summaryPanel.add(ridesLabel);
        summaryPanel.add(avgRatingLabel);
        summaryPanel.add(starPanel);
        
        JButton closeBtn = new JButton("Close");
        
        frame.add(summaryPanel, BorderLayout.CENTER);
        frame.add(closeBtn, BorderLayout.SOUTH);
        
        closeBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
            }
        });
        
        frame.setVisible(true);
    }

    public static void viewDriverRequestsGUI() {
        if (pendingRideRequests.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No pending ride requests.");
            return;
        }

        JFrame frame = new JFrame("Pending Ride Requests");
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());

        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (int i = 0; i < pendingRideRequests.size(); i++) {
            RideRequest req = pendingRideRequests.get(i);
            listModel.addElement("Request #" + (i+1) + 
                               ": " + req.from + " → " + req.to + 
                               " (Passenger: " + req.passengerName + ")" +
                               " - Fare: ₱" + req.fare);
        }

        JList<String> requestList = new JList<>(listModel);
        requestList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(requestList);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 3));
        JButton acceptBtn = new JButton("Accept Request");
        JButton declineBtn = new JButton("Decline");
        JButton backBtn = new JButton("Back");

        buttonPanel.add(acceptBtn);
        buttonPanel.add(declineBtn);
        buttonPanel.add(backBtn);

        acceptBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selectedIndex = requestList.getSelectedIndex();
                if (selectedIndex != -1) {
                    RideRequest selectedRequest = pendingRideRequests.get(selectedIndex);
                    Driver driver = drivers.get(currentDriverLoggedIn);
                    
                    // Update in database
                    acceptRideRequestInDB(selectedRequest, driver);
                    
                    // Move request to active rides
                    selectedRequest.status = "ACCEPTED";
                    selectedRequest.driverAssigned = driver.name;
                    selectedRequest.driverId = driver.id;
                    pendingRideRequests.remove(selectedIndex);
                    activeRides.add(selectedRequest);
                    driver.isAvailable = false;
                    
                    JOptionPane.showMessageDialog(frame, 
                        "Request accepted!\n\n" +
                        "Passenger: " + selectedRequest.passengerName + "\n" +
                        "From: " + selectedRequest.from + "\n" +
                        "To: " + selectedRequest.to + "\n" +
                        "Fare: ₱" + selectedRequest.fare + "\n\n" +
                        "Please proceed to pickup location.");
                    
                    frame.dispose();
                    driverMenuGUI();
                } else {
                    JOptionPane.showMessageDialog(frame, "Please select a request first.");
                }
            }
        });

        declineBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selectedIndex = requestList.getSelectedIndex();
                if (selectedIndex != -1) {
                    pendingRideRequests.remove(selectedIndex);
                    listModel.remove(selectedIndex);
                    JOptionPane.showMessageDialog(frame, "Request declined.");
                    
                    // Remove from database
                    try {
                        RideRequest removedRequest = pendingRideRequests.get(selectedIndex);
                        String sql = "DELETE FROM rides WHERE id = ?";
                        PreparedStatement pstmt = connection.prepareStatement(sql);
                        pstmt.setInt(1, removedRequest.id);
                        pstmt.executeUpdate();
                        pstmt.close();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                } else {
                    JOptionPane.showMessageDialog(frame, "Please select a request first.");
                }
            }
        });

        backBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
            }
        });

        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(buttonPanel, BorderLayout.SOUTH);
        frame.setVisible(true);
    }

    public static void adminMenuGUI() {
        JFrame frame = new JFrame("Admin Menu");
        frame.setSize(400, 350);
        frame.setLayout(new GridLayout(6, 1));

        JButton viewBtn = new JButton("View Ride History");
        JButton deleteBtn = new JButton("Delete Ride History");
        JButton manageDriversBtn = new JButton("Manage Drivers");
        JButton viewRequestsBtn = new JButton("View All Requests");
        JButton viewDriverStatsBtn = new JButton("View Driver Statistics");
        JButton logoutBtn = new JButton("Logout");

        frame.add(viewBtn);
        frame.add(deleteBtn);
        frame.add(manageDriversBtn);
        frame.add(viewRequestsBtn);
        frame.add(viewDriverStatsBtn);
        frame.add(logoutBtn);

        viewBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                viewRideHistoryGUI();
            }
        });

        deleteBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteRideHistoryGUI();
            }
        });

        manageDriversBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                manageDriversGUI();
            }
        });

        viewRequestsBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                viewAllRequestsGUI();
            }
        });

        viewDriverStatsBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                viewDriverStatisticsGUI();
            }
        });

        logoutBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                isAdminLoggedIn = false;
                JOptionPane.showMessageDialog(frame, "Admin logged out.");
                frame.dispose();
                showMainMenu();
            }
        });

        frame.setVisible(true);
    }
    
    public static void viewDriverStatisticsGUI() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DRIVER STATISTICS ===\n\n");
        
        for (Driver driver : drivers.values()) {
            sb.append("Driver: ").append(driver.name).append("\n");
            sb.append("Vehicle: ").append(driver.vehicle).append("\n");
            sb.append("Total Rides: ").append(driver.totalRides).append("\n");
            sb.append("Total Earnings: ₱").append(String.format("%.2f", driver.totalEarnings)).append("\n");
            sb.append("Average Rating: ").append(String.format("%.1f", driver.averageRating)).append("/5\n");
            sb.append("Status: ").append(driver.isAvailable ? "Available" : "Busy").append("\n");
            sb.append("-------------------\n");
        }
        
        JOptionPane.showMessageDialog(null, sb.toString(), "Driver Statistics", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void manageDriversGUI() {
        JFrame frame = new JFrame("Manage Drivers");
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());

        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (Driver driver : drivers.values()) {
            listModel.addElement(driver.name + 
                               " - " + driver.vehicle + 
                               " - " + driver.priceRange + 
                               " (" + (driver.isAvailable ? "Available" : "Busy") + ")" +
                               " - Earnings: ₱" + String.format("%.2f", driver.totalEarnings) +
                               " - Rating: " + String.format("%.1f", driver.averageRating) + "/5");
        }

        JList<String> driverList = new JList<>(listModel);
        JScrollPane scrollPane = new JScrollPane(driverList);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 3));
        JButton addBtn = new JButton("Add Driver");
        JButton removeBtn = new JButton("Remove Driver");
        JButton backBtn = new JButton("Back");

        buttonPanel.add(addBtn);
        buttonPanel.add(removeBtn);
        buttonPanel.add(backBtn);

        addBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JPanel addPanel = new JPanel(new GridLayout(5, 2));
                addPanel.add(new JLabel("Driver Name:"));
                JTextField nameField = new JTextField();
                addPanel.add(nameField);
                
                addPanel.add(new JLabel("Vehicle:"));
                JTextField vehicleField = new JTextField();
                addPanel.add(vehicleField);
                
                addPanel.add(new JLabel("Price Range:"));
                JTextField priceField = new JTextField();
                addPanel.add(priceField);
                
                addPanel.add(new JLabel("Username:"));
                JTextField userField = new JTextField();
                addPanel.add(userField);
                
                addPanel.add(new JLabel("Password:"));
                JPasswordField passField = new JPasswordField();
                addPanel.add(passField);
                
                int result = JOptionPane.showConfirmDialog(frame, addPanel, 
                    "Add New Driver", JOptionPane.OK_CANCEL_OPTION);
                
                if (result == JOptionPane.OK_OPTION) {
                    String name = nameField.getText();
                    String vehicle = vehicleField.getText();
                    String price = priceField.getText();
                    String username = userField.getText();
                    String password = new String(passField.getPassword());
                    
                    if (!name.isEmpty() && !username.isEmpty()) {
                        try {
                            String sql = "INSERT INTO drivers (name, vehicle, price_range, username, password) VALUES (?, ?, ?, ?, ?)";
                            PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                            pstmt.setString(1, name);
                            pstmt.setString(2, vehicle);
                            pstmt.setString(3, price);
                            pstmt.setString(4, username);
                            pstmt.setString(5, password);
                            pstmt.executeUpdate();
                            
                            ResultSet generatedKeys = pstmt.getGeneratedKeys();
                            if (generatedKeys.next()) {
                                int id = generatedKeys.getInt(1);
                                Driver newDriver = new Driver(id, name, vehicle, price, username, password);
                                drivers.put(username, newDriver);
                                listModel.addElement(name + " - " + vehicle + " - " + price + " (Available)");
                                JOptionPane.showMessageDialog(frame, "Driver added successfully!");
                            }
                            pstmt.close();
                        } catch (SQLException ex) {
                            JOptionPane.showMessageDialog(frame, "Error adding driver: " + ex.getMessage());
                        }
                    }
                }
            }
        });

        removeBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selectedIndex = driverList.getSelectedIndex();
                if (selectedIndex != -1) {
                    String selectedDriver = driverList.getSelectedValue();
                    String driverName = selectedDriver.split(" - ")[0];
                    
                    // Find and remove driver
                    String driverKeyToRemove = null;
                    Driver driverToRemove = null;
                    for (Map.Entry<String, Driver> entry : drivers.entrySet()) {
                        if (entry.getValue().name.equals(driverName)) {
                            driverKeyToRemove = entry.getKey();
                            driverToRemove = entry.getValue();
                            break;
                        }
                    }
                    
                    if (driverKeyToRemove != null && driverToRemove != null) {
                        try {
                            // Delete from database
                            String sql = "DELETE FROM drivers WHERE id = ?";
                            PreparedStatement pstmt = connection.prepareStatement(sql);
                            pstmt.setInt(1, driverToRemove.id);
                            pstmt.executeUpdate();
                            pstmt.close();
                            
                            drivers.remove(driverKeyToRemove);
                            listModel.remove(selectedIndex);
                            JOptionPane.showMessageDialog(frame, "Driver removed successfully!");
                        } catch (SQLException ex) {
                            JOptionPane.showMessageDialog(frame, "Error removing driver: " + ex.getMessage());
                        }
                    }
                } else {
                    JOptionPane.showMessageDialog(frame, "Please select a driver to remove.");
                }
            }
        });

        backBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
            }
        });

        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(buttonPanel, BorderLayout.SOUTH);
        frame.setVisible(true);
    }

    public static void viewAllRequestsGUI() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== PENDING REQUESTS ===\n");
        if (pendingRideRequests.isEmpty()) {
            sb.append("No pending requests.\n");
        } else {
            for (int i = 0; i < pendingRideRequests.size(); i++) {
                RideRequest req = pendingRideRequests.get(i);
                sb.append(i+1).append(". ").append(req.toString()).append("\n");
            }
        }
        
        sb.append("\n=== ACTIVE RIDES ===\n");
        if (activeRides.isEmpty()) {
            sb.append("No active rides.\n");
        } else {
            for (int i = 0; i < activeRides.size(); i++) {
                RideRequest req = activeRides.get(i);
                sb.append(i+1).append(". ").append(req.toString()).append("\n");
            }
        }
        
        sb.append("\n=== RIDES PENDING RATING ===\n");
        if (ridesPendingRating.isEmpty()) {
            sb.append("No rides pending rating.\n");
        } else {
            int i = 1;
            for (RideRequest req : ridesPendingRating.values()) {
                sb.append(i++).append(". ").append(req.toString()).append("\n");
            }
        }
        
        JOptionPane.showMessageDialog(null, sb.toString(), "All Ride Requests", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void viewRideHistoryGUI() {
        StringBuilder sb = new StringBuilder();
        if (rideHistory.isEmpty()) {
            sb.append("No rides yet.");
        } else {
            for (int i = 0; i < rideHistory.size(); i++) {
                sb.append((i + 1) + ". " + rideHistory.get(i) + "\n");
            }
        }
        JOptionPane.showMessageDialog(null, sb.toString(), "Ride History", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void deleteRideHistoryGUI() {
        if (rideHistory.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No rides to delete.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rideHistory.size(); i++) {
            sb.append((i + 1) + ". " + rideHistory.get(i) + "\n");
        }

        String input = JOptionPane.showInputDialog("Select the ride number to delete:\n" + sb.toString());
        try {
            int rideNumber = Integer.parseInt(input);
            if (rideNumber >= 1 && rideNumber <= rideHistory.size()) {
                rideHistory.remove(rideNumber - 1);
                JOptionPane.showMessageDialog(null, "Ride history deleted.");
            } else {
                JOptionPane.showMessageDialog(null, "Invalid ride number.");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Invalid input.");
        }
    }

    // Close database connection when application exits
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (connection != null && !connection.isClosed()) {
                        connection.close();
                        System.out.println("Database connection closed.");
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }));
    }
}