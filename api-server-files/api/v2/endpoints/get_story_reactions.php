<?php
// Get Story Reactions API
// Отримати всі реакції до story або підрахунок реакцій

if (!empty($_POST['story_id']) && is_numeric($_POST['story_id']) && $_POST['story_id'] > 0) {
    $story_id = Wo_Secure($_POST['story_id']);
    $limit = (!empty($_POST['limit']) && is_numeric($_POST['limit'])) ? Wo_Secure($_POST['limit']) : 20;
    $offset = (!empty($_POST['offset']) && is_numeric($_POST['offset'])) ? Wo_Secure($_POST['offset']) : 0;

    // Перевіряємо чи існує story
    $story = $db->where('id', $story_id)->getOne(T_USER_STORY);
    if (!$story) {
        $error_code = 6;
        $error_message = 'Story not found';
    } else {
        // Отримуємо підрахунок реакцій по типах
        $reaction_counts = array(
            'like' => 0,
            'love' => 0,
            'haha' => 0,
            'wow' => 0,
            'sad' => 0,
            'angry' => 0,
            'total' => 0
        );

        // Підраховуємо кількість кожного типу реакції
        $reactions_stats = $db->where('story_id', $story_id)
                              ->groupBy('reaction')
                              ->get('Wo_StoryReactions', null, 'reaction, COUNT(*) as count');

        foreach ($reactions_stats as $stat) {
            if (isset($reaction_counts[$stat->reaction])) {
                $reaction_counts[$stat->reaction] = (int)$stat->count;
                $reaction_counts['total'] += (int)$stat->count;
            }
        }

        // Перевіряємо чи є реакція від поточного користувача
        $user_reaction = $db->where('story_id', $story_id)
                           ->where('user_id', $wo['user']['user_id'])
                           ->getOne('Wo_StoryReactions');

        $is_reacted = !empty($user_reaction);
        $user_reaction_type = $is_reacted ? $user_reaction->reaction : null;

        // Якщо потрібен список користувачів з реакціями
        $users = array();
        if (!empty($_POST['get_users']) && $_POST['get_users'] == 1) {
            $reactions = $db->where('story_id', $story_id)
                           ->orderBy('time', 'DESC')
                           ->get('Wo_StoryReactions', array($offset, $limit));

            foreach ($reactions as $reaction) {
                $user_data = Wo_UserData($reaction->user_id);
                if ($user_data) {
                    $users[] = array(
                        'user_id' => $user_data['user_id'],
                        'username' => $user_data['username'],
                        'first_name' => $user_data['first_name'],
                        'last_name' => $user_data['last_name'],
                        'avatar' => $user_data['avatar'],
                        'reaction' => $reaction->reaction,
                        'time' => $reaction->time
                    );
                }
            }
        }

        $response_data = array(
            'api_status' => 200,
            'reactions' => $reaction_counts,
            'is_reacted' => $is_reacted,
            'type' => $user_reaction_type,
            'users' => $users
        );
    }
} else {
    $error_code = 5;
    $error_message = 'story_id can not be empty.';
}
