/**
 * Модель Sequelize для таблицы wo_group_call_participants
 * Участники групповых звонков
 */

module.exports = (sequelize, DataTypes) => {
    const WoGroupCallParticipants = sequelize.define('wo_group_call_participants', {
        id: {
            type: DataTypes.INTEGER(11),
            primaryKey: true,
            autoIncrement: true,
            allowNull: false
        },
        call_id: {
            type: DataTypes.INTEGER(11),
            allowNull: false,
            comment: 'ID из wo_group_calls'
        },
        user_id: {
            type: DataTypes.INTEGER(11),
            allowNull: false,
            comment: 'ID участника'
        },
        joined_at: {
            type: DataTypes.DATE,
            allowNull: false,
            defaultValue: DataTypes.NOW,
            comment: 'Когда присоединился'
        },
        left_at: {
            type: DataTypes.DATE,
            allowNull: true,
            comment: 'Когда покинул звонок'
        },
        duration: {
            type: DataTypes.INTEGER(11),
            allowNull: true,
            comment: 'Длительность участия в секундах'
        },
        audio_enabled: {
            type: DataTypes.BOOLEAN,
            allowNull: false,
            defaultValue: true,
            comment: 'Включен ли микрофон'
        },
        video_enabled: {
            type: DataTypes.BOOLEAN,
            allowNull: false,
            defaultValue: false,
            comment: 'Включена ли камера'
        }
    }, {
        tableName: 'wo_group_call_participants',
        timestamps: false,
        indexes: [
            { fields: ['call_id'] },
            { fields: ['user_id'] },
            { fields: ['joined_at'] }
        ]
    });

    return WoGroupCallParticipants;
};
