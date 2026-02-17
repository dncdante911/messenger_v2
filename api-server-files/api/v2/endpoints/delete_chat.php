<?php
/**
 * Delete/Clear private chat history
 * Endpoint: ?type=delete_chat
 */

// Get user_id from request
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
    // Delete messages between current user and target user (both directions)
    $stmt = $db->prepare("
        DELETE FROM Wo_Messages
        WHERE (from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?)
    ");
    $stmt->execute([$current_user_id, $target_user_id, $target_user_id, $current_user_id]);

    $deleted_count = $stmt->rowCount();

    echo json_encode([
        'api_status' => 200,
        'message' => "Chat history cleared. $deleted_count messages deleted."
    ]);
} catch (Exception $e) {
    echo json_encode([
        'api_status' => 500,
        'error_message' => 'Failed to clear chat history: ' . $e->getMessage()
    ]);
}
