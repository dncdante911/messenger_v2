<?php
// +------------------------------------------------------------------------+
// | API Endpoint: Upload User Avatar
// +------------------------------------------------------------------------+
// | Загрузка аватара пользователя с детальным логированием
// +------------------------------------------------------------------------+

// Налаштування логування
define('AVATAR_LOG_FILE', '/var/www/www-root/data/www/worldmates.club/api/v2/logs/avatar_upload.log');

// Функція для запису логів У КОНКРЕТНИЙ файл
function log_avatar($message) {
    $timestamp = date('Y-m-d H:i:s');
    $log_message = "[{$timestamp}] {$message}\n";
    error_log($log_message, 3, AVATAR_LOG_FILE);
}

// Створюємо папку logs якщо її немає
$log_dir = dirname(AVATAR_LOG_FILE);
if (!file_exists($log_dir)) {
    mkdir($log_dir, 0755, true);
}

// Initialize response
$error_code = 0;
$error_message = '';
$data = [];

log_avatar("========== NEW AVATAR UPLOAD REQUEST ==========");
log_avatar("📊 Request method: " . $_SERVER['REQUEST_METHOD']);
log_avatar("📊 Content-Type: " . ($_SERVER['CONTENT_TYPE'] ?? 'not set'));
log_avatar("📊 REQUEST_URI: " . ($_SERVER['REQUEST_URI'] ?? 'not set'));
log_avatar("📊 SCRIPT_FILENAME: " . ($_SERVER['SCRIPT_FILENAME'] ?? 'not set'));
log_avatar("📊 Called through index.php: " . (defined('DB_HOST') ? 'YES (config loaded)' : 'NO (config NOT loaded)'));
log_avatar("📊 \$sqlConnect exists: " . (isset($sqlConnect) ? 'YES' : 'NO'));
log_avatar("📊 \$wo exists: " . (isset($wo) ? 'YES' : 'NO'));

// Get access token from POST or GET (Android sends via URL, but WoWonder expects POST)
$access_token = $_POST['access_token'] ?? $_GET['access_token'] ?? '';

if (empty($access_token)) {
    log_avatar("❌ ERROR: access_token is missing");
    log_avatar("❌ \$_POST keys: " . implode(', ', array_keys($_POST)));
    log_avatar("❌ \$_GET keys: " . implode(', ', array_keys($_GET)));
    $error_code = 3;
    $error_message = 'access_token is missing';
    http_response_code(400);
} else {
    log_avatar("✓ access_token received: " . substr($access_token, 0, 20) . "...");
}

if ($error_code == 0) {
    // Validate access token and get user_id
    log_avatar("🔍 Step 1: Validating access token...");

    // Перевірка чи функція існує
    if (!function_exists('Wo_ValidateAccessToken')) {
        log_avatar("❌ CRITICAL: Function Wo_ValidateAccessToken does NOT exist!");
        log_avatar("❌ This means config.php was not loaded properly");
        log_avatar("❌ Available functions starting with 'Wo_': " . implode(', ', array_filter(get_defined_functions()['user'], function($f) { return strpos($f, 'Wo_') === 0; })));
        $error_code = 500;
        $error_message = 'Server configuration error: Required function not found';
        http_response_code(500);
    } else {
        log_avatar("✓ Function Wo_ValidateAccessToken exists");

        try {
            $user_id = Wo_ValidateAccessToken($access_token);
            log_avatar("✓ Wo_ValidateAccessToken returned: " . var_export($user_id, true));

            if (empty($user_id) || !is_numeric($user_id) || $user_id < 1) {
                log_avatar("❌ ERROR: Invalid access_token - user_id is invalid");
                $error_code = 4;
                $error_message = 'Invalid access_token';
                http_response_code(401);
            } else {
                log_avatar("✓ Valid user_id: {$user_id}");
            }
        } catch (Exception $e) {
            log_avatar("❌ EXCEPTION in Wo_ValidateAccessToken: " . $e->getMessage());
            log_avatar("❌ Stack trace: " . $e->getTraceAsString());
            $error_code = 500;
            $error_message = 'Error validating access token';
            http_response_code(500);
        } catch (Error $e) {
            log_avatar("❌ FATAL ERROR in Wo_ValidateAccessToken: " . $e->getMessage());
            log_avatar("❌ File: " . $e->getFile() . " Line: " . $e->getLine());
            $error_code = 500;
            $error_message = 'Fatal error validating access token';
            http_response_code(500);
        }
    }
}

