<?php
/**
 * Delete/Clear group chat history
 * Endpoint: ?type=delete_group_chat
 */

$group_id = $_POST['group_id'] ?? $_GET['group_id'] ?? null;

if (!$group_id) {
    echo json_encode([
        'api_status' => 400,
        'error_message' => 'group_id is required'
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
    // Check if user is admin/owner of the group
    $stmt = $pdo->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$group_id, $current_user_id]);
    $role = $stmt->fetchColumn();

    if (!in_array($role, ['owner', 'admin'])) {
        echo json_encode([
            'api_status' => 403,
            'error_message' => 'Only admin or owner can clear chat history'
        ]);
        exit;
    }

    // Delete all messages in the group
    $stmt = $pdo->prepare("DELETE FROM Wo_Messages WHERE group_id = ?");
    $stmt->execute([$group_id]);

    $deleted_count = $stmt->rowCount();

    echo json_encode([
        'api_status' => 200,
        'message' => "Group chat history cleared. $deleted_count messages deleted."
    ]);
} catch (Exception $e) {
    echo json_encode([
        'api_status' => 500,
        'error_message' => 'Failed to clear group chat history: ' . $e->getMessage()
    ]);
}
