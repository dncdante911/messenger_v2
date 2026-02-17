/**
 * Модель Sequelize для таблицы wo_group_calls
 * Групповые звонки
 */

module.exports = (sequelize, DataTypes) => {
    const WoGroupCalls = sequelize.define('wo_group_calls', {
        id: {
            type: DataTypes.INTEGER(11),
            primaryKey: true,
            autoIncrement: true,
            allowNull: false
        },
        group_id: {
            type: DataTypes.INTEGER(11),
            allowNull: false,
            comment: 'ID группы из wo_groupchat'
        },
        initiated_by: {
            type: DataTypes.INTEGER(11),
            allowNull: false,
            comment: 'ID пользователя, который начал звонок'
        },
        call_type: {
            type: DataTypes.ENUM('audio', 'video'),
            allowNull: false,
            defaultValue: 'audio',
            comment: 'Тип звонка'
        },
        status: {
            type: DataTypes.ENUM('ringing', 'active', 'ended'),
            allowNull: false,
            defaultValue: 'ringing',
            comment: 'Статус группового звонка'
        },
        room_name: {
            type: DataTypes.STRING(100),
            allowNull: false,
            unique: true,
            comment: 'Уникальное имя комнаты'
        },
        created_at: {
            type: DataTypes.DATE,
            allowNull: false,
            defaultValue: DataTypes.NOW
        },
        started_at: {
            type: DataTypes.DATE,
            allowNull: true,
            comment: 'Когда звонок стал активным (первый участник подключился)'
        },
        ended_at: {
            type: DataTypes.DATE,
            allowNull: true
        },
        max_participants: {
            type: DataTypes.INTEGER(11),
            allowNull: true,
            comment: 'Максимальное количество участников одновременно'
        }
    }, {
        tableName: 'wo_group_calls',
        timestamps: false,
        indexes: [
            { fields: ['group_id'] },
            { fields: ['initiated_by'] },
            { fields: ['status'] },
            { fields: ['created_at'] },
            { fields: ['room_name'], unique: true }
        ]
    });

    return WoGroupCalls;
};
