const funcs = require('../functions/functions')
const compiledTemplates = require('../compiledTemplates/compiledTemplates')
const socketEvents = require('../events/events')
const { Sequelize, Op, DataTypes } = require("sequelize");
const striptags = require('striptags');
const moment = require("moment")

const OnUserLoggedoffController = async (ctx, data, io, socket) => {
    if (!data || !data.from_id) return;
    const user_id = ctx.userHashUserId[data.from_id];
    if (!user_id) return;
    try {
        let followers = await ctx.wo_followers.findAll({
            attributes: ["following_id"],
            where: { follower_id: user_id, following_id: { [Op.not]: user_id } },
            raw: true
        })
        for (let follow of followers) {
            await io.to(String(follow.following_id)).emit("on_user_loggedoff", { user_id: user_id })
        }
    } catch (err) {
        console.error('[OnUserLoggedoffController] error:', err.message)
    }
};

module.exports = { OnUserLoggedoffController };