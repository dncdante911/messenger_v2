<?php
// Update FCM token for push notifications
$response_data = array('api_status' => 400);

$fcm_token = !empty($_POST['fcm_token']) ? trim($_POST['fcm_token']) : '';
$device_type = !empty($_POST['device_type']) ? trim($_POST['device_type']) : 'android';

if (empty($fcm_token)) {
    $error_code = 1;
    $error_message = 'fcm_token is required';
} else {
    $uid = $wo['user']['user_id'];
    $fcm_esc = mysqli_real_escape_string($sqlConnect, $fcm_token);
    $device_esc = mysqli_real_escape_string($sqlConnect, $device_type);

    // Store FCM token in user's device_id field based on device type
    if ($device_type === 'android') {
        $update = mysqli_query($sqlConnect, "UPDATE " . T_USERS . " SET `android_m_device_id` = '{$fcm_esc}' WHERE `user_id` = {$uid}");
    } else {
        $update = mysqli_query($sqlConnect, "UPDATE " . T_USERS . " SET `ios_m_device_id` = '{$fcm_esc}' WHERE `user_id` = {$uid}");
    }

    if ($update) {
        $response_data = array(
            'api_status' => 200,
            'message' => 'FCM token updated successfully'
        );
    } else {
        $error_code = 2;
        $error_message = 'Failed to update FCM token';
    }
}
