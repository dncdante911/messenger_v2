const { Sequelize, Op, DataTypes } = require("sequelize");

const LeaveGroupController = async (ctx, data, io, socket, callback) => {
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

    // Check if user is a member
    const membership = await ctx.wo_groupchatusers.findOne({
      where: {
        group_id: data.group_id,
        user_id: userId,
        active: '1'
      }
    });

    if (!membership) {
      return callback({
        status: 404,
        error: "You are not a member of this group"
      });
    }

    // Owner cannot leave (must transfer ownership or delete group)
    if (membership.role === 'owner') {
      return callback({
        status: 403,
        error: "Owner cannot leave the group. Transfer ownership or delete the group instead."
      });
    }

    // Remove member (set active to '0')
    await ctx.wo_groupchatusers.update({
      active: '0'
    }, {
      where: {
        group_id: data.group_id,
        user_id: userId
      }
    });

    const response = {
      status: 200,
      message: "Left group successfully",
      group_id: data.group_id
    };

    callback(response);

    // Notify remaining group members
    const allMembers = await ctx.wo_groupchatusers.findAll({
      where: { group_id: data.group_id, active: '1' }
    });

    allMembers.forEach(member => {
      const memberSocketId = ctx.userIdHash[member.user_id];
      if (memberSocketId) {
        io.to(memberSocketId).emit('group_member_left', {
          group_id: data.group_id,
          user_id: userId
        });
      }
    });

  } catch (error) {
    console.error("Error in LeaveGroupController:", error);
    callback({
      status: 500,
      error: "Failed to leave group",
      details: error.message
    });
  }
};

module.exports = { LeaveGroupController };
