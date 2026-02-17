<?php
// +------------------------------------------------------------------------+
// | Quick Registration - Step 2: Verify Code
// | Verifies the 6-digit code, activates account, returns access_token
// +------------------------------------------------------------------------+

$response_data = array('api_status' => 400);

$email = !empty($_POST['email']) ? trim($_POST['email']) : '';
$phone_number = !empty($_POST['phone_number']) ? trim($_POST['phone_number']) : '';
$code = !empty($_POST['code']) ? trim($_POST['code']) : '';

// Validate inputs
if (empty($code)) {
    $error_code = 1;
    $error_message = 'Verification code is required';
}
if (empty($error_code) && empty($email) && empty($phone_number)) {
    $error_code = 2;
    $error_message = 'email or phone_number is required';
}
if (empty($error_code) && (!is_numeric($code) || strlen($code) != 6)) {
    $error_code = 3;
    $error_message = 'Invalid code format. Code must be 6 digits.';
}

if (empty($error_code)) {
    // Find user by email or phone
    $found_user_id = 0;

    if (!empty($email)) {
        $email_esc = mysqli_real_escape_string($sqlConnect, $email);
        $q = mysqli_query($sqlConnect, "SELECT `user_id`, `sms_code`, `active`, `username` FROM " . T_USERS . " WHERE `email` = '{$email_esc}' LIMIT 1");
        if ($q && mysqli_num_rows($q) > 0) {
            $user_row = mysqli_fetch_assoc($q);
            $found_user_id = (int)$user_row['user_id'];
        }
    }
    elseif (!empty($phone_number)) {
        $phone_esc = mysqli_real_escape_string($sqlConnect, $phone_number);
        $q = mysqli_query($sqlConnect, "SELECT `user_id`, `sms_code`, `active`, `username` FROM " . T_USERS . " WHERE `phone_number` = '{$phone_esc}' LIMIT 1");
        if ($q && mysqli_num_rows($q) > 0) {
            $user_row = mysqli_fetch_assoc($q);
            $found_user_id = (int)$user_row['user_id'];
        }
    }

    if (empty($found_user_id) || empty($user_row)) {
        $error_code = 4;
        $error_message = 'Account not found. Please register first.';
    }
    // Check if code matches
    elseif ($user_row['sms_code'] != $code) {
        $error_code = 5;
        $error_message = 'Invalid verification code. Please try again.';
    }
    else {
        // Code is correct — activate account
        $uid = $found_user_id;
        $new_email_code = md5(rand(1111, 9999) . time());
        $update = mysqli_query($sqlConnect, "UPDATE " . T_USERS . " SET `active` = '1', `email_code` = '{$new_email_code}', `sms_code` = '0' WHERE `user_id` = {$uid}");

        if (!$update) {
            $error_code = 6;
            $error_message = 'Failed to activate account. Please try again.';
        } else {
            // Create session and return access_token
            $access_token = sha1(rand(111111111, 999999999)) . md5(microtime()) . rand(11111111, 99999999) . md5(rand(5555, 9999));
            $time = time();
            $device_type = 'phone';
            if (!empty($_POST['device_type']) && in_array($_POST['device_type'], array('phone', 'windows'))) {
                $device_type = Wo_Secure($_POST['device_type']);
            }

            $session_created = mysqli_query($sqlConnect, "INSERT INTO " . T_APP_SESSIONS . " (`user_id`, `session_id`, `platform`, `time`) VALUES ('{$uid}', '{$access_token}', '{$device_type}', '{$time}')");

            // Auto-follow/auto-join if configured
            $username = $user_row['username'];
            if (!empty($wo['config']['auto_friend_users'])) {
                Wo_AutoFollow($uid);
            }
            if (!empty($wo['config']['auto_page_like'])) {
                Wo_AutoPageLike($uid);
            }
            if (!empty($wo['config']['auto_group_join'])) {
                Wo_AutoGroupJoin($uid);
            }

            cache($uid, 'users', 'delete');

            $response_data = array(
                'api_status' => 200,
                'message' => 'Account verified and activated successfully',
                'access_token' => $access_token,
                'user_id' => $uid,
                'username' => $username,
                'user_platform' => $device_type,
            );
        }
    }
}
