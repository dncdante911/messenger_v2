const { Sequelize, Op, DataTypes } = require("sequelize");

const SetGroupRoleController = async (ctx, data, io, socket, callback) => {
  try {
    // Validation
    if (!data.group_id) {
      return callback({
        status: 400,
        error: "Group ID is required"
      });
    }

    if (!data.user_id_to_update) {
      return callback({
        status: 400,
        error: "User ID to update is required"
      });
    }

    if (!data.new_role || !['admin', 'moderator', 'member'].includes(data.new_role)) {
      return callback({
        status: 400,
        error: "Invalid role. Must be: admin, moderator, or member"
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

    // Check if requester is owner
    const membership = await ctx.wo_groupchatusers.findOne({
      where: {
        group_id: data.group_id,
        user_id: userId,
        role: 'owner',
        active: '1'
      }
    });

    if (!membership) {
      return callback({
        status: 403,
        error: "Only group owner can change member roles"
      });
    }

    // Check if member to update exists
    const memberToUpdate = await ctx.wo_groupchatusers.findOne({
      where: {
        group_id: data.group_id,
        user_id: data.user_id_to_update,
        active: '1'
      }
    });

    if (!memberToUpdate) {
      return callback({
        status: 404,
        error: "Member not found in this group"
      });
    }

    // Owner role cannot be changed
    if (memberToUpdate.role === 'owner') {
      return callback({
        status: 403,
        error: "Owner role cannot be changed"
      });
    }

    // Cannot change own role
    if (data.user_id_to_update === userId) {
      return callback({
        status: 403,
        error: "Cannot change your own role"
      });
    }

    // Update role
    await ctx.wo_groupchatusers.update({
      role: data.new_role
    }, {
      where: {
        group_id: data.group_id,
        user_id: data.user_id_to_update
      }
    });

    // Get updated member data
    const updatedMember = await ctx.wo_groupchatusers.findOne({
      where: {
        group_id: data.group_id,
        user_id: data.user_id_to_update
      }
    });

    const userInfo = await ctx.wo_users.findOne({
      where: { user_id: data.user_id_to_update },
      attributes: ['user_id', 'username', 'name', 'avatar']
    });

    const response = {
      status: 200,
      message: "Member role updated successfully",
      member: {
        id: updatedMember.id,
        user_id: updatedMember.user_id,
        group_id: updatedMember.group_id,
        role: updatedMember.role,
        active: updatedMember.active,
        user: userInfo ? {
          user_id: userInfo.user_id,
          username: userInfo.username,
          name: userInfo.name,
          avatar: userInfo.avatar
        } : null
      }
    };

    callback(response);

    // Notify all group members
    const allMembers = await ctx.wo_groupchatusers.findAll({
      where: { group_id: data.group_id, active: '1' }
    });

    allMembers.forEach(member => {
      const memberSocketId = ctx.userIdHash[member.user_id];
      if (memberSocketId) {
        io.to(memberSocketId).emit('group_member_role_updated', {
          group_id: data.group_id,
          user_id: data.user_id_to_update,
          new_role: data.new_role
        });
      }
    });

  } catch (error) {
    console.error("Error in SetGroupRoleController:", error);
    callback({
      status: 500,
      error: "Failed to update member role",
      details: error.message
    });
  }
};

module.exports = { SetGroupRoleController };
