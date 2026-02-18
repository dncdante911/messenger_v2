<?php
// +------------------------------------------------------------------------+
// | Get User Messages Endpoint (V2 API)
// | Fetches message history between current user and a recipient
// | Replaces legacy phone API polling (get_user_messages.php)
// +------------------------------------------------------------------------+

$response_data = array(
    'api_status' => 400,
);

// Required: recipient user_id
// Android sends 'recipient_id', legacy clients send 'user_id'
$recipient_id = !empty($_POST['recipient_id']) ? $_POST['recipient_id'] :
                (!empty($_POST['user_id']) ? $_POST['user_id'] :
                (!empty($_GET['user_id']) ? $_GET['user_id'] : ''));

if (empty($recipient_id) || !is_numeric($recipient_id)) {
    $error_code    = 3;
    $error_message = 'user_id (recipient) is required';
}

if (empty($error_code)) {
    $recipient_data = Wo_UserData($recipient_id);
    if (empty($recipient_data)) {
        $error_code    = 6;
        $error_message = 'Recipient user not found';
    }
}

if (empty($error_code)) {
    $logged_user_id = $wo['user']['user_id'];

    // Pagination parameters
    $limit = 20;
    $after_message_id = 0;
    $before_message_id = 0;
    $message_id = 0;

    if (!empty($_POST['limit']) && is_numeric($_POST['limit']) && $_POST['limit'] > 0 && $_POST['limit'] <= 100) {
        $limit = (int)$_POST['limit'];
    }
    if (!empty($_POST['after_message_id']) && is_numeric($_POST['after_message_id'])) {
        $after_message_id = (int)$_POST['after_message_id'];
    }
    if (!empty($_POST['before_message_id']) && is_numeric($_POST['before_message_id'])) {
        $before_message_id = (int)$_POST['before_message_id'];
    }
    if (!empty($_POST['message_id']) && is_numeric($_POST['message_id'])) {
        $message_id = (int)$_POST['message_id'];
    }

    // Detect client type
    $use_gcm = !empty($_POST['use_gcm']) && $_POST['use_gcm'] == 'true';
    $is_worldmates = $use_gcm;

    // Build query
    $r_id = (int)$recipient_id;
    $l_id = (int)$logged_user_id;

    $query = "SELECT * FROM " . T_MESSAGES . " WHERE ((`from_id` = {$r_id} AND `to_id` = {$l_id} AND `deleted_two` = '0') OR (`from_id` = {$l_id} AND `to_id` = {$r_id} AND `deleted_one` = '0')) AND `page_id` = '0'";

    if ($message_id > 0) {
        $query .= " AND `id` = {$message_id}";
    } elseif ($after_message_id > 0) {
        $query .= " AND `id` > {$after_message_id}";
    } elseif ($before_message_id > 0) {
        $query .= " AND `id` < {$before_message_id}";
    }

    $query .= " ORDER BY `id` DESC LIMIT {$limit}";
    $sql_result = mysqli_query($sqlConnect, $query);

    $messages = array();

    if ($sql_result && mysqli_num_rows($sql_result) > 0) {
        $user_timezone = !empty($wo['user']['timezone']) ? $wo['user']['timezone'] : 'UTC';
        $timezone = new DateTimeZone($user_timezone);

        while ($message = mysqli_fetch_assoc($sql_result)) {
            // Position (left = received, right = sent)
            $message_po = ($message['from_id'] == $logged_user_id) ? 'right' : 'left';
            $message['position'] = $message_po;

            // Message type
            $message['type'] = '';
            if (!empty($message['media'])) {
                $message['type'] = Wo_GetFilePosition($message['media']);
            }
            if (!empty($message['stickers']) && strpos($message['stickers'], '.gif') !== false) {
                $message['type'] = 'gif';
            }
            if (!empty($message['type_two']) && $message['type_two'] == 'contact') {
                $message['type'] = 'contact';
            }
            if (!empty($message['lng']) && !empty($message['lat'])) {
                $message['type'] = 'map';
            }
            $message['type'] = $message_po . '_' . $message['type'];

            // Product
            $message['product'] = null;
            if (!empty($message['product_id'])) {
                $message['type'] = $message_po . '_product';
                $message['product'] = Wo_GetProduct($message['product_id']);
            }

            // File size
            $message['file_size'] = 0;
            if (!empty($message['media'])) {
                $message['file_size'] = '0MB';
                if (file_exists($message['media'])) {
                    $message['file_size'] = Wo_SizeFormat(filesize($message['media']));
                }
                $message['media'] = Wo_GetMedia($message['media']);
            }

            // Stickers
            if (empty($message['stickers'])) {
                $message['stickers'] = '';
            }

            // Time text
            $message['time_text'] = Wo_Time_Elapsed_String($message['time']);
            if (!empty($message['time'])) {
                $time_today = time() - 86400;
                if ($message['time'] < $time_today) {
                    $message['time_text'] = date('m.d.y', $message['time']);
                } else {
                    $time = new DateTime('now', $timezone);
                    $time->setTimestamp($message['time']);
                    $message['time_text'] = $time->format('H:i');
                }
            }

            // Encryption handling
            if ($is_worldmates) {
                // WorldMates: keep GCM fields as-is
            } else {
                // WoWonder/browser: use ECB or plain
                if (!empty($message['text_ecb'])) {
                    $message['text'] = $message['text_ecb'];
                }
                unset($message['iv'], $message['tag'], $message['cipher_version'], $message['text_ecb']);
            }

            // Sender data
            $message['user_data'] = Wo_UserData($message['from_id']);
            if (!empty($message['user_data'])) {
                unset($message['user_data']['password']);
                unset($message['user_data']['email']);
                unset($message['user_data']['phone_number']);
            }

            // Reply data
            if (!empty($message['reply_id']) && $message['reply_id'] > 0) {
                $reply_query = mysqli_query($sqlConnect, "SELECT `id`, `from_id`, `text`, `media`, `time` FROM " . T_MESSAGES . " WHERE `id` = " . (int)$message['reply_id'] . " LIMIT 1");
                if ($reply_query && mysqli_num_rows($reply_query) > 0) {
                    $message['reply'] = mysqli_fetch_assoc($reply_query);
                }
            }

            $messages[] = $message;
        }
    }

    // Reverse so oldest is first (consistent with chat display)
    $messages = array_reverse($messages);

    // Check typing status
    $typing = 0;
    if (function_exists('Wo_IsTyping')) {
        $typing = Wo_IsTyping($recipient_id) ? 1 : 0;
    }

    $response_data = array(
        'api_status' => 200,
        'typing' => $typing,
        'messages' => $messages,
    );
}
