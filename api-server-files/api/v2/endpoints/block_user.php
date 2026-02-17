<?php
/**
 * Block a user
 * Endpoint: ?type=block_user
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

if ($current_user_id == $target_user_id) {
    echo json_encode([
        'api_status' => 400,
        'error_message' => 'Cannot block yourself'
    ]);
    exit;
}

try {
    // Check if already blocked
    $stmt = $db->prepare("SELECT id FROM Wo_Blocks WHERE user_id = ? AND blocked = ?");
    $stmt->execute([$current_user_id, $target_user_id]);

    if ($stmt->fetch()) {
        echo json_encode([
            'api_status' => 200,
            'message' => 'User is already blocked'
        ]);
        exit;
    }

    // Add block
    $stmt = $db->prepare("INSERT INTO Wo_Blocks (user_id, blocked, time) VALUES (?, ?, ?)");
    $stmt->execute([$current_user_id, $target_user_id, time()]);

    // Also remove from followers/following if exists
    $db->prepare("DELETE FROM Wo_Followers WHERE follower_id = ? AND following_id = ?")->execute([$current_user_id, $target_user_id]);
    $db->prepare("DELETE FROM Wo_Followers WHERE follower_id = ? AND following_id = ?")->execute([$target_user_id, $current_user_id]);

    echo json_encode([
        'api_status' => 200,
        'message' => 'User blocked successfully'
    ]);
} catch (Exception $e) {
    echo json_encode([
        'api_status' => 500,
        'error_message' => 'Failed to block user: ' . $e->getMessage()
    ]);
}
