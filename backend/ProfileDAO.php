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

$function = $_POST["phpFunction"] ?? "";

switch ($function) {
    case "getProfile":
        getProfile($connection);
        break;
    case "updateProfile":
        updateProfile($connection);
        break;
    default:
        echo json_encode([
            "response" => "false",
            "message" => "Invalid function"
        ]);
        break;
}

$connection->close();

function getProfile($connection) {
    $userId = intval($_POST["userId"] ?? 0);
    if ($userId <= 0) {
        echo json_encode([
            "response" => "false",
            "message" => "Invalid user"
        ]);
        return;
    }

    $stmt = $connection->prepare(
        "SELECT fullname, email, gender, age, weight, height FROM users WHERE id = ? LIMIT 1"
    );
    $stmt->bind_param("i", $userId);
    $stmt->execute();
    $result = $stmt->get_result();

    if ($row = $result->fetch_assoc()) {
        echo json_encode([
            "response" => "true",
            "FullName" => $row["fullname"],
            "Email" => $row["email"],
            "Gender" => $row["gender"],
            "Age" => (string)$row["age"],
            "Weight" => (string)$row["weight"],
            "Height" => (string)$row["height"]
        ]);
    } else {
        echo json_encode([
            "response" => "false",
            "message" => "Profile not found"
        ]);
    }

    $stmt->close();
}

function updateProfile($connection) {
    $userId = intval($_POST["userId"] ?? 0);
    $fullName = trim($_POST["fullName"] ?? "");
    $email = trim($_POST["email"] ?? "");
    $gender = trim($_POST["gender"] ?? "");
    $age = intval($_POST["age"] ?? 0);
    $weight = intval($_POST["weight"] ?? 0);
    $height = intval($_POST["height"] ?? 0);

    if (
        $userId <= 0 ||
        $fullName === "" ||
        $email === "" ||
        $gender === "" ||
        $age <= 0 ||
        $weight <= 0 ||
        $height <= 0
    ) {
        echo json_encode([
            "response" => "false",
            "message" => "All profile fields are required"
        ]);
        return;
    }

    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        echo json_encode([
            "response" => "false",
            "message" => "Invalid email format"
        ]);
        return;
    }

    $emailCheck = $connection->prepare(
        "SELECT id FROM users WHERE email = ? AND id <> ? LIMIT 1"
    );
    $emailCheck->bind_param("si", $email, $userId);
    $emailCheck->execute();
    $emailResult = $emailCheck->get_result();

    if ($emailResult->fetch_assoc()) {
        $emailCheck->close();
        echo json_encode([
            "response" => "false",
            "message" => "Email already exists"
        ]);
        return;
    }
    $emailCheck->close();

    $stmt = $connection->prepare(
        "UPDATE users
         SET fullname = ?, email = ?, gender = ?, age = ?, weight = ?, height = ?
         WHERE id = ?"
    );
    $stmt->bind_param("sssiiii", $fullName, $email, $gender, $age, $weight, $height, $userId);

    if ($stmt->execute()) {
        echo json_encode([
            "response" => "true",
            "message" => "Profile updated"
        ]);
    } else {
        echo json_encode([
            "response" => "false",
            "message" => "Failed to update profile"
        ]);
    }

    $stmt->close();
}
?>
