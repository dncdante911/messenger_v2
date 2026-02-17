<?php
/**
 * Windows message decryptor for WoWonder-based desktop API.
 * Supports:
 *  - v1 AES-128-ECB (legacy)
 *  - v2 AES-256-GCM (iv+tag)
 */

if (!function_exists('wm_build_key_ecb')) {
    function wm_build_key_ecb($timestamp) {
        $timestamp_str = strval($timestamp);
        return str_pad(substr($timestamp_str, 0, 16), 16, "\0");
    }
}

if (!function_exists('wm_build_key_gcm')) {
    function wm_build_key_gcm($timestamp) {
        $timestamp_str = strval($timestamp);
        if ($timestamp_str === '') {
            $timestamp_str = '0';
        }
        $key = '';
        while (strlen($key) < 32) {
            $key .= $timestamp_str;
        }
        return substr($key, 0, 32);
    }
}

if (!function_exists('wm_try_decrypt_ecb')) {
    function wm_try_decrypt_ecb($encrypted_b64, $timestamp) {
        if (empty($encrypted_b64)) {
            return null;
        }

        $cipher_raw = base64_decode($encrypted_b64, true);
        if ($cipher_raw === false) {
            return null;
        }

        $key = wm_build_key_ecb($timestamp);
        $plain = openssl_decrypt($cipher_raw, 'AES-128-ECB', $key, OPENSSL_RAW_DATA);
        if ($plain === false || $plain === null) {
            return null;
        }

        return trim($plain);
    }
}

if (!function_exists('wm_try_decrypt_gcm')) {
    function wm_try_decrypt_gcm($encrypted_b64, $timestamp, $iv_b64, $tag_b64) {
        if (empty($encrypted_b64) || empty($iv_b64) || empty($tag_b64)) {
            return null;
        }

        $cipher_raw = base64_decode($encrypted_b64, true);
        $iv_raw = base64_decode($iv_b64, true);
        $tag_raw = base64_decode($tag_b64, true);

        if ($cipher_raw === false || $iv_raw === false || $tag_raw === false) {
            return null;
        }

        $key = wm_build_key_gcm($timestamp);
        $plain = openssl_decrypt($cipher_raw, 'aes-256-gcm', $key, OPENSSL_RAW_DATA, $iv_raw, $tag_raw);

        if ($plain === false || $plain === null) {
            return null;
        }

        return trim($plain);
    }
}

if (!function_exists('wm_decrypt_message_auto')) {
    function wm_decrypt_message_auto($message) {
        if (empty($message) || !is_array($message)) {
            return null;
        }

        $text = '';
        if (!empty($message['text_ecb']) && is_string($message['text_ecb'])) {
            $text = $message['text_ecb'];
        } else if (!empty($message['text']) && is_string($message['text'])) {
            $text = $message['text'];
        }

        if (empty($text)) {
            return null;
        }

        $timestamp = isset($message['time']) ? intval($message['time']) : 0;
        if ($timestamp <= 0) {
            return null;
        }

        $cipher_version = isset($message['cipher_version']) ? intval($message['cipher_version']) : null;
        $iv = isset($message['iv']) ? $message['iv'] : (isset($message['msg_iv']) ? $message['msg_iv'] : null);
        $tag = isset($message['tag']) ? $message['tag'] : (isset($message['auth_tag']) ? $message['auth_tag'] : null);

        if ($cipher_version === 2 || (!empty($iv) && !empty($tag))) {
            $gcm = wm_try_decrypt_gcm($text, $timestamp, $iv, $tag);
            if (!empty($gcm)) {
                return $gcm;
            }
        }

        $ecb = wm_try_decrypt_ecb($text, $timestamp);
        if (!empty($ecb)) {
            return $ecb;
        }

        return null;
    }
}
