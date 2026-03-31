<?php
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: POST, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type");
header("Content-Type: application/json; charset=UTF-8");

if ($_SERVER["REQUEST_METHOD"] === "OPTIONS") {
    http_response_code(200);
    exit;
}

require_once "config.php";

if (!isset($_POST["phpFunction"]) || $_POST["phpFunction"] !== "login") {
    echo json_encode(["response" => "false"]);
    exit;
}

$email = trim($_POST["username"] ?? "");
$passwordText = trim($_POST["password"] ?? "");

if ($email === "" || $passwordText === "") {
    echo json_encode(["response" => "false"]);
    exit;
}

$sql = "SELECT id, fullname, email, gender, age, password, weight, height FROM users WHERE email = ? LIMIT 1";
$stmt = $connection->prepare($sql);
$stmt->bind_param("s", $email);
$stmt->execute();
$result = $stmt->get_result();

if ($row = $result->fetch_assoc()) {
    if (password_verify($passwordText, $row["password"])) {
        echo json_encode([
            "userid" => (string)$row["id"],
            "FullName" => $row["fullname"],
            "Email" => $row["email"],
            "Gender" => $row["gender"],
            "Age" => (string)$row["age"],
            "Weight" => (string)$row["weight"],
            "Height" => (string)$row["height"],
            "response" => "true"
        ]);
    } else {
        echo json_encode(["response" => "false"]);
    }
} else {
    echo json_encode(["response" => "false"]);
}

$stmt->close();
$connection->close();
?>
