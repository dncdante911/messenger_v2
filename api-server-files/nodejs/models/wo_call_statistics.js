/**
 * Модель Sequelize для таблицы wo_call_statistics
 * Статистика качества звонков
 */

module.exports = (sequelize, DataTypes) => {
    const WoCallStatistics = sequelize.define('wo_call_statistics', {
        id: {
            type: DataTypes.INTEGER(11),
            primaryKey: true,
            autoIncrement: true,
            allowNull: false
        },
        call_id: {
            type: DataTypes.INTEGER(11),
            allowNull: true,
            comment: 'ID из wo_calls (для 1-на-1)'
        },
        group_call_id: {
            type: DataTypes.INTEGER(11),
            allowNull: true,
            comment: 'ID из wo_group_calls (для групповых)'
        },
        user_id: {
            type: DataTypes.INTEGER(11),
            allowNull: false,
            comment: 'ID пользователя'
        },
        avg_bitrate: {
            type: DataTypes.INTEGER(11),
            allowNull: true,
            comment: 'Средний битрейт в kbps'
        },
        avg_packet_loss: {
            type: DataTypes.DECIMAL(5, 2),
            allowNull: true,
            comment: 'Средняя потеря пакетов в %'
        },
        avg_jitter: {
            type: DataTypes.INTEGER(11),
            allowNull: true,
            comment: 'Средний jitter в ms'
        },
        avg_rtt: {
            type: DataTypes.INTEGER(11),
            allowNull: true,
            comment: 'Средний RTT (Round Trip Time) в ms'
        },
        connection_quality: {
            type: DataTypes.ENUM('excellent', 'good', 'fair', 'poor'),
            allowNull: true,
            comment: 'Общая оценка качества'
        },
        created_at: {
            type: DataTypes.DATE,
            allowNull: false,
            defaultValue: DataTypes.NOW
        }
    }, {
        tableName: 'wo_call_statistics',
        timestamps: false,
        indexes: [
            { fields: ['call_id'] },
            { fields: ['group_call_id'] },
            { fields: ['user_id'] },
            { fields: ['created_at'] }
        ]
    });

    return WoCallStatistics;
};
