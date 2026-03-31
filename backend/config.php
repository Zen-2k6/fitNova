<?php
//MySQL connection parameters
$servername = "localhost";
$username = "root";  // Default username for phpMyAdmin is " root "
$password = "";      // Default is empty in XAMPP " "
$dbname = "fitnova";
// Create connection
$connection = new mysqli($servername, $username, $password, $dbname);

// Check connection
if ($connection->connect_error) {
    echo $connection->connect_error;
}
//else{ echo "Connection success.";}
?>
