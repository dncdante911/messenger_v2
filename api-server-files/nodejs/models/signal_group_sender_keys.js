'use strict';

/**
 * Sequelize модель для таблиці signal_group_sender_keys.
 *
 * Signal Protocol — Sender Key Distribution для групових чатів.
 *
 * Сервер зберігає ЛИШЕ зашифровані payload-и (store-and-forward).
 * Відкритих/приватних ключів групи тут немає.
 * Розшифрувати distribution може лише recipient за допомогою свого
 * приватного Signal-ключа (через Double Ratchet з sender).
 *
 * Колонки:
 *   id            — автоінкремент первинний ключ
 *   group_id      — ID групи (wo_groupchat.group_id)
 *   sender_id     — хто розповсюджує свій SenderKey
 *   recipient_id  — кому призначений зашифрований payload
 *   distribution  — зашифрований SenderKeyDistributionMessage (Base64)
 *   delivered     — 0=очікує, 1=доставлено
 *   created_at    — час створення
 *   delivered_at  — час підтвердження доставки
 */
module.exports = function(sequelize, DataTypes) {
    return sequelize.define('signal_group_sender_keys', {
        id: {
            type:          DataTypes.BIGINT.UNSIGNED,
            autoIncrement: true,
            primaryKey:    true,
        },
        group_id: {
            type:      DataTypes.INTEGER.UNSIGNED,
            allowNull: false,
            comment:   'ID групового чату (wo_groupchat.group_id)',
        },
        sender_id: {
            type:      DataTypes.INTEGER.UNSIGNED,
            allowNull: false,
            comment:   'ID учасника, що розповсюджує свій SenderKey',
        },
        recipient_id: {
            type:      DataTypes.INTEGER.UNSIGNED,
            allowNull: false,
            comment:   'ID учасника, якому призначений цей payload',
        },
        distribution: {
            type:      DataTypes.TEXT('medium'),
            allowNull: false,
            comment:   'Зашифрований SenderKeyDistributionMessage (Base64)',
        },
        delivered: {
            type:         DataTypes.TINYINT(1),
            allowNull:    false,
            defaultValue: 0,
            comment:      '0=очікує доставки, 1=доставлено',
        },
        created_at: {
            type:         DataTypes.DATE,
            allowNull:    false,
            defaultValue: DataTypes.NOW,
        },
        delivered_at: {
            type:      DataTypes.DATE,
            allowNull: true,
            defaultValue: null,
        },
    }, {
        sequelize,
        tableName:  'signal_group_sender_keys',
        timestamps: false,
    });
};
