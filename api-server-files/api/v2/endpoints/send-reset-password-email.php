<?php
// +------------------------------------------------------------------------+
// | Password Reset Endpoint
// | Supports both email and SMS (phone) reset methods
// +------------------------------------------------------------------------+
$response_data = array(
    'api_status' => 400,
);

// Accept either email or phone_number
$reset_email = !empty($_POST['email']) ? $_POST['email'] : '';
$reset_phone = !empty($_POST['phone_number']) ? $_POST['phone_number'] : '';

if (empty($reset_email) && empty($reset_phone)) {
    $error_code    = 3;
    $error_message = 'email or phone_number (POST) is required';
}

if (empty($error_code)) {
    $user_id = 0;
    $user_recover_data = null;

    // Find user by email
    if (!empty($reset_email)) {
        if (Wo_EmailExists($reset_email) === false) {
            $error_code    = 6;
            $error_message = 'Email not found';
        } else {
            $user_id = Wo_UserIdFromEmail($reset_email);
            $user_recover_data = Wo_UserData($user_id);
        }
    }
    // Find user by phone number
    elseif (!empty($reset_phone)) {
        if (function_exists('Wo_UserIdFromPhoneNumber')) {
            $user_id = Wo_UserIdFromPhoneNumber($reset_phone);
        }
        if (empty($user_id)) {
            $error_code    = 6;
            $error_message = 'Phone number not found';
        } else {
            $user_recover_data = Wo_UserData($user_id);
        }
    }

    if (empty($error_code) && empty($user_recover_data)) {
        $error_code    = 6;
        $error_message = 'User not found';
    }

    if (empty($error_code)) {
        // Generate a unique reset code and save to email_code field
        $reset_code = md5(rand(11111, 99999) . time() . $user_id);
        $reset_code_short = rand(100000, 999999); // 6-digit code for SMS/mobile
        $reset_code_esc = mysqli_real_escape_string($sqlConnect, $reset_code);
        $reset_code_short_esc = mysqli_real_escape_string($sqlConnect, $reset_code_short);

        // Save both codes: email_code for web link, sms_code for mobile verification
        mysqli_query($sqlConnect, "UPDATE " . T_USERS . " SET `email_code` = '{$reset_code_esc}', `sms_code` = '{$reset_code_short_esc}' WHERE `user_id` = " . (int)$user_id);
        cache($user_id, 'users', 'delete');

        // Determine reset method
        if (!empty($reset_email)) {
            // EMAIL RESET
            $site_url = !empty($wo['config']['site_url']) ? $wo['config']['site_url'] : $wo['site_url'];
            $reset_link = $site_url . '/index.php?link1=reset-password&code=' . $user_id . '_' . $user_recover_data['password'];

            $site_name = !empty($wo['config']['siteName']) ? $wo['config']['siteName'] : 'WorldMates';
            $subject = $site_name . ' - Password Reset Request';

            // Try to load email template, fallback to simple HTML
            $body = '';
            $wo['recover'] = $user_recover_data;
            $wo['recover']['link'] = $reset_link;

            if (function_exists('Wo_LoadPage')) {
                $saved_dir = getcwd();
                $site_root = $_SERVER['DOCUMENT_ROOT'];
                if (is_dir($site_root) && file_exists($site_root . '/themes')) {
                    chdir($site_root);
                    $body = @Wo_LoadPage('emails/recover');
                    chdir($saved_dir);
                }
            }

            // Fallback email body
            if (empty($body)) {
                $user_name = !empty($user_recover_data['name']) ? $user_recover_data['name'] : $user_recover_data['username'];
                $body = '<html><body style="font-family: Arial, sans-serif; padding: 20px;">';
                $body .= '<h2>' . htmlspecialchars($site_name) . '</h2>';
                $body .= '<p>Hello ' . htmlspecialchars($user_name) . ',</p>';
                $body .= '<p>We received a password reset request for your account.</p>';
                $body .= '<p>Your reset code is: <strong>' . $reset_code_short . '</strong></p>';
                $body .= '<p>Or click the link below to reset your password:</p>';
                $body .= '<p><a href="' . htmlspecialchars($reset_link) . '" style="background-color: #4CAF50; color: white; padding: 10px 20px; text-decoration: none; border-radius: 4px;">Reset Password</a></p>';
                $body .= '<p>If you did not request this, please ignore this email.</p>';
                $body .= '<p>Thank you,<br>' . htmlspecialchars($site_name) . ' Team</p>';
                $body .= '</body></html>';
            }

            $from_email = !empty($wo['config']['siteEmail']) ? $wo['config']['siteEmail'] : 'noreply@worldmates.club';

            $send_message_data = array(
                'from_email' => $from_email,
                'from_name'  => $site_name,
                'to_email'   => $reset_email,
                'to_name'    => '',
                'subject'    => $subject,
                'charSet'    => 'utf-8',
                'message_body' => $body,
                'is_html'    => true
            );
            $send = Wo_SendMessage($send_message_data);
            if ($send) {
                $response_data = array(
                    'api_status' => 200,
                    'message' => 'Password reset email sent successfully',
                    'reset_method' => 'email',
                );
            } else {
                $error_code    = 7;
                $error_message = 'Failed to send the email, please check your server email settings.';
            }
        }
        elseif (!empty($reset_phone)) {
            // SMS RESET
            $site_name = !empty($wo['config']['siteName']) ? $wo['config']['siteName'] : 'WorldMates';
            $sms_message = $site_name . ": Your password reset code is " . $reset_code_short;

            if (function_exists('Wo_SendSMSMessage') && Wo_SendSMSMessage($reset_phone, $sms_message) === true) {
                $response_data = array(
                    'api_status' => 200,
                    'message' => 'Password reset code sent via SMS',
                    'reset_method' => 'sms',
                );
            } else {
                $error_code    = 7;
                $error_message = 'Failed to send SMS. Please check your server SMS settings.';
            }
        }
    }
}
