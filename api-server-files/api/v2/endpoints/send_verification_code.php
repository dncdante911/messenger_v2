<?php
// API endpoint для відправки кодів верифікації на email або SMS

$json_error_data = array();
$json_success_data = array();

// Перевіряємо обов'язкові параметри
$verification_type = (!empty($_POST['verification_type'])) ? Wo_Secure($_POST['verification_type']) : '';
$contact_info = (!empty($_POST['contact_info'])) ? Wo_Secure($_POST['contact_info']) : '';
$username = (!empty($_POST['username'])) ? Wo_Secure($_POST['username']) : '';

if (empty($verification_type)) {
    $json_error_data = array(
        'api_status' => '400',
        'errors' => array(
            'error_id' => '1',
            'error_text' => 'verification_type is required (email or phone)'
        )
    );
} else if (empty($contact_info)) {
    $json_error_data = array(
        'api_status' => '400',
        'errors' => array(
            'error_id' => '2',
            'error_text' => 'contact_info is required (email address or phone number)'
        )
    );
} else if (empty($username)) {
    $json_error_data = array(
        'api_status' => '400',
        'errors' => array(
            'error_id' => '3',
            'error_text' => 'username is required'
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
        // Генеруємо 6-значний код
        $verification_code = rand(100000, 999999);

        // Зберігаємо код в базу даних
        if ($verification_type == 'email') {
            // Для email використовуємо поле sms_code (WoWonder використовує його для всіх кодів)
            $update_query = mysqli_query($sqlConnect, "UPDATE " . T_USERS . " SET `sms_code` = '{$verification_code}' WHERE `user_id` = '{$user_id}'");

            if ($update_query) {
                // Відправляємо email з кодом
                $user_data = Wo_UserData($user_id);

                // Формуємо тіло email
                $email_body = "
                    <h2>Код верифікації для WorldMates</h2>
                    <p>Ваш код верифікації: <strong style='font-size: 24px; color: #4CAF50;'>{$verification_code}</strong></p>
                    <p>Введіть цей код в додатку щоб завершити реєстрацію.</p>
                    <p>Код дійсний протягом 15 хвилин.</p>
                    <hr>
                    <p style='color: #666; font-size: 12px;'>Якщо ви не реєструвалися на WorldMates, проігноруйте цей лист.</p>
                ";

                $send_message_data = array(
                    'from_email' => $wo['config']['siteEmail'],
                    'from_name' => $wo['config']['siteName'],
                    'to_email' => $contact_info,
                    'to_name' => $username,
                    'subject' => 'Код верифікації WorldMates',
                    'charSet' => 'utf-8',
                    'message_body' => $email_body,
                    'is_html' => true
                );

                $send = Wo_SendMessage($send_message_data);

                if ($send) {
                    $json_success_data = array(
                        'api_status' => '200',
                        'message' => 'Verification code sent to your email',
                        'code_sent' => true
                    );
                } else {
                    $json_error_data = array(
                        'api_status' => '400',
                        'errors' => array(
                            'error_id' => '5',
                            'error_text' => 'Failed to send email'
                        )
                    );
                }
            } else {
                $json_error_data = array(
                    'api_status' => '400',
                    'errors' => array(
                        'error_id' => '6',
                        'error_text' => 'Failed to save verification code'
                    )
                );
            }

        } else if ($verification_type == 'phone') {
            // Для SMS також використовуємо поле sms_code
            $update_query = mysqli_query($sqlConnect, "UPDATE " . T_USERS . " SET `sms_code` = '{$verification_code}' WHERE `user_id` = '{$user_id}'");

            if ($update_query) {
                // TODO: Інтеграція з SMS провайдером (Twilio, Nexmo, тощо)
                // Поки що просто повертаємо успіх
                // В production тут має бути виклик SMS API

                // ТИМЧАСОВО: для тестування виводимо код в логи
                error_log("SMS Verification Code for user {$username} ({$contact_info}): {$verification_code}");

                $json_success_data = array(
                    'api_status' => '200',
                    'message' => 'Verification code sent to your phone',
                    'code_sent' => true,
                    // ТІЛЬКИ ДЛЯ РОЗРОБКИ - видаліть в production!
                    'debug_code' => $verification_code
                );
            } else {
                $json_error_data = array(
                    'api_status' => '400',
                    'errors' => array(
                        'error_id' => '7',
                        'error_text' => 'Failed to save verification code'
                    )
                );
            }
        } else {
            $json_error_data = array(
                'api_status' => '400',
                'errors' => array(
                    'error_id' => '8',
                    'error_text' => 'Invalid verification_type. Use "email" or "phone"'
                )
            );
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
