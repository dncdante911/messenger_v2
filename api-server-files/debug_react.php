<?php
// Debug react_story.php
// Check what's wrong with reactions

error_reporting(E_ALL);
ini_set('display_errors', 1);

$depth = '';
require_once($depth . 'assets/init.php');

header('Content-Type: text/html; charset=utf-8');

echo "<h1>Debug react_story.php</h1>";
echo "<style>body { font-family: monospace; } .ok { color: green; } .error { color: red; }</style>";

// Simulate POST data
$_POST['id'] = 16; // Story ID from logs
$_POST['reaction'] = 'like';

echo "<h2>Simulating react_story.php...</h2>";
echo "<p>POST data:</p><pre>" . print_r($_POST, true) . "</pre>";

// Check wo global
echo "<h2>Checking \$wo['reactions_types']:</h2>";
if (isset($wo['reactions_types'])) {
    echo "<pre>" . print_r($wo['reactions_types'], true) . "</pre>";
} else {
    echo "<p class='error'>❌ \$wo['reactions_types'] NOT SET!</p>";
}

$reactions_types = array_keys($wo['reactions_types']);
echo "<h2>Reactions types:</h2>";
echo "<pre>" . print_r($reactions_types, true) . "</pre>";

// Check story exists
echo "<h2>Checking story existence:</h2>";
$story_id = Wo_Secure($_POST['id']);
$story = $db->where('id', $story_id)->getOne(T_USER_STORY);

if ($story) {
    echo "<p class='ok'>✅ Story found: id={$story->id}, user_id={$story->user_id}</p>";
} else {
    echo "<p class='error'>❌ Story NOT found!</p>";
}

// Check reaction exists
echo "<h2>Checking existing reaction:</h2>";
$is_reacted = $db->where('user_id', $wo['user']['user_id'])
                 ->where('story_id', $story_id)
                 ->getValue('Wo_StoryReactions', 'COUNT(*)');

echo "<p>Is reacted: $is_reacted</p>";

// Try to insert
echo "<h2>Trying to insert reaction:</h2>";
try {
    if ($is_reacted > 0) {
        echo "<p>Removing existing reaction...</p>";
        $db->where('user_id', $wo['user']['user_id'])
           ->where('story_id', $story_id)
           ->delete('Wo_StoryReactions');
        echo "<p class='ok'>✅ Reaction removed</p>";
    } else {
        echo "<p>Adding new reaction...</p>";
        $insert_id = $db->insert('Wo_StoryReactions', array(
            'user_id' => $wo['user']['user_id'],
            'story_id' => $story_id,
            'reaction' => Wo_Secure($_POST['reaction']),
            'time' => time()
        ));

        if ($insert_id) {
            echo "<p class='ok'>✅ Reaction inserted, id=$insert_id</p>";

            // Update counter
            $db->where('id', $story_id)->update(T_USER_STORY, array(
                'reaction_count' => $db->inc(1)
            ));
            echo "<p class='ok'>✅ Counter updated</p>";
        } else {
            echo "<p class='error'>❌ Insert failed: " . $db->getLastError() . "</p>";
        }
    }
} catch (Exception $e) {
    echo "<p class='error'>❌ Exception: " . $e->getMessage() . "</p>";
    echo "<pre>" . $e->getTraceAsString() . "</pre>";
}

// Check final count
echo "<h2>Final reaction count:</h2>";
$final_count = $db->where('story_id', $story_id)->getValue('Wo_StoryReactions', 'COUNT(*)');
echo "<p>Total reactions for story $story_id: $final_count</p>";
?>
