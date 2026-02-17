package com.worldmates.messenger.data.stickers

import android.content.Context
import com.worldmates.messenger.data.model.Sticker
import com.worldmates.messenger.data.model.StickerPack

/**
 * Вбудовані анімовані стікери
 *
 * Цей файл містить пакети стандартних анімованих стікерів,
 * які вбудовані в додаток і доступні без інтернету
 */
object EmbeddedStickerPacks {

    /**
     * Отримати всі вбудовані пакети
     */
    fun getAllEmbeddedPacks(): List<StickerPack> {
        return listOf(
            getEmotionsPack(),
            getAnimalsPack(),
            getCelebrationPack(),
            getGesturesPack(),
            getHeartsPack()
        )
    }

    /**
     * Пак 1: Емоції (Emotions) - 40 анімованих емоджі
     */
    private fun getEmotionsPack(): StickerPack {
        return StickerPack(
            id = -1, // Негативний ID для вбудованих паків
            name = "Емоції",
            description = "Анімовані емоції для спілкування",
            iconUrl = null,
            thumbnailUrl = null,
            author = "WorldMates",
            stickers = listOf(
                // Щасливі емоції
                Sticker(1001, -1, "lottie://emotions_happy", format = "lottie", emoji = "😊"),
                Sticker(1002, -1, "lottie://emotions_laughing", format = "lottie", emoji = "😂"),
                Sticker(1003, -1, "lottie://emotions_joy", format = "lottie", emoji = "😁"),
                Sticker(1004, -1, "lottie://emotions_heart_eyes", format = "lottie", emoji = "😍"),
                Sticker(1005, -1, "lottie://emotions_love", format = "lottie", emoji = "🥰"),
                Sticker(1006, -1, "lottie://emotions_starry_eyes", format = "lottie", emoji = "🤩"),
                Sticker(1007, -1, "lottie://emotions_grinning", format = "lottie", emoji = "😄"),
                Sticker(1008, -1, "lottie://emotions_smiling", format = "lottie", emoji = "☺️"),

                // Сумні емоції
                Sticker(1009, -1, "lottie://emotions_sad", format = "lottie", emoji = "😢"),
                Sticker(1010, -1, "lottie://emotions_crying", format = "lottie", emoji = "😭"),
                Sticker(1011, -1, "lottie://emotions_disappointed", format = "lottie", emoji = "😞"),
                Sticker(1012, -1, "lottie://emotions_worried", format = "lottie", emoji = "😟"),

                // Здивовані
                Sticker(1013, -1, "lottie://emotions_surprised", format = "lottie", emoji = "😮"),
                Sticker(1014, -1, "lottie://emotions_shocked", format = "lottie", emoji = "😲"),
                Sticker(1015, -1, "lottie://emotions_astonished", format = "lottie", emoji = "😳"),

                // Злі
                Sticker(1016, -1, "lottie://emotions_angry", format = "lottie", emoji = "😠"),
                Sticker(1017, -1, "lottie://emotions_rage", format = "lottie", emoji = "😡"),
                Sticker(1018, -1, "lottie://emotions_annoyed", format = "lottie", emoji = "😤"),

                // Інші
                Sticker(1019, -1, "lottie://emotions_thinking", format = "lottie", emoji = "🤔"),
                Sticker(1020, -1, "lottie://emotions_nervous", format = "lottie", emoji = "😅"),
                Sticker(1021, -1, "lottie://emotions_cool", format = "lottie", emoji = "😎"),
                Sticker(1022, -1, "lottie://emotions_sleeping", format = "lottie", emoji = "😴"),
                Sticker(1023, -1, "lottie://emotions_sick", format = "lottie", emoji = "🤒"),
                Sticker(1024, -1, "lottie://emotions_crazy", format = "lottie", emoji = "🤪"),
                Sticker(1025, -1, "lottie://emotions_party", format = "lottie", emoji = "🥳"),
                Sticker(1026, -1, "lottie://emotions_sneaky", format = "lottie", emoji = "😏"),
                Sticker(1027, -1, "lottie://emotions_silly", format = "lottie", emoji = "🤪"),
                Sticker(1028, -1, "lottie://emotions_drooling", format = "lottie", emoji = "🤤"),
                Sticker(1029, -1, "lottie://emotions_kissing", format = "lottie", emoji = "😘"),
                Sticker(1030, -1, "lottie://emotions_smirking", format = "lottie", emoji = "😏"),

                // Екстремальні емоції
                Sticker(1031, -1, "lottie://emotions_exploding_head", format = "lottie", emoji = "🤯"),
                Sticker(1032, -1, "lottie://emotions_dizzy", format = "lottie", emoji = "😵"),
                Sticker(1033, -1, "lottie://emotions_cold", format = "lottie", emoji = "🥶"),
                Sticker(1034, -1, "lottie://emotions_hot", format = "lottie", emoji = "🥵"),
                Sticker(1035, -1, "lottie://emotions_scared", format = "lottie", emoji = "😱"),
                Sticker(1036, -1, "lottie://emotions_hugging", format = "lottie", emoji = "🤗"),
                Sticker(1037, -1, "lottie://emotions_yawning", format = "lottie", emoji = "🥱"),
                Sticker(1038, -1, "lottie://emotions_vomiting", format = "lottie", emoji = "🤮"),
                Sticker(1039, -1, "lottie://emotions_shushing", format = "lottie", emoji = "🤫"),
                Sticker(1040, -1, "lottie://emotions_lying", format = "lottie", emoji = "🤥")
            ),
            stickerCount = 40,
            isActive = true,
            isAnimated = true
        )
    }

