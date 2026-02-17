<?php
// +------------------------------------------------------------------------+
// | Password Reset Endpoint
// | Validates reset code (email or SMS) and updates password
// +------------------------------------------------------------------------+
$response_data = array(
    'api_status' => 400,
);

$new_password = !empty($_POST['new_password']) ? $_POST['new_password'] : '';
$email = !empty($_POST['email']) ? $_POST['email'] : '';
$phone_number = !empty($_POST['phone_number']) ? $_POST['phone_number'] : '';
$code = !empty($_POST['code']) ? $_POST['code'] : '';

if (empty($new_password) || empty($code)) {
    $error_code    = 8;
    $error_message = 'new_password and code are required';
}

if (empty($error_code) && empty($email) && empty($phone_number)) {
    $error_code    = 8;
    $error_message = 'email or phone_number is required';
}

if (empty($error_code)) {
    $update = false;
    $found_user_id = 0;

    // Method 1: Web reset token (user_id_passwordhash format)
    if (function_exists('Wo_isValidPasswordResetToken') && Wo_isValidPasswordResetToken($code) === true) {
        $update = true;
    }
    if (!$update && function_exists('Wo_isValidPasswordResetToken2') && Wo_isValidPasswordResetToken2($code) === true) {
        $update = true;
    }

    // Method 2: Email + email_code match (API-generated reset code)
    if (!$update && !empty($email)) {
        $email_esc = mysqli_real_escape_string($sqlConnect, $email);
        $code_esc = mysqli_real_escape_string($sqlConnect, $code);
        $check_query = mysqli_query($sqlConnect, "SELECT `user_id` FROM " . T_USERS . " WHERE `email` = '{$email_esc}' AND `email_code` = '{$code_esc}' AND `email_code` != '' LIMIT 1");
        if ($check_query && mysqli_num_rows($check_query) > 0) {
            $row = mysqli_fetch_assoc($check_query);
            $found_user_id = (int)$row['user_id'];
            $update = true;
        }
    }

    // Method 3: Phone number + sms_code match (SMS reset)
    if (!$update && !empty($phone_number)) {
        $phone_esc = mysqli_real_escape_string($sqlConnect, $phone_number);
        $code_esc = mysqli_real_escape_string($sqlConnect, $code);
        $check_query = mysqli_query($sqlConnect, "SELECT `user_id` FROM " . T_USERS . " WHERE `phone_number` = '{$phone_esc}' AND `sms_code` = '{$code_esc}' AND `sms_code` != '' LIMIT 1");
        if ($check_query && mysqli_num_rows($check_query) > 0) {
            $row = mysqli_fetch_assoc($check_query);
            $found_user_id = (int)$row['user_id'];
            $update = true;
        }
    }

    // Method 4: Phone number + email_code match (fallback)
    if (!$update && !empty($phone_number)) {
        $phone_esc = mysqli_real_escape_string($sqlConnect, $phone_number);
        $code_esc = mysqli_real_escape_string($sqlConnect, $code);
        $check_query = mysqli_query($sqlConnect, "SELECT `user_id` FROM " . T_USERS . " WHERE `phone_number` = '{$phone_esc}' AND `email_code` = '{$code_esc}' AND `email_code` != '' LIMIT 1");
        if ($check_query && mysqli_num_rows($check_query) > 0) {
            $row = mysqli_fetch_assoc($check_query);
            $found_user_id = (int)$row['user_id'];
            $update = true;
        }
    }

    if (!$update) {
        $error_code    = 9;
        $error_message = 'Invalid reset code';
    }

    if ($update == true) {
        if (strlen($new_password) >= 6) {
            $password = password_hash($new_password, PASSWORD_DEFAULT);
            $password_esc = mysqli_real_escape_string($sqlConnect, $password);

            // Find user by stored user_id or by email/phone
            $uid = $found_user_id;
            if (empty($uid) && !empty($email)) {
                $email_esc = mysqli_real_escape_string($sqlConnect, $email);
                $user_query = mysqli_query($sqlConnect, "SELECT `user_id` FROM " . T_USERS . " WHERE `email` = '{$email_esc}' LIMIT 1");
                if ($user_query && mysqli_num_rows($user_query) > 0) {
                    $getUser = mysqli_fetch_assoc($user_query);
                    $uid = (int)$getUser['user_id'];
                }
            }
            if (empty($uid) && !empty($phone_number)) {
                $phone_esc = mysqli_real_escape_string($sqlConnect, $phone_number);
                $user_query = mysqli_query($sqlConnect, "SELECT `user_id` FROM " . T_USERS . " WHERE `phone_number` = '{$phone_esc}' LIMIT 1");
                if ($user_query && mysqli_num_rows($user_query) > 0) {
                    $getUser = mysqli_fetch_assoc($user_query);
                    $uid = (int)$getUser['user_id'];
                }
            }

            if ($uid > 0) {
                // Update password and clear reset codes
                mysqli_query($sqlConnect, "UPDATE " . T_USERS . " SET `password` = '{$password_esc}', `email_code` = '', `sms_code` = '' WHERE `user_id` = {$uid}");

                // Delete all active sessions (force re-login)
                mysqli_query($sqlConnect, "DELETE FROM " . T_APP_SESSIONS . " WHERE `user_id` = {$uid}");

                cache($uid, 'users', 'delete');
                $response_data['api_status'] = 200;
                $response_data['message'] = 'Your password was updated';
            } else {
                $error_code    = 6;
                $error_message = 'User not found';
            }
        } else {
            $error_code    = 10;
            $error_message = 'Password is too short (minimum 6 characters)';
        }
    }
}
