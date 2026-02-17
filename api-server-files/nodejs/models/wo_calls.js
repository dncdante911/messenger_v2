/**
 * Модель Sequelize для таблицы wo_calls
 * 1-на-1 звонки между пользователями
 */

module.exports = (sequelize, DataTypes) => {
    const WoCalls = sequelize.define('wo_calls', {
        id: {
            type: DataTypes.INTEGER(11),
            primaryKey: true,
            autoIncrement: true,
            allowNull: false
        },
        from_id: {
            type: DataTypes.INTEGER(11),
            allowNull: false,
            comment: 'ID пользователя, который инициировал звонок'
        },
        to_id: {
            type: DataTypes.INTEGER(11),
            allowNull: false,
            comment: 'ID пользователя-получателя'
        },
        call_type: {
            type: DataTypes.ENUM('audio', 'video'),
            allowNull: false,
            defaultValue: 'audio',
            comment: 'Тип звонка: audio или video'
        },
        status: {
            type: DataTypes.ENUM('ringing', 'connected', 'ended', 'missed', 'rejected', 'failed'),
            allowNull: false,
            defaultValue: 'ringing',
            comment: 'Статус звонка'
        },
        room_name: {
            type: DataTypes.STRING(100),
            allowNull: false,
            unique: true,
            comment: 'Уникальное имя комнаты для WebRTC'
        },
        sdp_offer: {
            type: DataTypes.TEXT('long'),
            allowNull: true,
            comment: 'SDP offer от инициатора'
        },
        sdp_answer: {
            type: DataTypes.TEXT('long'),
            allowNull: true,
            comment: 'SDP answer от получателя'
        },
        created_at: {
            type: DataTypes.DATE,
            allowNull: false,
            defaultValue: DataTypes.NOW,
            comment: 'Время инициации звонка'
        },
        accepted_at: {
            type: DataTypes.DATE,
            allowNull: true,
            comment: 'Время принятия звонка'
        },
        ended_at: {
            type: DataTypes.DATE,
            allowNull: true,
            comment: 'Время завершения звонка'
        },
        duration: {
            type: DataTypes.INTEGER(11),
            allowNull: true,
            comment: 'Длительность звонка в секундах'
        }
    }, {
        tableName: 'wo_calls',
        timestamps: false,
        indexes: [
            { fields: ['from_id'] },
            { fields: ['to_id'] },
            { fields: ['status'] },
            { fields: ['created_at'] },
            { fields: ['room_name'], unique: true }
        ]
    });

    return WoCalls;
};