    /**
     * Пак 2: Тварини (Animals) - 40 анімованих тварин
     */
    private fun getAnimalsPack(): StickerPack {
        return StickerPack(
            id = -2,
            name = "Тварини",
            description = "Милі анімовані тварини",
            iconUrl = null,
            thumbnailUrl = null,
            author = "WorldMates",
            stickers = listOf(
                // Свійські тварини
                Sticker(2001, -2, "lottie://animals_dog", format = "lottie", emoji = "🐶"),
                Sticker(2002, -2, "lottie://animals_cat", format = "lottie", emoji = "🐱"),
                Sticker(2003, -2, "lottie://animals_hamster", format = "lottie", emoji = "🐹"),
                Sticker(2004, -2, "lottie://animals_rabbit", format = "lottie", emoji = "🐰"),
                Sticker(2005, -2, "lottie://animals_mouse", format = "lottie", emoji = "🐭"),

                // Ферма
                Sticker(2006, -2, "lottie://animals_cow", format = "lottie", emoji = "🐮"),
                Sticker(2007, -2, "lottie://animals_pig", format = "lottie", emoji = "🐷"),
                Sticker(2008, -2, "lottie://animals_chicken", format = "lottie", emoji = "🐔"),
                Sticker(2009, -2, "lottie://animals_horse", format = "lottie", emoji = "🐴"),
                Sticker(2010, -2, "lottie://animals_sheep", format = "lottie", emoji = "🐑"),

                // Дикі тварини
                Sticker(2011, -2, "lottie://animals_lion", format = "lottie", emoji = "🦁"),
                Sticker(2012, -2, "lottie://animals_tiger", format = "lottie", emoji = "🐯"),
                Sticker(2013, -2, "lottie://animals_bear", format = "lottie", emoji = "🐻"),
                Sticker(2014, -2, "lottie://animals_panda", format = "lottie", emoji = "🐼"),
                Sticker(2015, -2, "lottie://animals_koala", format = "lottie", emoji = "🐨"),
                Sticker(2016, -2, "lottie://animals_monkey", format = "lottie", emoji = "🐵"),
                Sticker(2017, -2, "lottie://animals_elephant", format = "lottie", emoji = "🐘"),
                Sticker(2018, -2, "lottie://animals_giraffe", format = "lottie", emoji = "🦒"),
                Sticker(2019, -2, "lottie://animals_zebra", format = "lottie", emoji = "🦓"),
                Sticker(2020, -2, "lottie://animals_deer", format = "lottie", emoji = "🦌"),

                // Птахи
                Sticker(2021, -2, "lottie://animals_bird", format = "lottie", emoji = "🐦"),
                Sticker(2022, -2, "lottie://animals_owl", format = "lottie", emoji = "🦉"),
                Sticker(2023, -2, "lottie://animals_penguin", format = "lottie", emoji = "🐧"),
                Sticker(2024, -2, "lottie://animals_duck", format = "lottie", emoji = "🦆"),
                Sticker(2025, -2, "lottie://animals_parrot", format = "lottie", emoji = "🦜"),

                // Морські
                Sticker(2026, -2, "lottie://animals_fish", format = "lottie", emoji = "🐠"),
                Sticker(2027, -2, "lottie://animals_dolphin", format = "lottie", emoji = "🐬"),
                Sticker(2028, -2, "lottie://animals_whale", format = "lottie", emoji = "🐳"),
                Sticker(2029, -2, "lottie://animals_octopus", format = "lottie", emoji = "🐙"),
                Sticker(2030, -2, "lottie://animals_turtle", format = "lottie", emoji = "🐢"),

                // Комахи та інші
                Sticker(2031, -2, "lottie://animals_butterfly", format = "lottie", emoji = "🦋"),
                Sticker(2032, -2, "lottie://animals_bee", format = "lottie", emoji = "🐝"),
                Sticker(2033, -2, "lottie://animals_ladybug", format = "lottie", emoji = "🐞"),
                Sticker(2034, -2, "lottie://animals_snail", format = "lottie", emoji = "🐌"),
                Sticker(2035, -2, "lottie://animals_spider", format = "lottie", emoji = "🕷️"),
                Sticker(2036, -2, "lottie://animals_frog", format = "lottie", emoji = "🐸"),
                Sticker(2037, -2, "lottie://animals_crocodile", format = "lottie", emoji = "🐊"),
                Sticker(2038, -2, "lottie://animals_snake", format = "lottie", emoji = "🐍"),
                Sticker(2039, -2, "lottie://animals_dinosaur", format = "lottie", emoji = "🦕"),
                Sticker(2040, -2, "lottie://animals_dragon", format = "lottie", emoji = "🐉")
            ),
            stickerCount = 40,
            isActive = true,
            isAnimated = true
        )
    }

