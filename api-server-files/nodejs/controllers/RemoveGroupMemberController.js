const { Sequelize, Op, DataTypes } = require("sequelize");

const RemoveGroupMemberController = async (ctx, data, io, socket, callback) => {
  try {
    // Validation
    if (!data.group_id) {
      return callback({
        status: 400,
        error: "Group ID is required"
      });
    }

    if (!data.user_id_to_remove) {
      return callback({
        status: 400,
        error: "User ID to remove is required"
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

    // Check if requester is owner or admin
    const membership = await ctx.wo_groupchatusers.findOne({
      where: {
        group_id: data.group_id,
        user_id: userId,
        active: '1'
      }
    });

    if (!membership || (membership.role !== 'owner' && membership.role !== 'admin')) {
      return callback({
        status: 403,
        error: "Only group owner or admin can remove members"
      });
    }

    // Check if member to remove exists
    const memberToRemove = await ctx.wo_groupchatusers.findOne({
      where: {
        group_id: data.group_id,
        user_id: data.user_id_to_remove,
        active: '1'
      }
    });

    if (!memberToRemove) {
      return callback({
        status: 404,
        error: "Member not found in this group"
      });
    }

    // Owner cannot be removed
    if (memberToRemove.role === 'owner') {
      return callback({
        status: 403,
        error: "Group owner cannot be removed"
      });
    }

    // Admin can only remove regular members, not other admins or owner
    if (membership.role === 'admin' && (memberToRemove.role === 'admin' || memberToRemove.role === 'owner')) {
      return callback({
        status: 403,
        error: "Admins cannot remove other admins or owner"
      });
    }

    // Remove member (set active to '0')
    await ctx.wo_groupchatusers.update({
      active: '0'
    }, {
      where: {
        group_id: data.group_id,
        user_id: data.user_id_to_remove
      }
    });

    const response = {
      status: 200,
      message: "Member removed successfully",
      group_id: data.group_id,
      user_id: data.user_id_to_remove
    };

    callback(response);

    // Notify all group members
    const allMembers = await ctx.wo_groupchatusers.findAll({
      where: { group_id: data.group_id, active: '1' }
    });

    allMembers.forEach(member => {
      const memberSocketId = ctx.userIdHash[member.user_id];
      if (memberSocketId) {
        io.to(memberSocketId).emit('group_member_removed', {
          group_id: data.group_id,
          user_id: data.user_id_to_remove
        });
      }
    });

    // Notify removed member
    const removedSocketId = ctx.userIdHash[data.user_id_to_remove];
    if (removedSocketId) {
      io.to(removedSocketId).emit('removed_from_group', {
        group_id: data.group_id
      });
    }

  } catch (error) {
    console.error("Error in RemoveGroupMemberController:", error);
    callback({
      status: 500,
      error: "Failed to remove member",
      details: error.message
    });
  }
};

module.exports = { RemoveGroupMemberController };
