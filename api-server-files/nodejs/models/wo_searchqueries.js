'use strict';

/**
 * Wo_SearchQueries — хранит историю поиска пользователя.
 *
 * is_saved = 0 → недавний (recent) поиск, автоочистка при переполнении
 * is_saved = 1 → сохранённый (bookmarked) поиск, остаётся навсегда
 */
module.exports = (sequelize, DataTypes) =>
    sequelize.define('Wo_SearchQueries', {
        id:         { type: DataTypes.INTEGER.UNSIGNED, autoIncrement: true, primaryKey: true },
        // ID пользователя-владельца запроса
        user_id:    { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
        // Текст поискового запроса
        query:      { type: DataTypes.STRING(255), allowNull: false, defaultValue: '' },
        // 0 = недавний, 1 = сохранённый
        is_saved:   { type: DataTypes.TINYINT, allowNull: false, defaultValue: 0 },
        // Unix timestamp создания
        created_at: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false, defaultValue: 0 },
    }, {
        tableName:  'Wo_SearchQueries',
        timestamps: false,
    });