    /**
     * Пак 3: Святкування (Celebration) - 40 анімацій
     */
    private fun getCelebrationPack(): StickerPack {
        return StickerPack(
            id = -3,
            name = "Святкування",
            description = "Анімації для свят та подій",
            iconUrl = null,
            thumbnailUrl = null,
            author = "WorldMates",
            stickers = listOf(
                Sticker(3001, -3, "lottie://celebration_confetti", format = "lottie", emoji = "🎊"),
                Sticker(3002, -3, "lottie://celebration_party", format = "lottie", emoji = "🎉"),
                Sticker(3003, -3, "lottie://celebration_balloon", format = "lottie", emoji = "🎈"),
                Sticker(3004, -3, "lottie://celebration_gift", format = "lottie", emoji = "🎁"),
                Sticker(3005, -3, "lottie://celebration_cake", format = "lottie", emoji = "🎂"),
                Sticker(3006, -3, "lottie://celebration_fireworks", format = "lottie", emoji = "🎆"),
                Sticker(3007, -3, "lottie://celebration_sparkles", format = "lottie", emoji = "✨"),
                Sticker(3008, -3, "lottie://celebration_tada", format = "lottie", emoji = "🎉"),
                Sticker(3009, -3, "lottie://celebration_champagne", format = "lottie", emoji = "🍾"),
                Sticker(3010, -3, "lottie://celebration_cheers", format = "lottie", emoji = "🥂"),

                // Свята
                Sticker(3011, -3, "lottie://celebration_christmas_tree", format = "lottie", emoji = "🎄"),
                Sticker(3012, -3, "lottie://celebration_santa", format = "lottie", emoji = "🎅"),
                Sticker(3013, -3, "lottie://celebration_snowman", format = "lottie", emoji = "⛄"),
                Sticker(3014, -3, "lottie://celebration_halloween", format = "lottie", emoji = "🎃"),
                Sticker(3015, -3, "lottie://celebration_ghost", format = "lottie", emoji = "👻"),
                Sticker(3016, -3, "lottie://celebration_heart", format = "lottie", emoji = "💝"),
                Sticker(3017, -3, "lottie://celebration_cupid", format = "lottie", emoji = "💘"),
                Sticker(3018, -3, "lottie://celebration_easter", format = "lottie", emoji = "🐣"),

                // Досягнення
                Sticker(3019, -3, "lottie://celebration_trophy", format = "lottie", emoji = "🏆"),
                Sticker(3020, -3, "lottie://celebration_medal", format = "lottie", emoji = "🏅"),
                Sticker(3021, -3, "lottie://celebration_crown", format = "lottie", emoji = "👑"),
                Sticker(3022, -3, "lottie://celebration_star", format = "lottie", emoji = "⭐"),
                Sticker(3023, -3, "lottie://celebration_diamond", format = "lottie", emoji = "💎"),
                Sticker(3024, -3, "lottie://celebration_fire", format = "lottie", emoji = "🔥"),
                Sticker(3025, -3, "lottie://celebration_rocket", format = "lottie", emoji = "🚀"),
                Sticker(3026, -3, "lottie://celebration_target", format = "lottie", emoji = "🎯"),

                // Музика та розваги
                Sticker(3027, -3, "lottie://celebration_music", format = "lottie", emoji = "🎵"),
                Sticker(3028, -3, "lottie://celebration_disco", format = "lottie", emoji = "🪩"),
                Sticker(3029, -3, "lottie://celebration_dance", format = "lottie", emoji = "💃"),
                Sticker(3030, -3, "lottie://celebration_microphone", format = "lottie", emoji = "🎤"),

                // Інше
                Sticker(3031, -3, "lottie://celebration_clap", format = "lottie", emoji = "👏"),
                Sticker(3032, -3, "lottie://celebration_ok", format = "lottie", emoji = "👌"),
                Sticker(3033, -3, "lottie://celebration_thumbs_up", format = "lottie", emoji = "👍"),
                Sticker(3034, -3, "lottie://celebration_victory", format = "lottie", emoji = "✌️"),
                Sticker(3035, -3, "lottie://celebration_fist_bump", format = "lottie", emoji = "👊"),
                Sticker(3036, -3, "lottie://celebration_rainbow", format = "lottie", emoji = "🌈"),
                Sticker(3037, -3, "lottie://celebration_sun", format = "lottie", emoji = "☀️"),
                Sticker(3038, -3, "lottie://celebration_moon", format = "lottie", emoji = "🌙"),
                Sticker(3039, -3, "lottie://celebration_lightning", format = "lottie", emoji = "⚡"),
                Sticker(3040, -3, "lottie://celebration_magic", format = "lottie", emoji = "✨")
            ),
            stickerCount = 40,
            isActive = true,
            isAnimated = true
        )
    }

