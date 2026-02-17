const funcs = require('../functions/functions');
const { Sequelize, Op, DataTypes } = require("sequelize");

const GetGroupDetailsController = async (ctx, data, io, socket, callback) => {
  try {
    // Validation
    if (!data.group_id) {
      return callback({
        status: 400,
        error: "Group ID is required"
      });
    }

    const userId = ctx.userHashUserId[data.from_id];
    if (!userId) {
      return callback({
        status: 401,
        error: "User not authenticated"
      });
    }

    // Check if group exists
    const group = await ctx.wo_groupchat.findOne({
      where: { group_id: data.group_id }
    });

    if (!group) {
      return callback({
        status: 404,
        error: "Group not found"
      });
    }

    // Check if user is a member (or if group is public)
    const membership = await ctx.wo_groupchatusers.findOne({
      where: {
        group_id: data.group_id,
        user_id: userId
      }
    });

    if (group.is_private === '1' && (!membership || membership.active !== '1')) {
      return callback({
        status: 403,
        error: "This is a private group"
      });
    }

    // Get all active members
    const members = await ctx.wo_groupchatusers.findAll({
      where: {
        group_id: data.group_id,
        active: '1'
      },
      order: [
        [Sequelize.literal("FIELD(role, 'owner', 'admin', 'moderator', 'member')"), 'ASC']
      ]
    });

    // Get user details for all members
    const memberUserIds = members.map(m => m.user_id);
    const memberUsers = await ctx.wo_users.findAll({
      where: { user_id: { [Op.in]: memberUserIds } },
      attributes: ['user_id', 'username', 'name', 'avatar', 'last_seen', 'verified']
    });

    // Combine member data with user info
    const membersWithDetails = members.map(member => {
      const user = memberUsers.find(u => u.user_id === member.user_id);
      return {
        id: member.id,
        user_id: member.user_id,
        group_id: member.group_id,
        role: member.role,
        active: member.active,
        last_seen: member.last_seen,
        user: user ? {
          user_id: user.user_id,
          username: user.username,
          name: user.name,
          avatar: user.avatar,
          last_seen: user.last_seen,
          verified: user.verified
        } : null
      };
    });

    // Get recent messages count
    const messagesCount = await ctx.wo_messages.count({
      where: { group_id: data.group_id }
    });

    // Parse settings
    let settings = null;
    if (group.settings) {
      try {
        settings = JSON.parse(group.settings);
      } catch (e) {
        settings = null;
      }
    }

    const response = {
      status: 200,
      group: {
        group_id: group.group_id,
        user_id: group.user_id,
        group_name: group.group_name,
        description: group.description,
        is_private: group.is_private,
        settings: settings,
        avatar: await funcs.Wo_GetMedia(ctx, group.avatar),
        time: group.time,
        type: group.type,
        members_count: members.length,
        messages_count: messagesCount,
        members: membersWithDetails,
        is_member: membership && membership.active === '1',
        my_role: membership ? membership.role : null
      }
    };

    callback(response);

  } catch (error) {
    console.error("Error in GetGroupDetailsController:", error);
    callback({
      status: 500,
      error: "Failed to get group details",
      details: error.message
    });
  }
};

module.exports = { GetGroupDetailsController };
