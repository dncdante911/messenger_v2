<?php
// +------------------------------------------------------------------------+
// | @author Deen Doughouz (DoughouzForest)
// | @author_url 1: http://www.wowonder.com
// | @author_url 2: http://codecanyon.net/user/doughouzforest
// | @author_email: wowondersocial@gmail.com
// +------------------------------------------------------------------------+
// | WoWonder - The Ultimate Social Networking Platform
// | Copyright (c) 2016 WoWonder. All rights reserved.
// +------------------------------------------------------------------------+

// Load Phone API initialization
require_once(__DIR__ . '/api_init.php');

$json_error_data   = array();
$json_success_data = array();
if (empty($_GET['type']) || !isset($_GET['type'])) {
    $json_error_data = array(
        'api_status' => '400',
        'api_text' => 'failed',
        'api_version' => $api_version,
        'errors' => array(
            'error_id' => '1',
            'error_text' => 'Bad request, no type specified.'
        )
    );
    header("Content-type: application/json");
    echo json_encode($json_error_data, JSON_PRETTY_PRINT);
    exit();
}
$json_error_data = array(
    'api_status' => '400',
    'api_text' => 'failed',
    'api_version' => $api_version,
    'errors' => array()
);
$type = Wo_Secure($_GET['type'], 0);
if ($type == 'user_registration') {
    if (empty($_POST['username'])) {
        $json_error_data['errors'] = array(
            'error_id' => '2',
            'error_text' => 'Please enter your username.',
            'error_key' => 'username_empty'
        );
    } else if (in_array(true, Wo_IsNameExist($_POST['username'], 0))) {
    	$json_error_data['errors'] = array(
            'error_id' => '3',
            'error_text' => 'This username is already taken. Please choose another one.',
            'error_key' => 'username_taken'
        );
    } else if (in_array($_POST['username'], $wo['site_pages'])) {
    	$json_error_data['errors'] = array(
            'error_id' => '4',
            'error_text' => 'This username is reserved. Please choose another one.',
            'error_key' => 'username_reserved'
        );
    } else if (mb_strlen($_POST['username'], 'UTF-8') < 3 OR mb_strlen($_POST['username'], 'UTF-8') > 32) {
    	$json_error_data['errors'] = array(
            'error_id' => '6',
            'error_text' => 'Username must be between 3 and 32 characters.',
            'error_key' => 'username_length'
        );
    } else if (!preg_match('/^[\w\p{Cyrillic}\p{Greek}\p{Arabic}]+$/u', $_POST['username'])) {
    	$json_error_data['errors'] = array(
            'error_id' => '7',
            'error_text' => 'Username contains invalid characters. Only letters, numbers, and underscore are allowed.',
            'error_key' => 'username_invalid'
        );
    } else if (empty($_POST['email']) && empty($_POST['phone_number'])) {
        $json_error_data['errors'] = array(
            'error_id' => '8',
            'error_text' => 'Please provide an email address or phone number.',
            'error_key' => 'contact_empty'
        );
    } else if (!empty($_POST['email']) && Wo_EmailExists($_POST['email']) === true) {
    	$json_error_data['errors'] = array(
            'error_id' => '9',
            'error_text' => 'This email is already registered. Please log in or use a different email.',
            'error_key' => 'email_taken'
        );
    } else if (!empty($_POST['email']) && !filter_var($_POST['email'], FILTER_VALIDATE_EMAIL)) {
    	$json_error_data['errors'] = array(
            'error_id' => '10',
            'error_text' => 'Invalid email format. Please enter a valid email address.',
            'error_key' => 'email_invalid'
        );
    } else if (!empty($_POST['phone_number']) && (function_exists('Wo_PhoneExists') ? Wo_PhoneExists($_POST['phone_number']) : (function_exists('Wo_IsPhoneExist') ? Wo_IsPhoneExist($_POST['phone_number']) : false))) {
        $json_error_data['errors'] = array(
            'error_id' => '16',
            'error_text' => 'This phone number is already registered. Please log in or use a different number.',
            'error_key' => 'phone_taken'
        );
    } else if (empty($_POST['password'])) {
    	$json_error_data['errors'] = array(
            'error_id' => '11',
            'error_text' => 'Please enter your password.',
            'error_key' => 'password_empty'
        );
    } else if (strlen($_POST['password']) < 6) {
    	$json_error_data['errors'] = array(
            'error_id' => '12',
            'error_text' => 'Password is too short. Minimum 6 characters required.',
            'error_key' => 'password_weak'
        );
    } else if (!preg_match('/[A-Za-z]/', $_POST['password']) || !preg_match('/[0-9]/', $_POST['password'])) {
        $json_error_data['errors'] = array(
            'error_id' => '17',
            'error_text' => 'Password must contain at least one letter and one number.',
            'error_key' => 'password_simple'
        );
    } else if (empty($_POST['confirm_password'])) {
    	$json_error_data['errors'] = array(
            'error_id' => '13',
            'error_text' => 'Please confirm your password.',
            'error_key' => 'confirm_password_empty'
        );
    } else if ($_POST['password'] != $_POST['confirm_password']) {
    	$json_error_data['errors'] = array(
            'error_id' => '14',
            'error_text' => 'Passwords do not match. Please make sure both passwords are identical.',
            'error_key' => 'password_mismatch'
        );
    } else if (empty($_POST['s'])) {
        $json_error_data['errors'] = array(
            'error_id' => '15',
            'error_text' => 'Session error. Please try again.',
            'error_key' => 'session_error'
        );
    } 
    if (empty($json_error_data['errors'])) {
        $username        = Wo_Secure($_POST['username'], 0);
        $password        = $_POST['password'];
        $email           = !empty($_POST['email']) ? Wo_Secure($_POST['email'], 0) : '';
        $phone_number    = !empty($_POST['phone_number']) ? Wo_Secure($_POST['phone_number'], 0) : '';
        $gender          = 'male';
        if (!empty($_POST['gender'])) {
        	if ($_POST['gender'] == 'female') {
        		$gender  = 'female';
        	}
        }
        // Якщо є email і включена верифікація - вимагаємо верифікацію
        // Якщо тільки телефон - автоматично активуємо
        $activate = (!empty($email) && $wo['config']['emailValidation'] == '1') ? '0' : '1';

        $re_data  = array(
            'username' => $username,
            'password' => $password,
            'email_code' => md5($username),
            'src' => 'Phone',
            'timezone' => 'UTC',
            'gender' => Wo_Secure($gender),
            'lastseen' => time(),
            'active' => Wo_Secure($activate)
        );

        // Додаємо email якщо є
        if (!empty($email)) {
            $re_data['email'] = $email;
        }

        // Додаємо phone_number якщо є
        if (!empty($phone_number)) {
            $re_data['phone_number'] = $phone_number;
        }
        if (!empty($_POST['android_m_device_id'])) {
            $re_data['android_m_device_id']  = Wo_Secure($_POST['android_m_device_id']);
        }
        if (!empty($_POST['ios_m_device_id'])) {
            $re_data['ios_m_device_id']  = Wo_Secure($_POST['ios_m_device_id']);
        }
        if (!empty($_POST['android_n_device_id'])) {
            $re_data['android_n_device_id']  = Wo_Secure($_POST['android_n_device_id']);
        }
        if (!empty($_POST['ios_n_device_id'])) {
            $re_data['ios_n_device_id']  = Wo_Secure($_POST['ios_n_device_id']);
        }
        $register = Wo_RegisterUser($re_data);
        if ($register !== true) {
            $json_error_data = array(
                'api_status' => '400',
                'api_text' => 'failed',
                'api_version' => $api_version,
                'errors' => array(
                    'error_id' => '15',
                    'error_text' => 'Registration failed. Please try again later.'
                )
            );
            header("Content-type: application/json");
            echo json_encode($json_error_data, JSON_PRETTY_PRINT);
            exit();
        }
        if ($register === true) {
            if ($activate == 1) {
                // Успішна реєстрація з автоматичною активацією
                $s = $_POST['s'];
                $s_md5 = md5($_POST['s']);
                $time = time();
                $user_id = Wo_UserIdFromUsername($username);
                $add_session = mysqli_query($sqlConnect, "INSERT INTO " . T_APP_SESSIONS . " (`user_id`, `session_id`, `platform`, `time`) VALUES ('{$user_id}', '{$s_md5}', 'phone', '{$time}')");

                $json_success_data  = array(
                	'api_status' => '200',
                    'api_text' => 'success',
                    'api_version' => $api_version,
                    'message' => 'Successfully joined, Please wait..',
                    'success_type' => 'registered',
                    'session_id' => $s_md5,
                    'cookie' => Wo_CreateLoginSession(Wo_UserIdForLogin($username)),
                    'user_id' => $user_id,
                    'access_token' => $s_md5,  // КРИТИЧНО: Android очікує access_token
                    'username' => $username
                );
            } else {
                // Email верифікація потрібна
                $user_id = Wo_UserIdFromUsername($username);

                // Відправляємо email якщо є
                if (!empty($email)) {
                    $wo['user']        = $_POST;
                    $body              = Wo_LoadPage('emails/activate');
                    $send_message_data = array(
                        'from_email' => $wo['config']['siteEmail'],
                        'from_name' => $wo['config']['siteName'],
                        'to_email' => $email,
                        'to_name' => $username,
                        'subject' => $wo['lang']['account_activation'],
                        'charSet' => 'utf-8',
                        'message_body' => $body,
                        'is_html' => true
                    );
                    $send              = Wo_SendMessage($send_message_data);
                }

                // Створюємо session навіть для неактивованих користувачів
                // Щоб вони могли увійти після верифікації
                $s = $_POST['s'];
                $s_md5 = md5($_POST['s']);
                $time = time();
                $add_session = mysqli_query($sqlConnect, "INSERT INTO " . T_APP_SESSIONS . " (`user_id`, `session_id`, `platform`, `time`) VALUES ('{$user_id}', '{$s_md5}', 'phone', '{$time}')");

                $json_success_data  = array(
                	'api_status' => '200',
                    'api_text' => 'success',
                    'api_version' => $api_version,
                    'message' => 'Registration successful! We have sent you an email, Please check your inbox/spam to verify your email.',
                    'success_type' => 'verification',
                    'session_id' => $s_md5,
                    'user_id' => $user_id,
                    'access_token' => $s_md5,  // Додаємо access_token для Android
                    'username' => $username
                );
            }
        }
    } else {
        header("Content-type: application/json");
        echo json_encode($json_error_data, JSON_PRETTY_PRINT);
        exit();
    }
}
header("Content-type: application/json");
echo json_encode($json_success_data);
exit();
?>