    /**
     * Пак 4: Жести (Gestures) - 40 анімованих жестів
     */
    private fun getGesturesPack(): StickerPack {
        return StickerPack(
            id = -4,
            name = "Жести",
            description = "Анімовані жести рук",
            iconUrl = null,
            thumbnailUrl = null,
            author = "WorldMates",
            stickers = listOf(
                Sticker(4001, -4, "lottie://gestures_thumbs_up", format = "lottie", emoji = "👍"),
                Sticker(4002, -4, "lottie://gestures_thumbs_down", format = "lottie", emoji = "👎"),
                Sticker(4003, -4, "lottie://gestures_clap", format = "lottie", emoji = "👏"),
                Sticker(4004, -4, "lottie://gestures_wave", format = "lottie", emoji = "👋"),
                Sticker(4005, -4, "lottie://gestures_ok", format = "lottie", emoji = "👌"),
                Sticker(4006, -4, "lottie://gestures_victory", format = "lottie", emoji = "✌️"),
                Sticker(4007, -4, "lottie://gestures_crossed_fingers", format = "lottie", emoji = "🤞"),
                Sticker(4008, -4, "lottie://gestures_love", format = "lottie", emoji = "🤟"),
                Sticker(4009, -4, "lottie://gestures_rock", format = "lottie", emoji = "🤘"),
                Sticker(4010, -4, "lottie://gestures_call_me", format = "lottie", emoji = "🤙"),
                Sticker(4011, -4, "lottie://gestures_muscle", format = "lottie", emoji = "💪"),
                Sticker(4012, -4, "lottie://gestures_pray", format = "lottie", emoji = "🙏"),
                Sticker(4013, -4, "lottie://gestures_handshake", format = "lottie", emoji = "🤝"),
                Sticker(4014, -4, "lottie://gestures_fist_bump", format = "lottie", emoji = "👊"),
                Sticker(4015, -4, "lottie://gestures_raised_hand", format = "lottie", emoji = "✋"),
                Sticker(4016, -4, "lottie://gestures_pointing_up", format = "lottie", emoji = "☝️"),
                Sticker(4017, -4, "lottie://gestures_pointing_right", format = "lottie", emoji = "👉"),
                Sticker(4018, -4, "lottie://gestures_pointing_left", format = "lottie", emoji = "👈"),
                Sticker(4019, -4, "lottie://gestures_pointing_down", format = "lottie", emoji = "👇"),
                Sticker(4020, -4, "lottie://gestures_middle_finger", format = "lottie", emoji = "🖕"),
                Sticker(4021, -4, "lottie://gestures_vulcan_salute", format = "lottie", emoji = "🖖"),
                Sticker(4022, -4, "lottie://gestures_writing", format = "lottie", emoji = "✍️"),
                Sticker(4023, -4, "lottie://gestures_selfie", format = "lottie", emoji = "🤳"),
                Sticker(4024, -4, "lottie://gestures_nail_polish", format = "lottie", emoji = "💅"),
                Sticker(4025, -4, "lottie://gestures_flexed_biceps", format = "lottie", emoji = "💪"),
                Sticker(4026, -4, "lottie://gestures_raised_fist", format = "lottie", emoji = "✊"),
                Sticker(4027, -4, "lottie://gestures_oncoming_fist", format = "lottie", emoji = "👊"),
                Sticker(4028, -4, "lottie://gestures_left_facing_fist", format = "lottie", emoji = "🤛"),
                Sticker(4029, -4, "lottie://gestures_right_facing_fist", format = "lottie", emoji = "🤜"),
                Sticker(4030, -4, "lottie://gestures_clapping_hands", format = "lottie", emoji = "👏"),
                Sticker(4031, -4, "lottie://gestures_raising_hands", format = "lottie", emoji = "🙌"),
                Sticker(4032, -4, "lottie://gestures_open_hands", format = "lottie", emoji = "👐"),
                Sticker(4033, -4, "lottie://gestures_palms_up", format = "lottie", emoji = "🤲"),
                Sticker(4034, -4, "lottie://gestures_folded_hands", format = "lottie", emoji = "🙏"),
                Sticker(4035, -4, "lottie://gestures_pinching_hand", format = "lottie", emoji = "🤏"),
                Sticker(4036, -4, "lottie://gestures_pinched_fingers", format = "lottie", emoji = "🤌"),
                Sticker(4037, -4, "lottie://gestures_heart_hands", format = "lottie", emoji = "🫶"),
                Sticker(4038, -4, "lottie://gestures_index_pointing_at_viewer", format = "lottie", emoji = "🫵"),
                Sticker(4039, -4, "lottie://gestures_shaking_hands", format = "lottie", emoji = "🤝"),
                Sticker(4040, -4, "lottie://gestures_rightwards_hand", format = "lottie", emoji = "🫱")
            ),
            stickerCount = 40,
            isActive = true,
            isAnimated = true
        )
    }

