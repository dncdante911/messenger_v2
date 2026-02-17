<?php
class Cache {
    private $redis;
    private $use_redis = false;

    function __construct() {
        global $wo; // Получаем настройки WoWonder
        if (!empty($wo['config']['cache_system']) && $wo['config']['cache_system'] == 'redis') {
            try {
                $this->redis = new Redis();
                $this->redis->connect($wo['config']['redis_host'], $wo['config']['redis_port']);
                if (!empty($wo['config']['redis_password'])) {
                    $this->redis->auth($wo['config']['redis_password']);
                }
                $this->use_redis = true;
            } catch (Exception $e) {
                error_log("Redis Error: " . $e->getMessage());
                $this->use_redis = false;
            }
        }
    }

    function read($fileName) {
        if ($this->use_redis) {
            $data = $this->redis->get($fileName);
            if (!$data) {
                return null;
            }
            $decoded = @unserialize($data);
            if ($decoded === false && $data !== serialize(false)) {
                error_log("Ошибка кеша Redis: повреждённые данные в ключе $fileName");
                return null;
            }
            return $decoded;
        }

        $fileName = 'cache/' . $fileName;
        if (file_exists($fileName)) {
            $handle = fopen($fileName, 'rb');
            if ($handle) {
                $variable = fread($handle, filesize($fileName));
                fclose($handle);
                $decoded = @unserialize($variable);
                if ($decoded === false && $variable !== serialize(false)) {
                    error_log("Ошибка кеша файлов: повреждённые данные в файле $fileName");
                    return null;
                }
                return $decoded;
            }
        }
        return null;
    } // <--- ДОБАВИЛ ЗАКРЫВАЮЩУЮ СКОБКУ ЗДЕСЬ

    function write($fileName, $variable) {
        if ($this->use_redis) {
            $this->redis->set($fileName, serialize($variable));
            return;
        }

        $fileName = 'cache/' . $fileName;
        $handle = fopen($fileName, 'w');
        if ($handle) {
            fwrite($handle, serialize($variable));
            fclose($handle);
        }
    }

    function delete($fileName) {
        if ($this->use_redis) {
            $this->redis->del($fileName);
            return;
        }

        $fileName = 'cache/' . $fileName;
        @unlink($fileName);
    }
}
?>
