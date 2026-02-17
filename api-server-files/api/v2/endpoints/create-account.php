<?php
// +------------------------------------------------------------------------+
// | @author Deen Doughouz (DoughouzForest)
// | @author_url 1: http://www.wowonder.com
// | @author_url 2: http://codecanyon.net/user/doughouzforest
// | @author_email: wowondersocial@gmail.com   
// +------------------------------------------------------------------------+
// | WoWonder - The Ultimate Social Networking Platform
// | Copyright (c) 2018 WoWonder. All rights reserved.
// +------------------------------------------------------------------------+
$response_data   = array(
    'api_status' => 400
);
$required_fields = array(
    'username',
    'password',
    'confirm_password'
);
if ($wo['config']['auto_username'] == 1) {
    $_POST['username'] = time() . rand(111111, 999999);
    if (empty($_POST['first_name']) || empty($_POST['last_name'])) {
        $error_code    = 3;
        $error_message = $wo['lang']['first_name_last_name_empty'];
    }
    elseif (preg_match('/[^\w\s]+/u', $_POST['first_name']) || preg_match('/[^\w\s]+/u', $_POST['last_name'])) {
        $error_code    = 3;
        $error_message = $wo['lang']['username_invalid_characters'];
    }
}
foreach ($required_fields as $key => $value) {
    if (empty($_POST[$value]) && empty($error_code)) {
        $error_code    = 3;
        $error_message = $value . ' (POST) is missing';
    }
}

// Email or phone_number is required
if (empty($error_code) && empty($_POST['email']) && empty($_POST['phone_number'])) {
    $error_code    = 3;
    $error_message = 'email or phone_number (POST) is required';
}

