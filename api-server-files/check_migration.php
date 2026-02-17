<?php
// Check Story Reactions Migration Status
// Run this from browser: http://yoursite.com/check_migration.php

// Load WoWonder init
$depth = '';
require_once($depth . 'assets/init.php');

header('Content-Type: text/html; charset=utf-8');

echo "<h1>Story Reactions Migration Check</h1>";
echo "<style>body { font-family: monospace; } .ok { color: green; } .error { color: red; }</style>";

// Check Wo_StoryReactions table
echo "<h2>1. Checking Wo_StoryReactions table:</h2>";
$check_table = mysqli_query($sqlConnect, "SHOW TABLES LIKE 'Wo_StoryReactions'");
if (mysqli_num_rows($check_table) > 0) {
    echo "<p class='ok'>✅ Table Wo_StoryReactions EXISTS</p>";

    // Show columns
    $columns = mysqli_query($sqlConnect, "DESCRIBE Wo_StoryReactions");
    echo "<pre>";
    while ($col = mysqli_fetch_assoc($columns)) {
        echo "  - {$col['Field']} ({$col['Type']})\n";
    }
    echo "</pre>";

    // Show count
    $count = mysqli_query($sqlConnect, "SELECT COUNT(*) as cnt FROM Wo_StoryReactions");
    $count_row = mysqli_fetch_assoc($count);
    echo "<p>Total reactions: {$count_row['cnt']}</p>";
} else {
    echo "<p class='error'>❌ Table Wo_StoryReactions DOES NOT EXIST</p>";
    echo "<p>Run migration: <code>migration_story_reactions_simple.sql</code></p>";
}

// Check wo_storyreactions table (lowercase)
echo "<h2>2. Checking wo_storyreactions table (lowercase):</h2>";
$check_table_lower = mysqli_query($sqlConnect, "SHOW TABLES LIKE 'wo_storyreactions'");
if (mysqli_num_rows($check_table_lower) > 0) {
    echo "<p class='ok'>✅ Table wo_storyreactions EXISTS</p>";
} else {
    echo "<p>⚠️ Table wo_storyreactions does not exist (optional)</p>";
}

// Check Wo_UserStory columns
echo "<h2>3. Checking Wo_UserStory columns:</h2>";
$columns = mysqli_query($sqlConnect, "DESCRIBE Wo_UserStory");
$has_comment_count = false;
$has_reaction_count = false;

echo "<pre>";
while ($col = mysqli_fetch_assoc($columns)) {
    if ($col['Field'] == 'comment_count') {
        $has_comment_count = true;
        echo "<span class='ok'>  ✅ comment_count ({$col['Type']})</span>\n";
    } elseif ($col['Field'] == 'reaction_count') {
        $has_reaction_count = true;
        echo "<span class='ok'>  ✅ reaction_count ({$col['Type']})</span>\n";
    } else {
        echo "     {$col['Field']} ({$col['Type']})\n";
    }
}
echo "</pre>";

if (!$has_comment_count) {
    echo "<p class='error'>❌ Column comment_count MISSING in Wo_UserStory</p>";
    echo "<p>Run: <code>ALTER TABLE Wo_UserStory ADD COLUMN comment_count int(11) NOT NULL DEFAULT 0;</code></p>";
}

if (!$has_reaction_count) {
    echo "<p class='error'>❌ Column reaction_count MISSING in Wo_UserStory</p>";
    echo "<p>Run: <code>ALTER TABLE Wo_UserStory ADD COLUMN reaction_count int(11) NOT NULL DEFAULT 0;</code></p>";
}

// Check Wo_StoryComments table
echo "<h2>4. Checking Wo_StoryComments table:</h2>";
$check_comments = mysqli_query($sqlConnect, "SHOW TABLES LIKE 'Wo_StoryComments'");
if (mysqli_num_rows($check_comments) > 0) {
    echo "<p class='ok'>✅ Table Wo_StoryComments EXISTS</p>";

    $count = mysqli_query($sqlConnect, "SELECT COUNT(*) as cnt FROM Wo_StoryComments");
    $count_row = mysqli_fetch_assoc($count);
    echo "<p>Total comments: {$count_row['cnt']}</p>";
} else {
    echo "<p class='error'>❌ Table Wo_StoryComments DOES NOT EXIST</p>";
}

// Test query
echo "<h2>5. Test Query:</h2>";
echo "<p>Testing reactions query...</p>";
$test_story_id = 1; // Change to real story ID
$test_query = "SELECT reaction, COUNT(*) as count FROM Wo_StoryReactions WHERE story_id = $test_story_id GROUP BY reaction";
$result = mysqli_query($sqlConnect, $test_query);

if ($result) {
    echo "<p class='ok'>✅ Query OK</p>";
    echo "<pre>Query: $test_query\n\nResults:\n";
    while ($row = mysqli_fetch_assoc($result)) {
        echo "  {$row['reaction']}: {$row['count']}\n";
    }
    echo "</pre>";
} else {
    echo "<p class='error'>❌ Query FAILED: " . mysqli_error($sqlConnect) . "</p>";
}

echo "<hr>";
echo "<h2>Summary:</h2>";
$all_ok = mysqli_num_rows($check_table) > 0 && $has_comment_count && $has_reaction_count;
if ($all_ok) {
    echo "<p class='ok' style='font-size: 20px;'>✅ All checks passed! Migration is complete.</p>";
} else {
    echo "<p class='error' style='font-size: 20px;'>❌ Migration incomplete. Please run migration_story_reactions_simple.sql</p>";
}
?>