    /**
     * Пак 5: Серця (Hearts) - 40 анімованих сердець
     */
    private fun getHeartsPack(): StickerPack {
        return StickerPack(
            id = -5,
            name = "Серця",
            description = "Анімовані серця та любов",
            iconUrl = null,
            thumbnailUrl = null,
            author = "WorldMates",
            stickers = listOf(
                // Кольорові серця
                Sticker(5001, -5, "lottie://hearts_red", format = "lottie", emoji = "❤️"),
                Sticker(5002, -5, "lottie://hearts_orange", format = "lottie", emoji = "🧡"),
                Sticker(5003, -5, "lottie://hearts_yellow", format = "lottie", emoji = "💛"),
                Sticker(5004, -5, "lottie://hearts_green", format = "lottie", emoji = "💚"),
                Sticker(5005, -5, "lottie://hearts_blue", format = "lottie", emoji = "💙"),
                Sticker(5006, -5, "lottie://hearts_purple", format = "lottie", emoji = "💜"),
                Sticker(5007, -5, "lottie://hearts_brown", format = "lottie", emoji = "🤎"),
                Sticker(5008, -5, "lottie://hearts_black", format = "lottie", emoji = "🖤"),
                Sticker(5009, -5, "lottie://hearts_white", format = "lottie", emoji = "🤍"),
                Sticker(5010, -5, "lottie://hearts_pink", format = "lottie", emoji = "🩷"),

                // Спеціальні серця
                Sticker(5011, -5, "lottie://hearts_sparkling", format = "lottie", emoji = "💖"),
                Sticker(5012, -5, "lottie://hearts_growing", format = "lottie", emoji = "💗"),
                Sticker(5013, -5, "lottie://hearts_beating", format = "lottie", emoji = "💓"),
                Sticker(5014, -5, "lottie://hearts_revolving", format = "lottie", emoji = "💞"),
                Sticker(5015, -5, "lottie://hearts_two", format = "lottie", emoji = "💕"),
                Sticker(5016, -5, "lottie://hearts_decorated", format = "lottie", emoji = "💝"),
                Sticker(5017, -5, "lottie://hearts_ribbon", format = "lottie", emoji = "💝"),
                Sticker(5018, -5, "lottie://hearts_arrow", format = "lottie", emoji = "💘"),
                Sticker(5019, -5, "lottie://hearts_cupid", format = "lottie", emoji = "💘"),
                Sticker(5020, -5, "lottie://hearts_broken", format = "lottie", emoji = "💔"),

                // Емоції з серцями
                Sticker(5021, -5, "lottie://hearts_face_with_hearts", format = "lottie", emoji = "🥰"),
                Sticker(5022, -5, "lottie://hearts_heart_eyes", format = "lottie", emoji = "😍"),
                Sticker(5023, -5, "lottie://hearts_kissing", format = "lottie", emoji = "😘"),
                Sticker(5024, -5, "lottie://hearts_kissing_with_heart", format = "lottie", emoji = "😘"),
                Sticker(5025, -5, "lottie://hearts_smiling_with_hearts", format = "lottie", emoji = "🥰"),

                // Символи любові
                Sticker(5026, -5, "lottie://hearts_kiss", format = "lottie", emoji = "💋"),
                Sticker(5027, -5, "lottie://hearts_love_letter", format = "lottie", emoji = "💌"),
                Sticker(5028, -5, "lottie://hearts_ring", format = "lottie", emoji = "💍"),
                Sticker(5029, -5, "lottie://hearts_rose", format = "lottie://hearts_rose", emoji = "🌹"),
                Sticker(5030, -5, "lottie://hearts_bouquet", format = "lottie", emoji = "💐"),

                // Анімовані ефекти
                Sticker(5031, -5, "lottie://hearts_explosion", format = "lottie", emoji = "💥"),
                Sticker(5032, -5, "lottie://hearts_sparkles", format = "lottie", emoji = "✨"),
                Sticker(5033, -5, "lottie://hearts_stars", format = "lottie", emoji = "⭐"),
                Sticker(5034, -5, "lottie://hearts_fire", format = "lottie", emoji = "🔥"),
                Sticker(5035, -5, "lottie://hearts_rainbow", format = "lottie", emoji = "🌈"),
                Sticker(5036, -5, "lottie://hearts_dizzy", format = "lottie", emoji = "💫"),
                Sticker(5037, -5, "lottie://hearts_glowing", format = "lottie", emoji = "💖"),
                Sticker(5038, -5, "lottie://hearts_floating", format = "lottie", emoji = "💕"),
                Sticker(5039, -5, "lottie://hearts_cascading", format = "lottie", emoji = "💞"),
                Sticker(5040, -5, "lottie://hearts_pulsing", format = "lottie", emoji = "💗")
            ),
            stickerCount = 40,
            isActive = true,
            isAnimated = true
        )
    }

    /**
     * Перевірка чи це вбудований стікер (по негативному ID паку)
     */
    fun isEmbeddedPack(packId: Long): Boolean {
        return packId < 0
    }

    /**
     * Отримати пак по ID
     */
    fun getPackById(packId: Long): StickerPack? {
        return getAllEmbeddedPacks().firstOrNull { it.id == packId }
    }

    /**
     * Отримати URL ресурсу для вбудованого стікера
     * Конвертує "lottie://emotions_happy" в фактичний ресурс
     */
    fun getEmbeddedStickerResourceUrl(context: Context, stickerUrl: String): String? {
        if (!stickerUrl.startsWith("lottie://")) {
            return null
        }

        val resourceName = stickerUrl.removePrefix("lottie://")
        val resourceId = context.resources.getIdentifier(
            resourceName,
            "raw",
            context.packageName
        )

        return if (resourceId != 0) {
            "android.resource://${context.packageName}/$resourceId"
        } else {
            // Якщо ресурс не знайдено, повертаємо fallback на emoji
            null
        }
    }
}