if ($error_code == 0 && !empty($user_id)) {
    // Load user data
    log_avatar("🔍 Step 2: Loading user data for user_id={$user_id}...");

    global $wo;

    if (!function_exists('Wo_UserData')) {
        log_avatar("❌ CRITICAL: Function Wo_UserData does NOT exist!");
        $error_code = 500;
        $error_message = 'Server configuration error: Wo_UserData not found';
        http_response_code(500);
    } else {
        try {
            $user_data = Wo_UserData($user_id);

            if (empty($user_data)) {
                log_avatar("❌ ERROR: User not found for user_id={$user_id}");
                $error_code = 5;
                $error_message = 'User not found';
                http_response_code(404);
            } else {
                log_avatar("✓ User data loaded: username={$user_data['username']}");

                // Update $wo['user'] with real user data (required by Wo_UploadImage)
                $wo['user'] = $user_data;
                $wo['loggedin'] = true;
                log_avatar("✓ \$wo['user'] and \$wo['loggedin'] set successfully");
            }
        } catch (Exception $e) {
            log_avatar("❌ EXCEPTION in Wo_UserData: " . $e->getMessage());
            $error_code = 500;
            $error_message = 'Error loading user data';
            http_response_code(500);
        }
    }
}

if ($error_code == 0 && !empty($user_data)) {
    // Check if file is uploaded
    log_avatar("🔍 Step 3: Checking uploaded file...");
    log_avatar("📂 \$_FILES dump: " . print_r($_FILES, true));

    // Android sends file with key 'file', but we expect 'avatar'
    if (!empty($_FILES['file']['tmp_name']) && empty($_FILES['avatar']['tmp_name'])) {
        $_FILES['avatar'] = $_FILES['file'];
        log_avatar("✓ Remapped \$_FILES['file'] to \$_FILES['avatar']");
    }

    if (empty($_FILES['avatar']['tmp_name'])) {
        log_avatar("❌ ERROR: Avatar file is required - \$_FILES['avatar']['tmp_name'] is empty");
        log_avatar("❌ Available keys in \$_FILES: " . implode(', ', array_keys($_FILES)));
        if (isset($_FILES['avatar']['error'])) {
            $upload_errors = [
                UPLOAD_ERR_INI_SIZE => 'File exceeds upload_max_filesize',
                UPLOAD_ERR_FORM_SIZE => 'File exceeds MAX_FILE_SIZE',
                UPLOAD_ERR_PARTIAL => 'File only partially uploaded',
                UPLOAD_ERR_NO_FILE => 'No file uploaded',
                UPLOAD_ERR_NO_TMP_DIR => 'Missing temporary folder',
                UPLOAD_ERR_CANT_WRITE => 'Failed to write file to disk',
                UPLOAD_ERR_EXTENSION => 'Upload stopped by extension'
            ];
            $error_msg = $upload_errors[$_FILES['avatar']['error']] ?? 'Unknown error: ' . $_FILES['avatar']['error'];
            log_avatar("❌ Upload error: {$error_msg}");
        }
        $error_code = 6;
        $error_message = 'Avatar file is required';
        http_response_code(400);
    } else {
        log_avatar("✓ File uploaded to tmp: {$_FILES['avatar']['tmp_name']}");
        log_avatar("🖼️ Avatar upload: user_id={$user_id}, file={$_FILES['avatar']['name']}, size={$_FILES['avatar']['size']}");

        // Check if Wo_UploadImage exists
        if (!function_exists('Wo_UploadImage')) {
            log_avatar("❌ CRITICAL: Function Wo_UploadImage does NOT exist!");
            $error_code = 500;
            $error_message = 'Server configuration error: Wo_UploadImage not found';
            http_response_code(500);
        } else {
            try {
                log_avatar("🔍 Step 4: Calling Wo_UploadImage...");

                // Upload image using WoWonder's function
                $upload_image = Wo_UploadImage(
                    $_FILES["avatar"]["tmp_name"],
                    $_FILES['avatar']['name'],
                    'avatar',
                    $_FILES['avatar']['type'],
                    $user_id
                );

                log_avatar("🖼️ Upload result: " . ($upload_image === true ? 'SUCCESS' : 'FAILED (returned: ' . var_export($upload_image, true) . ')'));

                if ($upload_image === true) {
                    // Get updated user data
                    $updated_user = Wo_UserData($user_id);

                    if ($updated_user) {
                        // Clear user cache
                        if (function_exists('cache')) {
                            cache($user_id, 'users', 'delete');
                            log_avatar("✓ Cache cleared");
                        }

                        log_avatar("✅ Avatar saved to DB: {$updated_user['avatar']}");

                        // Prepare avatar URL - check if it already contains domain
                        $avatar_url = $updated_user['avatar'];
                        if (!empty($avatar_url) && strpos($avatar_url, 'http') !== 0) {
                            // Relative path - use Wo_GetMedia to add domain
                            $avatar_url = Wo_GetMedia($avatar_url);
                        }
                        // If it already starts with http, use as-is (already full URL)

                        // Success response
                        $data = array(
                            'api_status' => 200,
                            'message' => 'Avatar uploaded successfully',
                            'url' => $avatar_url,
                            'avatar' => $updated_user['avatar'],
                            'avatar_org' => $updated_user['avatar_org']
                        );

                        log_avatar("✅ Response: url={$data['url']}, avatar={$data['avatar']}");
                    } else {
                        log_avatar("❌ ERROR: Failed to retrieve updated user data after upload");
                        $error_code = 8;
                        $error_message = 'Failed to retrieve updated user data';
                        http_response_code(500);
                    }
                } else {
                    $error_code = 7;
                    $error_message = 'Failed to upload image. Please check file type and size.';
                    log_avatar("❌ Wo_UploadImage failed for user_id={$user_id}");
                    log_avatar("❌ File details: name={$_FILES['avatar']['name']}, type={$_FILES['avatar']['type']}, size={$_FILES['avatar']['size']}");
                    log_avatar("❌ wo[loggedin]=" . ($wo['loggedin'] ? 'true' : 'false') . ", wo[user][user_id]={$wo['user']['user_id']}");
                    http_response_code(500);
                }
            } catch (Exception $e) {
                log_avatar("❌ EXCEPTION in Wo_UploadImage: " . $e->getMessage());
                log_avatar("❌ Stack trace: " . $e->getTraceAsString());
                $error_code = 500;
                $error_message = 'Error uploading image';
                http_response_code(500);
            } catch (Error $e) {
                log_avatar("❌ FATAL ERROR in Wo_UploadImage: " . $e->getMessage());
                log_avatar("❌ File: " . $e->getFile() . " Line: " . $e->getLine());
                $error_code = 500;
                $error_message = 'Fatal error uploading image';
                http_response_code(500);
            }
        }
    }
}

// Send response
if ($error_code > 0) {
    log_avatar("❌ Sending error response: error_code={$error_code}, message={$error_message}");
    http_response_code($error_code >= 100 ? $error_code : 400);
    echo json_encode([
        'api_status' => $error_code >= 100 ? $error_code : 400,
        'error_code' => $error_code,
        'error_message' => $error_message
    ]);
} else if (!empty($data)) {
    log_avatar("✅ SUCCESS - Sending successful response");
    echo json_encode($data);
} else {
    log_avatar("❌ CRITICAL ERROR: No data and no error code!");
    http_response_code(500);
    echo json_encode([
        'api_status' => 500,
        'error_message' => 'Unknown error occurred'
    ]);
}

log_avatar("========== REQUEST COMPLETED ==========\n");

// Exit to prevent api-v2.php from echoing additional output
exit();
?>
