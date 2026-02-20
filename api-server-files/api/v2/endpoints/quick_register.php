<?php
// +------------------------------------------------------------------------+
// | Quick Registration - Step 1: Send Code
// | Simplified registration: just phone or email → get verification code
// | No username/password required at this step
// +------------------------------------------------------------------------+

$response_data = array('api_status' => 400);

$email = !empty($_POST['email']) ? trim($_POST['email']) : '';
$phone_number = !empty($_POST['phone_number']) ? trim($_POST['phone_number']) : '';

// Validate: at least one contact method
if (empty($email) && empty($phone_number)) {
    $error_code = 1;
    $error_message = 'email or phone_number is required';
}

if (!empty($error_code)) {
    $response_data = array('api_status' => 400, 'error_message' => $error_message);
    echo json_encode($response_data);
    exit;
}

if (empty($error_code)) {
    // Validate email format
    if (!empty($email) && !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        $error_code = 2;
        $error_message = 'Invalid email format';
    }

    // Check if email already registered
    if (empty($error_code) && !empty($email) && Wo_EmailExists($email) === true) {
        $error_code = 3;
        $error_message = 'This email is already registered. Please log in.';
    }

    // Check if phone already registered
    if (empty($error_code) && !empty($phone_number)) {
        $phone_exists = false;
        if (function_exists('Wo_PhoneExists')) {
            $phone_exists = Wo_PhoneExists($phone_number);
        } elseif (function_exists('Wo_IsPhoneExist')) {
            $phone_exists = Wo_IsPhoneExist($phone_number);
        }
        if ($phone_exists) {
            $error_code = 4;
            $error_message = 'This phone number is already registered. Please log in.';
        }
    }
}

if (empty($error_code)) {
    // Generate auto-username (timestamp + random)
    $auto_username = 'u' . time() . rand(1000, 9999);

    // Generate temporary password (user can change later)
    $temp_password = bin2hex(random_bytes(8));

    // Generate 6-digit verification code
    $verification_code = rand(100000, 999999);

    // Create account data
    $account_data = array(
        'username' => Wo_Secure($auto_username, 0),
        'password' => $temp_password,
        'email_code' => md5(rand(1111, 9999) . time()),
        'sms_code' => $verification_code,
        'src' => 'Phone',
        'timezone' => 'UTC',
        'gender' => 'male',
        'lastseen' => time(),
        'active' => '0'  // Inactive until code verified
    );

    if (!empty($email)) {
        $account_data['email'] = Wo_Secure($email, 0);
    }
    if (!empty($phone_number)) {
        $account_data['phone_number'] = Wo_Secure($phone_number, 0);
    }

    // Device IDs
    if (!empty($_POST['android_m_device_id'])) {
        $account_data['android_m_device_id'] = Wo_Secure($_POST['android_m_device_id']);
    }
    if (!empty($_POST['ios_m_device_id'])) {
        $account_data['ios_m_device_id'] = Wo_Secure($_POST['ios_m_device_id']);
    }

    $register = Wo_RegisterUser($account_data);

    if ($register !== true) {
        $error_code = 5;
        $error_message = 'Registration failed. Please try again later.';
    } else {
        $user_id = Wo_UserIdFromUsername($auto_username);

        // Send verification code
        $code_sent = false;

        if (!empty($email)) {
            // Send via email
            $site_name = !empty($wo['config']['siteName']) ? $wo['config']['siteName'] : 'WorldMates';
            $body = '<html><body style="font-family: Arial, sans-serif; padding: 20px;">';
            $body .= '<h2>' . htmlspecialchars($site_name) . '</h2>';
            $body .= '<p>Your verification code:</p>';
            $body .= '<h1 style="color: #4CAF50; letter-spacing: 8px; font-size: 36px;">' . $verification_code . '</h1>';
            $body .= '<p>Enter this code in the app to complete registration.</p>';
            $body .= '<p>The code is valid for 15 minutes.</p>';
            $body .= '<p style="color: #999; font-size: 12px;">If you didn\'t request this, ignore this email.</p>';
            $body .= '</body></html>';

            $from_email = !empty($wo['config']['siteEmail']) ? $wo['config']['siteEmail'] : 'noreply@worldmates.club';

            $send_message_data = array(
                'from_email' => $from_email,
                'from_name'  => $site_name,
                'to_email'   => $email,
                'to_name'    => '',
                'subject'    => $site_name . ' - Verification Code: ' . $verification_code,
                'charSet'    => 'utf-8',
                'message_body' => $body,
                'is_html'    => true
            );
            $code_sent = Wo_SendMessage($send_message_data);
        }
        elseif (!empty($phone_number)) {
            // Send via SMS
            $site_name = !empty($wo['config']['siteName']) ? $wo['config']['siteName'] : 'WorldMates';
            $sms_text = $site_name . ": Your verification code is " . $verification_code;

            if (function_exists('Wo_SendSMSMessage')) {
                $code_sent = Wo_SendSMSMessage($phone_number, $sms_text);
            }
        }

        if ($code_sent) {
            $response_data = array(
                'api_status' => 200,
                'message' => !empty($email)
                    ? 'Verification code sent to your email'
                    : 'Verification code sent to your phone',
                'user_id' => $user_id,
                'username' => $auto_username,
                'verification_method' => !empty($email) ? 'email' : 'phone',
            );
        } else {
            // Code not sent, but account created — allow retry
            $response_data = array(
                'api_status' => 200,
                'message' => 'Account created. Verification code could not be sent, please try resending.',
                'user_id' => $user_id,
                'username' => $auto_username,
                'verification_method' => !empty($email) ? 'email' : 'phone',
                'code_sent' => false
            );
        }
    }
}

if (!empty($error_code)) {
    $response_data = array('api_status' => 400, 'error_message' => $error_message);
}
echo json_encode($response_data);
