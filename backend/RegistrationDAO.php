<?php
// RegistrationDAO.php
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: POST");
header("Access-Control-Allow-Headers: Content-Type");

// Database configuration
$servername = "localhost";
$username = "root"; // Change this to your database username
$password = ""; // Change this to your database password
$dbname = "fitnova"; // Change this to your database name

// Create connection
$conn = new mysqli($servername, $username, $password, $dbname);

// Check connection
if ($conn->connect_error) {
    die("Connection failed: " . $conn->connect_error);
}

// Get POST data
if (isset($_POST['phpFunction'])) {
    $function = $_POST['phpFunction'];
    
    switch ($function) {
        case 'createUser':
            createUser($conn);
            break;
        default:
            echo "Invalid function";
            break;
    }
} else {
    echo "No function specified";
}

function createUser($conn) {
    // Get parameters from POST request
    $fullName = $_POST['fullName'] ?? '';
    $email = $_POST['email'] ?? '';
    $password = $_POST['password'] ?? '';
    $gender = $_POST['gender'] ?? '';
    $age = $_POST['age'] ?? '';
    $weight = $_POST['weight'] ?? '';
    $height = $_POST['height'] ?? '';
    
    // Validate input
    if (empty($fullName) || empty($email) || empty($password) || empty($gender) || empty($age) || empty($weight) || empty($height)) {
        echo "All fields are required";
        return;
    }
    
    // Validate email format
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        echo "Invalid email format";
        return;
    }
    
    // Check if email already exists
    $checkEmail = "SELECT * FROM users WHERE email = ?";
    $stmt = $conn->prepare($checkEmail);
    $stmt->bind_param("s", $email);
    $stmt->execute();
    $result = $stmt->get_result();
    
    if ($result->num_rows > 0) {
        echo "Email already exists";
        $stmt->close();
        return;
    }
    $stmt->close();
    
    // Hash the password for security
    $hashedPassword = password_hash($password, PASSWORD_DEFAULT);
    
    // Insert user into database
    $sql = "INSERT INTO users (fullname, email, password, gender, age, weight, height, created_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())";
    
    $stmt = $conn->prepare($sql);
    $stmt->bind_param("sssssss", $fullName, $email, $hashedPassword, $gender, $age, $weight, $height);
    
    if ($stmt->execute()) {
        echo "true"; // Success response
    } else {
        echo "Registration failed: " . $conn->error;
    }
    
    $stmt->close();
}

$conn->close();
?>