if (empty($error_code)) {
    $username         = $_POST['username'];
    $password         = $_POST['password'];
    $email            = !empty($_POST['email']) ? $_POST['email'] : '';
    $phone_number     = !empty($_POST['phone_number']) ? $_POST['phone_number'] : '';
    $confirm_password = $_POST['confirm_password'];
    if (in_array(true, Wo_IsNameExist($username, 0))) {
        $error_code    = 4;
        $error_message = 'This username is already taken. Please choose another one.';
        $error_key     = 'username_taken';
    } else if (in_array($username, $wo['site_pages']) || !preg_match('/^[\w]+$/', $username)) {
        $error_code    = 5;
        $error_message = 'Username contains invalid characters. Only letters, numbers and underscore are allowed.';
        $error_key     = 'username_invalid';
    } else if (strlen($username) < 5 OR strlen($username) > 32) {
        $error_code    = 6;
        $error_message = 'Username must be between 5 and 32 characters.';
        $error_key     = 'username_length';
    } else if (!empty($email) && Wo_EmailExists($email) === true) {
        $error_code    = 7;
        $error_message = 'This email is already registered. Please log in or use a different email.';
        $error_key     = 'email_taken';
    } else if (!empty($email) && !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        $error_code    = 8;
        $error_message = 'Invalid email format. Please enter a valid email address.';
        $error_key     = 'email_invalid';
    } else if (!empty($phone_number) && (function_exists('Wo_PhoneExists') ? Wo_PhoneExists($phone_number) : (function_exists('Wo_IsPhoneExist') ? Wo_IsPhoneExist($phone_number) : false))) {
        $error_code    = 13;
        $error_message = 'This phone number is already registered. Please log in or use a different number.';
        $error_key     = 'phone_taken';
    } else if (strlen($password) < 6) {
        $error_code    = 9;
        $error_message = 'Password is too short. Minimum 6 characters required.';
        $error_key     = 'password_weak';
    } else if (!preg_match('/[A-Za-z]/', $password) || !preg_match('/[0-9]/', $password)) {
        $error_code    = 14;
        $error_message = 'Password must contain at least one letter and one number.';
        $error_key     = 'password_simple';
    } else if ($password != $confirm_password) {
        $error_code    = 10;
        $error_message = 'Passwords do not match. Please make sure both passwords are identical.';
        $error_key     = 'password_mismatch';
    }
    if (empty($error_code)) {
        // If registering with email and email validation is on, require verification
        // If registering with phone only, auto-activate
        $activate = 1;
        if (!empty($email) && $wo['config']['emailValidation'] == '1') {
            $activate = 0;
        }
        //$device_id = (!empty($_POST['device_id'])) ? $_POST['device_id'] : '';
        $gender = 'male';
        if (in_array($_POST['gender'], array_keys($wo['genders']))) {
            $gender = $_POST['gender'];
        }
        $code = md5(rand(1111, 9999) . time());
        $account_data = array(
            'username' => Wo_Secure($username, 0),
            'password' => $password,
            'email_code' => $code,
            'src' => 'Phone',
            'timezone' => 'UTC',
            'gender' => Wo_Secure($gender),
            'lastseen' => time(),
            'active' => Wo_Secure($activate)
        );
        if (!empty($email)) {
            $account_data['email'] = Wo_Secure($email, 0);
        }
        if (!empty($phone_number)) {
            $account_data['phone_number'] = Wo_Secure($phone_number, 0);
        }
        if (!empty($_POST['android_m_device_id'])) {
            $account_data['android_m_device_id']  = Wo_Secure($_POST['android_m_device_id']);
        }
        if (!empty($_POST['ios_m_device_id'])) {
            $account_data['ios_m_device_id']  = Wo_Secure($_POST['ios_m_device_id']);
        }
        if (!empty($_POST['android_n_device_id'])) {
            $account_data['android_n_device_id']  = Wo_Secure($_POST['android_n_device_id']);
        }
        if (!empty($_POST['ios_n_device_id'])) {
            $account_data['ios_n_device_id']  = Wo_Secure($_POST['ios_n_device_id']);
        }
        if ($gender == 'female') {
            $account_data['avatar'] = 'upload/photos/f-avatar.jpg';
        }
        if (!empty($_POST['ref'])) {
            $get_ip = get_ip_address();
            if (!empty($get_ip)) {
                $_POST['ref'] = Wo_Secure($_POST['ref']);
                $ref_user_id = Wo_UserIdFromUsername($_POST['ref']);
                $user_date = Wo_UserData($ref_user_id);
                if (!empty($user_date)) {
                    if (ip_in_range($user_date['ip_address'], '/24') === false && $user_date['ip_address'] != $get_ip) {
                        $_SESSION['ref'] = $user_date['username'];
                        if (!empty($_SESSION['ref']) && $wo['config']['affiliate_type'] == 0) {
                            $ref_user_id = Wo_UserIdFromUsername($_SESSION['ref']);
                            if (!empty($ref_user_id) && is_numeric($ref_user_id)) {
                                $account_data['referrer'] = Wo_Secure($ref_user_id);
                                $account_data['src']      = Wo_Secure('Referrer');
                                if ($wo['config']['affiliate_level'] < 2) {
                                    $update_balance      = Wo_UpdateBalance($ref_user_id, $wo['config']['amount_ref']);
                                }
                                unset($_SESSION['ref']);
                            }
                        }
                        elseif (!empty($_SESSION['ref']) && $wo['config']['affiliate_type'] == 1) {
                            $ref_user_id = Wo_UserIdFromUsername($_SESSION['ref']);
                            if (!empty($ref_user_id) && is_numeric($ref_user_id)) {
                                $account_data['ref_user_id']      = Wo_Secure($ref_user_id);
                            }
                        }
                    }
                }
            }
        }

        if ($wo['config']['auto_username'] == 1) {
            $account_data['first_name'] = Wo_Secure($_POST['first_name']);
            $account_data['last_name'] = Wo_Secure($_POST['last_name']);
        }

        $register     = Wo_RegisterUser($account_data);
        if ($register === true) {
            if (!empty($account_data['referrer']) && is_numeric($wo['config']['affiliate_level']) && $wo['config']['affiliate_level'] > 1) {
                $user_id = Wo_UserIdFromUsername($username);
                AddNewRef($account_data['referrer'],$user_id,$wo['config']['amount_ref']);
            }
            if (!empty($wo['config']['auto_friend_users'])) {
                $autoFollow = Wo_AutoFollow(Wo_UserIdFromUsername($_POST['username']));
            }
            if (!empty($wo['config']['auto_page_like'])) {
                Wo_AutoPageLike(Wo_UserIdFromUsername($_POST['username']));
            }
            if (!empty($wo['config']['auto_group_join'])) {
                Wo_AutoGroupJoin(Wo_UserIdFromUsername($_POST['username']));
            }
            
            if ($activate == 1) {
                $access_token        = sha1(rand(111111111, 999999999)) . md5(microtime()) . rand(11111111, 99999999) . md5(rand(5555, 9999));
                $time                = time();
                $user_id             = Wo_UserIdFromUsername($username);
                $device_type = 'phone';
                if (!empty($_POST['device_type']) && in_array($_POST['device_type'], array('phone','windows'))) {
                    $device_type = Wo_Secure($_POST['device_type']);
                }
                $create_access_token = mysqli_query($sqlConnect, "INSERT INTO " . T_APP_SESSIONS . " (`user_id`, `session_id`, `platform`, `time`) VALUES ('{$user_id}', '{$access_token}', '{$device_type}', '{$time}')");
                if ($create_access_token) {
                    $response_data = array(
                        'api_status' => 200,
                        'access_token' => $access_token,
                        'user_id' => $user_id,
                        'user_platform' => $device_type,
                    );
                }
            } elseif ($wo['config']['sms_or_email'] == 'mail') {
                $user_id             = Wo_UserIdFromUsername($username);
                $wo['user']        = $_POST;
                $wo['code']        = $code;
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
                if ($send) {
                    $response_data = array(
                        'api_status' => 220,
                        'message' => 'Registration successful! We have sent you an email, Please check your inbox/spam to verify your email.',
                        'user_id' => $user_id
                    );
                } else {
                    $error_code    = 11;
                    $error_message = 'Error found while sending the verification email, please try again later.';
                }
            }
            elseif ($wo['config']['sms_or_email'] == 'sms' && (!empty($_POST['phone_num']) || !empty($phone_number))) {
                $sms_phone = !empty($_POST['phone_num']) ? $_POST['phone_num'] : $phone_number;
                $random_activation = Wo_Secure(rand(11111, 99999));
                $message           = "Your confirmation code is: {$random_activation}";

                if (Wo_SendSMSMessage($sms_phone, $message) === true) {
                    $user_id             = Wo_UserIdFromUsername($username);
                    $query             = mysqli_query($sqlConnect, "UPDATE " . T_USERS . " SET `sms_code` = '{$random_activation}' WHERE `user_id` = {$user_id}");
                    $response_data = array(
                        'api_status' => 220,
                        'message' => 'Registration successful! We have sent you an sms, Please check your phone to verify your account.',
                        'user_id' => $user_id
                    );
                    cache($user_id, 'users', 'delete');
                } else {
                    $error_code    = 11;
                    $error_message = 'Error found while sending the verification sms, please try again later.';
                }
            }
            elseif ($wo['config']['sms_or_email'] == 'sms' && empty($_POST['phone_num']) && empty($phone_number)) {
                $error_code    = 12;
                $error_message = 'phone_num or phone_number can not be empty.';
            }
            if (!empty($response_data)) {
                $response_data['membership'] = false;
                if ($wo['config']['membership_system'] == 1) {
                    $response_data['membership'] = true;
                }
            }
        }
    }
}