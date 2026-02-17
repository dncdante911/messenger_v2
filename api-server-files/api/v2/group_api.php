<?php
/**
 * Group Management API for WorldMates
 * Handles all group-related operations
 *
 * This file should be placed in:
 * /var/www/html/worldmates.club/api/v2/group_api.php
 *
 * And included in the main api/v2/index.php or endpoints.php
 */

// Prevent direct access
if (!defined('IN_WO_API')) {
    http_response_code(403);
    exit(json_encode(['api_status' => 403, 'errors' => ['error_text' => 'Direct access forbidden']]));
}

// Get request type
$type = isset($_GET['type']) ? Wo_Secure($_GET['type']) : '';

switch ($type) {
    case 'create_group':
        createGroup();
        break;
    case 'update_group':
        updateGroup();
        break;
    case 'delete_group':
        deleteGroup();
        break;
    case 'get_group_details':
        getGroupDetails();
        break;
    case 'get_group_members':
        getGroupMembers();
        break;
    case 'add_group_member':
        addGroupMember();
        break;
    case 'remove_group_member':
        removeGroupMember();
        break;
    case 'set_group_admin':
        setGroupRole();
        break;
    case 'leave_group':
        leaveGroup();
        break;
    default:
        http_response_code(404);
        exit(json_encode([
            'api_status' => 404,
            'errors' => [
                'error_id' => '1',
                'error_text' => "Error: 404 API Type Not Found - $type"
            ]
        ]));
}

