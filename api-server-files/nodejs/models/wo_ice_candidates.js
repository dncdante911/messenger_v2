/**
 * Модель Sequelize для таблицы wo_ice_candidates
 * Хранение ICE candidates для восстановления соединений
 */

module.exports = (sequelize, DataTypes) => {
    const WoIceCandidates = sequelize.define('wo_ice_candidates', {
        id: {
            type: DataTypes.INTEGER(11),
            primaryKey: true,
            autoIncrement: true,
            allowNull: false
        },
        room_name: {
            type: DataTypes.STRING(100),
            allowNull: false,
            comment: 'Комната, к которой относится candidate'
        },
        candidate: {
            type: DataTypes.TEXT,
            allowNull: false,
            comment: 'JSON ICE candidate'
        },
        sdp_mid: {
            type: DataTypes.STRING(50),
            allowNull: true
        },
        sdp_m_line_index: {
            type: DataTypes.INTEGER(11),
            allowNull: true
        },
        created_at: {
            type: DataTypes.DATE,
            allowNull: false,
            defaultValue: DataTypes.NOW
        }
    }, {
        tableName: 'wo_ice_candidates',
        timestamps: false,
        indexes: [
            { fields: ['room_name'] },
            { fields: ['created_at'] }
        ]
    });

    return WoIceCandidates;
};
