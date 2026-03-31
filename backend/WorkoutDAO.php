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

if (!ensureWorkoutSessionsTable($connection)) {
    echo json_encode([
        "response" => "false",
        "message" => "Unable to initialize workout storage"
    ]);
    exit;
}

if (!isset($_POST["phpFunction"])) {
    echo json_encode([
        "response" => "false",
        "message" => "Invalid function"
    ]);
    exit;
}

switch ($_POST["phpFunction"]) {
    case "saveWorkoutSession":
        saveWorkoutSession($connection);
        break;
    case "getWorkoutHistory":
        getWorkoutHistory($connection);
        break;
    default:
        echo json_encode([
            "response" => "false",
            "message" => "Invalid function"
        ]);
        break;
}

$connection->close();

function saveWorkoutSession($connection) {
    $userId = intval($_POST["userId"] ?? 0);
    $workoutType = trim($_POST["workoutType"] ?? "");
    $durationSeconds = intval($_POST["durationSeconds"] ?? 0);
    $totalSets = intval($_POST["totalSets"] ?? 0);
    $totalReps = intval($_POST["totalReps"] ?? 0);
    $totalVolume = floatval($_POST["totalVolume"] ?? 0);
    $distanceMeters = floatval($_POST["distanceMeters"] ?? 0);
    $caloriesBurned = floatval($_POST["caloriesBurned"] ?? 0);
    $destinationLat = trim($_POST["destinationLat"] ?? "");
    $destinationLng = trim($_POST["destinationLng"] ?? "");
    $remainingDistanceMeters = floatval($_POST["remainingDistanceMeters"] ?? 0);
    $destinationReached = intval($_POST["destinationReached"] ?? 0);
    $targetDurationSeconds = intval($_POST["targetDurationSeconds"] ?? 0);
    $targetDurationReached = intval($_POST["targetDurationReached"] ?? 0);
    $routeJson = trim($_POST["routeJson"] ?? "[]");
    $setLogJson = trim($_POST["setLogJson"] ?? "[]");

    if ($userId <= 0 || $workoutType === "") {
        echo json_encode([
            "response" => "false",
            "message" => "Missing required workout data"
        ]);
        return;
    }

    if ($setLogJson === "") {
        $setLogJson = "[]";
    }

    if (!isValidWorkoutLog($setLogJson)) {
        echo json_encode([
            "response" => "false",
            "message" => "Invalid workout log format"
        ]);
        return;
    }

    if ($routeJson === "") {
        $routeJson = "[]";
    }

    if (!isValidWorkoutLog($routeJson)) {
        echo json_encode([
            "response" => "false",
            "message" => "Invalid route format"
        ]);
        return;
    }

    $destinationReached = $destinationReached > 0 ? 1 : 0;
    $targetDurationReached = $targetDurationReached > 0 ? 1 : 0;
    $targetDurationSeconds = max(0, $targetDurationSeconds);

    if (isStrengthWorkoutType($workoutType)) {
        if ($totalSets <= 0 || $setLogJson === "[]") {
            echo json_encode([
                "response" => "false",
                "message" => "Missing required workout data"
            ]);
            return;
        }
    } elseif ($durationSeconds <= 0 && $distanceMeters <= 0) {
        echo json_encode([
            "response" => "false",
            "message" => "No workout progress to save"
        ]);
        return;
    }

    if (!userExists($connection, $userId)) {
        echo json_encode([
            "response" => "false",
            "message" => "User not found"
        ]);
        return;
    }

    $insertSql = "INSERT INTO workout_sessions (
        user_id,
        workout_type,
        duration_seconds,
        total_sets,
        total_reps,
        total_volume,
        distance_meters,
        calories_burned,
        destination_lat,
        destination_lng,
        remaining_distance_meters,
        destination_reached,
        target_duration_seconds,
        target_duration_reached,
        route_json,
        set_log_json,
        created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULLIF(?, ''), NULLIF(?, ''), ?, ?, ?, ?, ?, ?, NOW())";

    $stmt = $connection->prepare($insertSql);
    $stmt->bind_param(
        "isiiidddssdiiiss",
        $userId,
        $workoutType,
        $durationSeconds,
        $totalSets,
        $totalReps,
        $totalVolume,
        $distanceMeters,
        $caloriesBurned,
        $destinationLat,
        $destinationLng,
        $remainingDistanceMeters,
        $destinationReached,
        $targetDurationSeconds,
        $targetDurationReached,
        $routeJson,
        $setLogJson
    );

    if ($stmt->execute()) {
        echo json_encode([
            "response" => "true",
            "message" => "Workout saved"
        ]);
    } else {
        echo json_encode([
            "response" => "false",
            "message" => "Failed to save workout"
        ]);
    }

    $stmt->close();
}