function createGroup() {
    global $wo, $sqlConnect;

    // Validate access token
    if (empty($_POST['access_token'])) {
        return returnError('access_token is required');
    }

    $user = Wo_UserData($_POST['access_token']);
    if (empty($user)) {
        return returnError('Invalid access token');
    }

    // Validate required fields
    $name = isset($_POST['name']) ? Wo_Secure($_POST['name']) : '';
    if (empty($name) || strlen($name) < 3) {
        return returnError('Group name must be at least 3 characters');
    }

    if (strlen($name) > 255) {
        return returnError('Group name is too long (max 255 characters)');
    }

    // Optional fields
    $description = isset($_POST['description']) ? Wo_Secure($_POST['description'], 0, false) : '';
    $isPrivate = isset($_POST['is_private']) ? intval($_POST['is_private']) : 0;
    $memberIds = isset($_POST['member_ids']) ? $_POST['member_ids'] : '';

    // Create group
    $groupData = [
        'user_id' => $user['user_id'],
        'group_name' => $name,
        'description' => $description,
        'is_private' => $isPrivate ? '1' : '0',
        'time' => time()
    ];

    $groupId = Wo_RegisterGroup($groupData);

    if (!$groupId) {
        return returnError('Failed to create group');
    }

    // Add creator as owner
    mysqli_query($sqlConnect, "INSERT INTO Wo_GroupChatUsers (group_id, user_id, role, active)
                                VALUES ($groupId, {$user['user_id']}, 'owner', '1')");

    // Add members
    if (!empty($memberIds)) {
        $memberIdsArray = array_filter(array_map('intval', explode(',', $memberIds)));

        foreach ($memberIdsArray as $memberId) {
            // Check if user exists
            $memberCheck = mysqli_query($sqlConnect, "SELECT user_id FROM Wo_Users WHERE user_id = $memberId");
            if (mysqli_num_rows($memberCheck) > 0) {
                mysqli_query($sqlConnect, "INSERT INTO Wo_GroupChatUsers (group_id, user_id, role, active)
                                            VALUES ($groupId, $memberId, 'member', '1')");
            }
        }
    }

    // Get created group details
    $group = getGroupData($groupId);

    returnSuccess([
        'group_id' => $groupId,
        'group' => $group,
        'message' => 'Group created successfully'
    ]);
}

function updateGroup() {
    global $wo, $sqlConnect;

    $user = Wo_UserData($_POST['access_token']);
    if (empty($user)) {
        return returnError('Invalid access token');
    }

    $groupId = isset($_POST['group_id']) ? intval($_POST['group_id']) : 0;
    if (!$groupId) {
        return returnError('group_id is required');
    }

    // Check if user is admin or owner
    $role = getUserRole($groupId, $user['user_id']);
    if ($role != 'owner' && $role != 'admin') {
        return returnError('Only group admins can update group info');
    }

    // Build update query
    $updates = [];

    if (isset($_POST['name'])) {
        $name = Wo_Secure($_POST['name']);
        if (!empty($name)) {
            $updates[] = "group_name = '$name'";
        }
    }

    if (isset($_POST['description'])) {
        $desc = Wo_Secure($_POST['description'], 0, false);
        $updates[] = "description = '$desc'";
    }

    if (isset($_POST['avatar'])) {
        $avatar = Wo_Secure($_POST['avatar']);
        $updates[] = "avatar = '$avatar'";
    }

    // Only owner can change privacy
    if (isset($_POST['is_private']) && $role == 'owner') {
        $isPrivate = intval($_POST['is_private']);
        $updates[] = "is_private = '$isPrivate'";
    }

    if (empty($updates)) {
        return returnError('No fields to update');
    }

    $updateQuery = "UPDATE Wo_GroupChat SET " . implode(', ', $updates) . " WHERE id = $groupId";
    mysqli_query($sqlConnect, $updateQuery);

    $group = getGroupData($groupId);

    returnSuccess([
        'group' => $group,
        'message' => 'Group updated successfully'
    ]);
}

function deleteGroup() {
    global $wo, $sqlConnect;

    $user = Wo_UserData($_POST['access_token']);
    if (empty($user)) {
        return returnError('Invalid access token');
    }

    $groupId = isset($_POST['group_id']) ? intval($_POST['group_id']) : 0;
    if (!$groupId) {
        return returnError('group_id is required');
    }

    // Only owner can delete
    $role = getUserRole($groupId, $user['user_id']);
    if ($role != 'owner') {
        return returnError('Only group owner can delete the group');
    }

    // Delete group messages
    mysqli_query($sqlConnect, "DELETE FROM Wo_Messages WHERE group_id = $groupId");

    // Delete group members
    mysqli_query($sqlConnect, "DELETE FROM Wo_GroupChatUsers WHERE group_id = $groupId");

    // Delete group
    mysqli_query($sqlConnect, "DELETE FROM Wo_GroupChat WHERE id = $groupId");

    returnSuccess(['message' => 'Group deleted successfully']);
}

function addGroupMember() {
    global $wo, $sqlConnect;

    $user = Wo_UserData($_POST['access_token']);
    if (empty($user)) {
        return returnError('Invalid access token');
    }

    $groupId = isset($_POST['group_id']) ? intval($_POST['group_id']) : 0;
    $userId = isset($_POST['user_id']) ? intval($_POST['user_id']) : 0;

    if (!$groupId || !$userId) {
        return returnError('group_id and user_id are required');
    }

    // Check permissions
    $group = getGroupData($groupId);
    $userRole = getUserRole($groupId, $user['user_id']);

    if ($group['is_private'] == '1' && $userRole != 'owner' && $userRole != 'admin') {
        return returnError('Only admins can add members to private groups');
    }

    // Check if already member
    $existing = mysqli_query($sqlConnect, "SELECT * FROM Wo_GroupChatUsers
                                           WHERE group_id = $groupId AND user_id = $userId");

    if (mysqli_num_rows($existing) > 0) {
        // Reactivate if previously removed
        mysqli_query($sqlConnect, "UPDATE Wo_GroupChatUsers SET active = '1'
                                    WHERE group_id = $groupId AND user_id = $userId");
    } else {
        // Add new member
        mysqli_query($sqlConnect, "INSERT INTO Wo_GroupChatUsers (group_id, user_id, role, active)
                                    VALUES ($groupId, $userId, 'member', '1')");
    }

    returnSuccess(['message' => 'Member added successfully']);
}

function removeGroupMember() {
    global $wo, $sqlConnect;

    $user = Wo_UserData($_POST['access_token']);
    if (empty($user)) {
        return returnError('Invalid access token');
    }

    $groupId = isset($_POST['group_id']) ? intval($_POST['group_id']) : 0;
    $userId = isset($_POST['user_id']) ? intval($_POST['user_id']) : 0;

    if (!$groupId || !$userId) {
        return returnError('group_id and user_id are required');
    }

    $userRole = getUserRole($groupId, $user['user_id']);
    $targetRole = getUserRole($groupId, $userId);

    // Check permissions
    if ($userRole != 'owner' && $userRole != 'admin') {
        return returnError('Only admins can remove members');
    }

    if ($userRole == 'admin' && ($targetRole == 'admin' || $targetRole == 'owner')) {
        return returnError('Admins cannot remove other admins or owner');
    }

    mysqli_query($sqlConnect, "DELETE FROM Wo_GroupChatUsers
                                WHERE group_id = $groupId AND user_id = $userId");

    returnSuccess(['message' => 'Member removed successfully']);
}

function setGroupRole() {
    global $wo, $sqlConnect;

    $user = Wo_UserData($_POST['access_token']);
    if (empty($user)) {
        return returnError('Invalid access token');
    }

    $groupId = isset($_POST['group_id']) ? intval($_POST['group_id']) : 0;
    $userId = isset($_POST['user_id']) ? intval($_POST['user_id']) : 0;
    $role = isset($_POST['role']) ? Wo_Secure($_POST['role']) : 'member';

    if (!$groupId || !$userId) {
        return returnError('group_id and user_id are required');
    }

    // Only owner can change roles
    if (getUserRole($groupId, $user['user_id']) != 'owner') {
        return returnError('Only group owner can change member roles');
    }

    // Validate role
    if (!in_array($role, ['admin', 'moderator', 'member'])) {
        return returnError('Invalid role');
    }

    mysqli_query($sqlConnect, "UPDATE Wo_GroupChatUsers SET role = '$role'
                                WHERE group_id = $groupId AND user_id = $userId");

    returnSuccess(['message' => 'Role updated successfully']);
}

function leaveGroup() {
    global $wo, $sqlConnect;

    $user = Wo_UserData($_POST['access_token']);
    if (empty($user)) {
        return returnError('Invalid access token');
    }

    $groupId = isset($_POST['group_id']) ? intval($_POST['group_id']) : 0;
    if (!$groupId) {
        return returnError('group_id is required');
    }

    // Owner cannot leave
    if (getUserRole($groupId, $user['user_id']) == 'owner') {
        return returnError('Group owner cannot leave. Transfer ownership or delete the group.');
    }

    mysqli_query($sqlConnect, "DELETE FROM Wo_GroupChatUsers
                                WHERE group_id = $groupId AND user_id = {$user['user_id']}");

    returnSuccess(['message' => 'Successfully left the group']);
}

function getGroupDetails() {
    global $wo;

    $user = Wo_UserData($_GET['access_token']);
    if (empty($user)) {
        return returnError('Invalid access token');
    }

    $groupId = isset($_POST['group_id']) ? intval($_POST['group_id']) : 0;
    if (!$groupId) {
        return returnError('group_id is required');
    }

    $group = getGroupData($groupId);

    // Check if private and user is member
    if ($group['is_private'] == '1') {
        $isMember = getUserRole($groupId, $user['user_id']);
        if (!$isMember) {
            return returnError('This group is private');
        }
    }

    returnSuccess(['group' => $group]);
}

function getGroupMembers() {
    global $wo, $sqlConnect;

    $user = Wo_UserData($_GET['access_token']);
    if (empty($user)) {
        return returnError('Invalid access token');
    }

    $groupId = isset($_POST['group_id']) ? intval($_POST['group_id']) : 0;
    if (!$groupId) {
        return returnError('group_id is required');
    }

    $query = "SELECT u.user_id, u.username, u.name, u.avatar,
                     gcu.role, gcu.active, gcu.last_seen
              FROM Wo_GroupChatUsers gcu
              JOIN Wo_Users u ON gcu.user_id = u.user_id
              WHERE gcu.group_id = $groupId AND gcu.active = '1'
              ORDER BY FIELD(gcu.role, 'owner', 'admin', 'moderator', 'member')";

    $result = mysqli_query($sqlConnect, $query);
    $members = [];

    while ($row = mysqli_fetch_assoc($result)) {
        $members[] = $row;
    }

    returnSuccess(['members' => $members]);
}

// Helper functions
function getGroupData($groupId) {
    global $sqlConnect;

    $query = "SELECT gc.*,
                     (SELECT COUNT(*) FROM Wo_GroupChatUsers WHERE group_id = gc.id AND active = '1') as members_count
              FROM Wo_GroupChat gc
              WHERE gc.id = $groupId";

    $result = mysqli_query($sqlConnect, $query);
    return mysqli_fetch_assoc($result);
}

function getUserRole($groupId, $userId) {
    global $sqlConnect;

    $query = "SELECT role FROM Wo_GroupChatUsers
              WHERE group_id = $groupId AND user_id = $userId AND active = '1'";

    $result = mysqli_query($sqlConnect, $query);
    if ($row = mysqli_fetch_assoc($result)) {
        return $row['role'];
    }
    return false;
}

function returnSuccess($data) {
    exit(json_encode(array_merge(['api_status' => 200], $data)));
}

function returnError($message) {
    exit(json_encode([
        'api_status' => 400,
        'errors' => ['error_text' => $message]
    ]));
}