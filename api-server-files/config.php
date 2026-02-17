<?php
/**
 * WorldMates Messenger - Root Configuration
 * Database credentials and site settings
 */

// Database credentials (from nodejs/config.json)
$sql_db_host = 'localhost';
$sql_db_user = 'social';
$sql_db_pass = '3344Frzaq0607DmC157';
$sql_db_name = 'socialhub';

// Site URL
$site_url = 'https://worldmates.club';

// Purchase code
$purchase_code = '49efabf2-a87b-4628-a3c7-50fe53bdb482';

// Additional settings
$config = array(
    'sql_db_host' => $sql_db_host,
    'sql_db_user' => $sql_db_user,
    'sql_db_pass' => $sql_db_pass,
    'sql_db_name' => $sql_db_name,
    'site_url' => $site_url,
    'purchase_code' => $purchase_code
);