function getWorkoutHistory($connection) {
    $userId = intval($_POST["userId"] ?? 0);
    $limit = intval($_POST["limit"] ?? 20);
    $limit = max(1, min($limit, 50));

    if ($userId <= 0) {
        echo json_encode([
            "response" => "false",
            "message" => "Invalid user"
        ]);
        return;
    }

    if (!userExists($connection, $userId)) {
        echo json_encode([
            "response" => "false",
            "message" => "User not found"
        ]);
        return;
    }

    $stmt = $connection->prepare(
        "SELECT
            id,
            workout_type,
            duration_seconds,
            total_sets,
            total_reps,
            total_volume,
            distance_meters,
            calories_burned,
            destination_lat,
            destination_lng,
            remaining_distance_meters,
            destination_reached,
            target_duration_seconds,
            target_duration_reached,
            route_json,
            set_log_json,
            DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
         FROM workout_sessions
         WHERE user_id = ?
         ORDER BY created_at DESC, id DESC
         LIMIT ?"
    );
    $stmt->bind_param("ii", $userId, $limit);
    $stmt->execute();
    $result = $stmt->get_result();

    $sessions = [];
    while ($row = $result->fetch_assoc()) {
        $sessions[] = [
            "id" => (string)$row["id"],
            "workoutType" => $row["workout_type"],
            "durationSeconds" => (string)$row["duration_seconds"],
            "totalSets" => (string)$row["total_sets"],
            "totalReps" => (string)$row["total_reps"],
            "totalVolume" => (string)$row["total_volume"],
            "distanceMeters" => (string)$row["distance_meters"],
            "caloriesBurned" => (string)$row["calories_burned"],
            "destinationLat" => $row["destination_lat"] === null ? null : (string)$row["destination_lat"],
            "destinationLng" => $row["destination_lng"] === null ? null : (string)$row["destination_lng"],
            "remainingDistanceMeters" => (string)$row["remaining_distance_meters"],
            "destinationReached" => (string)$row["destination_reached"],
            "targetDurationSeconds" => (string)$row["target_duration_seconds"],
            "targetDurationReached" => (string)$row["target_duration_reached"],
            "routeJson" => $row["route_json"],
            "setLogJson" => $row["set_log_json"],
            "createdAt" => $row["created_at"]
        ];
    }

    echo json_encode([
        "response" => "true",
        "sessions" => $sessions
    ]);

    $stmt->close();
}

function ensureWorkoutSessionsTable($connection) {
    $sql = "CREATE TABLE IF NOT EXISTS workout_sessions (
        id INT AUTO_INCREMENT PRIMARY KEY,
        user_id INT NOT NULL,
        workout_type VARCHAR(80) NOT NULL,
        duration_seconds INT NOT NULL DEFAULT 0,
        total_sets INT NOT NULL DEFAULT 0,
        total_reps INT NOT NULL DEFAULT 0,
        total_volume DECIMAL(10,2) NOT NULL DEFAULT 0.00,
        distance_meters DECIMAL(10,2) NOT NULL DEFAULT 0.00,
        calories_burned DECIMAL(10,2) NOT NULL DEFAULT 0.00,
        destination_lat DECIMAL(11,8) NULL DEFAULT NULL,
        destination_lng DECIMAL(11,8) NULL DEFAULT NULL,
        remaining_distance_meters DECIMAL(10,2) NOT NULL DEFAULT 0.00,
        destination_reached TINYINT(1) NOT NULL DEFAULT 0,
        target_duration_seconds INT NOT NULL DEFAULT 0,
        target_duration_reached TINYINT(1) NOT NULL DEFAULT 0,
        route_json LONGTEXT NOT NULL,
        set_log_json LONGTEXT NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_workout_sessions_user_id (user_id),
        INDEX idx_workout_sessions_type (workout_type)
    )";

    if ($connection->query($sql) !== true) {
        return false;
    }

    return ensureColumnExists(
        $connection,
        "workout_sessions",
        "distance_meters",
        "DECIMAL(10,2) NOT NULL DEFAULT 0.00"
    ) && ensureColumnExists(
        $connection,
        "workout_sessions",
        "calories_burned",
        "DECIMAL(10,2) NOT NULL DEFAULT 0.00"
    ) && ensureColumnExists(
        $connection,
        "workout_sessions",
        "destination_lat",
        "DECIMAL(11,8) NULL DEFAULT NULL"
    ) && ensureColumnExists(
        $connection,
        "workout_sessions",
        "destination_lng",
        "DECIMAL(11,8) NULL DEFAULT NULL"
    ) && ensureColumnExists(
        $connection,
        "workout_sessions",
        "remaining_distance_meters",
        "DECIMAL(10,2) NOT NULL DEFAULT 0.00"
    ) && ensureColumnExists(
        $connection,
        "workout_sessions",
        "destination_reached",
        "TINYINT(1) NOT NULL DEFAULT 0"
    ) && ensureColumnExists(
        $connection,
        "workout_sessions",
        "target_duration_seconds",
        "INT NOT NULL DEFAULT 0"
    ) && ensureColumnExists(
        $connection,
        "workout_sessions",
        "target_duration_reached",
        "TINYINT(1) NOT NULL DEFAULT 0"
    ) && ensureColumnExists(
        $connection,
        "workout_sessions",
        "route_json",
        "LONGTEXT NULL"
    );
}

function ensureColumnExists($connection, $table, $column, $definition) {
    $tableName = $connection->real_escape_string($table);
    $columnName = $connection->real_escape_string($column);
    $result = $connection->query("SHOW COLUMNS FROM `$tableName` LIKE '$columnName'");

    if ($result && $result->num_rows > 0) {
        $result->free();
        return true;
    }

    if ($result) {
        $result->free();
    }

    return $connection->query(
        "ALTER TABLE `$tableName` ADD COLUMN `$columnName` $definition"
    ) === true;
}

function isValidWorkoutLog($rawJson) {
    json_decode($rawJson, true);
    return json_last_error() === JSON_ERROR_NONE;
}

function isStrengthWorkoutType($workoutType) {
    return $workoutType === "weight_lifting";
}

function userExists($connection, $userId) {
    $userCheck = $connection->prepare("SELECT id FROM users WHERE id = ? LIMIT 1");
    $userCheck->bind_param("i", $userId);
    $userCheck->execute();
    $userResult = $userCheck->get_result();
    $exists = $userResult->fetch_assoc() ? true : false;
    $userCheck->close();
    return $exists;
}
?>
