<?php
// API endpoint для перевірки кодів верифікації

$json_error_data = array();
$json_success_data = array();

// Перевіряємо обов'язкові параметри
$verification_type = (!empty($_POST['verification_type'])) ? Wo_Secure($_POST['verification_type']) : '';
$contact_info = (!empty($_POST['contact_info'])) ? Wo_Secure($_POST['contact_info']) : '';
$code = (!empty($_POST['code'])) ? Wo_Secure($_POST['code']) : '';
$username = (!empty($_POST['username'])) ? Wo_Secure($_POST['username']) : '';

if (empty($code)) {
    $json_error_data = array(
        'api_status' => '400',
        'errors' => array(
            'error_id' => '1',
            'error_text' => 'Verification code is required'
        )
    );
} else if (empty($username)) {
    $json_error_data = array(
        'api_status' => '400',
        'errors' => array(
            'error_id' => '2',
            'error_text' => 'username is required'
        )
    );
} else if (!is_numeric($code) || strlen($code) != 6) {
    $json_error_data = array(
        'api_status' => '400',
        'errors' => array(
            'error_id' => '3',
            'error_text' => 'Invalid code format. Code must be 6 digits'
        )
    );
} else {
    // Отримуємо user_id по username
    $user_id = Wo_UserIdFromUsername($username);

    if (!$user_id || $user_id <= 0) {
        $json_error_data = array(
            'api_status' => '400',
            'errors' => array(
                'error_id' => '4',
                'error_text' => 'User not found'
            )
        );
    } else {
        // Перевіряємо код в базі даних
        $query = mysqli_query($sqlConnect, "SELECT `sms_code`, `active` FROM " . T_USERS . " WHERE `user_id` = '{$user_id}'");
        $user_data = mysqli_fetch_assoc($query);

        if (!$user_data) {
            $json_error_data = array(
                'api_status' => '400',
                'errors' => array(
                    'error_id' => '5',
                    'error_text' => 'User not found'
                )
            );
        } else if ($user_data['sms_code'] != $code) {
            $json_error_data = array(
                'api_status' => '400',
                'errors' => array(
                    'error_id' => '6',
                    'error_text' => 'Invalid verification code'
                )
            );
        } else {
            // Код правильний! Активуємо акаунт
            $new_email_code = md5(rand(1111, 9999) . time());
            $update_query = mysqli_query($sqlConnect, "UPDATE " . T_USERS . " SET `active` = '1', `email_code` = '{$new_email_code}', `sms_code` = '0' WHERE `user_id` = '{$user_id}'");

            if ($update_query) {
                // Отримуємо оновлені дані користувача
                $user_data = Wo_UserData($user_id);

                $json_success_data = array(
                    'api_status' => '200',
                    'message' => 'Account verified successfully',
                    'verified' => true,
                    'user_id' => $user_id,
                    'username' => $user_data['username'],
                    'avatar' => $user_data['avatar']
                );
            } else {
                $json_error_data = array(
                    'api_status' => '400',
                    'errors' => array(
                        'error_id' => '7',
                        'error_text' => 'Failed to activate account'
                    )
                );
            }
        }
    }
}

// Відправляємо відповідь
header("Content-type: application/json");
if (!empty($json_error_data)) {
    echo json_encode($json_error_data, JSON_PRETTY_PRINT);
} else {
    echo json_encode($json_success_data, JSON_PRETTY_PRINT);
}
exit();
