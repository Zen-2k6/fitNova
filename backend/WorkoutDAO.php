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

if (!validateWorkoutStorage($connection, $schemaMessage)) {
    echo json_encode([
        "response" => "false",
        "message" => $schemaMessage
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
    case "saveMonthlyGoal":
        saveMonthlyGoal($connection);
        break;
    case "getMonthlyGoal":
        getMonthlyGoal($connection);
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
    $routeName = trim($_POST["routeName"] ?? "");
    $distanceGoalMeters = floatval($_POST["distanceGoalMeters"] ?? 0);
    $caloriesGoal = floatval($_POST["caloriesGoal"] ?? 0);
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
        route_name,
        distance_goal_meters,
        calories_goal,
        route_json,
        set_log_json,
        created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULLIF(?, ''), NULLIF(?, ''), ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

    $stmt = $connection->prepare($insertSql);
    $stmt->bind_param(
        "isiiidddssdiiisddss",
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
        $routeName,
        $distanceGoalMeters,
        $caloriesGoal,
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
    $limit = max(1, min($limit, 250));

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
            route_name,
            distance_goal_meters,
            calories_goal,
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
            "routeName" => $row["route_name"] ?? "",
            "distanceGoalMeters" => (string)$row["distance_goal_meters"],
            "caloriesGoal" => (string)$row["calories_goal"],
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

function saveMonthlyGoal($connection) {
    $userId = intval($_POST["userId"] ?? 0);
    $workoutType = trim($_POST["workoutType"] ?? "");
    $goalMonth = trim($_POST["goalMonth"] ?? "");
    $distanceGoalKm = floatval($_POST["distanceGoalKm"] ?? 0);
    $caloriesGoalKcal = floatval($_POST["caloriesGoalKcal"] ?? 0);

    if ($userId <= 0 || $workoutType === "" || !preg_match('/^\d{4}-\d{2}$/', $goalMonth)) {
        echo json_encode([
            "response" => "false",
            "message" => "Invalid monthly goal data"
        ]);
        return;
    }

    if ($distanceGoalKm <= 0 && $caloriesGoalKcal <= 0) {
        echo json_encode([
            "response" => "false",
            "message" => "Enter at least one monthly goal"
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

    $sql = "INSERT INTO monthly_fitness_goals (
        user_id,
        workout_type,
        goal_month,
        distance_goal_km,
        calories_goal_kcal,
        created_at,
        updated_at
    ) VALUES (?, ?, ?, ?, ?, NOW(), NOW())
    ON DUPLICATE KEY UPDATE
        distance_goal_km = VALUES(distance_goal_km),
        calories_goal_kcal = VALUES(calories_goal_kcal),
        updated_at = NOW()";

    $stmt = $connection->prepare($sql);
    if (!$stmt) {
        echo json_encode([
            "response" => "false",
            "message" => "Failed to prepare monthly goal save"
        ]);
        return;
    }

    $stmt->bind_param(
        "issdd",
        $userId,
        $workoutType,
        $goalMonth,
        $distanceGoalKm,
        $caloriesGoalKcal
    );

    if ($stmt->execute()) {
        echo json_encode([
            "response" => "true",
            "message" => "Monthly goal saved"
        ]);
    } else {
        echo json_encode([
            "response" => "false",
            "message" => "Failed to save monthly goal"
        ]);
    }

    $stmt->close();
}

function getMonthlyGoal($connection) {
    $userId = intval($_POST["userId"] ?? 0);
    $workoutType = trim($_POST["workoutType"] ?? "");
    $goalMonth = trim($_POST["goalMonth"] ?? "");

    if ($userId <= 0 || $workoutType === "" || !preg_match('/^\d{4}-\d{2}$/', $goalMonth)) {
        echo json_encode([
            "response" => "false",
            "message" => "Invalid monthly goal request"
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
            distance_goal_km,
            calories_goal_kcal
         FROM monthly_fitness_goals
         WHERE user_id = ? AND workout_type = ? AND goal_month = ?
         LIMIT 1"
    );
    if (!$stmt) {
        echo json_encode([
            "response" => "false",
            "message" => "Failed to prepare monthly goal load"
        ]);
        return;
    }

    $stmt->bind_param("iss", $userId, $workoutType, $goalMonth);
    $stmt->execute();
    $result = $stmt->get_result();
    $row = $result->fetch_assoc();

    echo json_encode([
        "response" => "true",
        "distanceGoalKm" => $row ? (string)$row["distance_goal_km"] : "0",
        "caloriesGoalKcal" => $row ? (string)$row["calories_goal_kcal"] : "0"
    ]);

    $stmt->close();
}

function validateWorkoutStorage($connection, &$message) {
    $requiredSchema = [
        "workout_sessions" => [
            "id",
            "user_id",
            "workout_type",
            "duration_seconds",
            "total_sets",
            "total_reps",
            "total_volume",
            "distance_meters",
            "calories_burned",
            "destination_lat",
            "destination_lng",
            "remaining_distance_meters",
            "destination_reached",
            "target_duration_seconds",
            "target_duration_reached",
            "route_name",
            "distance_goal_meters",
            "calories_goal",
            "route_json",
            "set_log_json",
            "created_at"
        ],
        "monthly_fitness_goals" => [
            "id",
            "user_id",
            "workout_type",
            "goal_month",
            "distance_goal_km",
            "calories_goal_kcal",
            "created_at",
            "updated_at"
        ]
    ];

    foreach ($requiredSchema as $tableName => $requiredColumns) {
        $result = $connection->query("SHOW COLUMNS FROM `$tableName`");
        if (!$result) {
            $message = "Missing database table `$tableName`. Create the schema manually from README.md.";
            return false;
        }

        $existingColumns = [];
        while ($row = $result->fetch_assoc()) {
            $existingColumns[] = $row["Field"];
        }
        $result->free();

        foreach ($requiredColumns as $columnName) {
            if (!in_array($columnName, $existingColumns, true)) {
                $message = "Missing column `$tableName.$columnName`. Update the schema manually from README.md.";
                return false;
            }
        }
    }

    $message = "";
    return true;
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
