<?php
/**
 * Unblock a user
 * Endpoint: ?type=unblock_user
 */

$target_user_id = $_POST['user_id'] ?? $_GET['user_id'] ?? null;

if (!$target_user_id) {
    echo json_encode([
        'api_status' => 400,
        'error_message' => 'user_id is required'
    ]);
    exit;
}

$current_user_id = $wo['user']['user_id'] ?? $wo['user']['id'] ?? null;

if (!$current_user_id) {
    echo json_encode([
        'api_status' => 401,
        'error_message' => 'Not authenticated'
    ]);
    exit;
}

try {
    // Remove block
    $stmt = $db->prepare("DELETE FROM Wo_Blocks WHERE user_id = ? AND blocked = ?");
    $stmt->execute([$current_user_id, $target_user_id]);

    if ($stmt->rowCount() > 0) {
        echo json_encode([
            'api_status' => 200,
            'message' => 'User unblocked successfully'
        ]);
    } else {
        echo json_encode([
            'api_status' => 200,
            'message' => 'User was not blocked'
        ]);
    }
} catch (Exception $e) {
    echo json_encode([
        'api_status' => 500,
        'error_message' => 'Failed to unblock user: ' . $e->getMessage()
    ]);
}
