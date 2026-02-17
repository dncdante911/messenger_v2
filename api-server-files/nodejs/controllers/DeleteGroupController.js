const { Sequelize, Op, DataTypes } = require("sequelize");

const DeleteGroupController = async (ctx, data, io, socket, callback) => {
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

    // Check if user is owner
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
        error: "Only group owner can delete the group"
      });
    }

    // Get all members before deletion to notify them
    const members = await ctx.wo_groupchatusers.findAll({
      where: { group_id: data.group_id }
    });

    // Delete all messages in the group
    await ctx.wo_messages.destroy({
      where: { group_id: data.group_id }
    });

    // Delete all group members
    await ctx.wo_groupchatusers.destroy({
      where: { group_id: data.group_id }
    });

    // Delete the group
    await ctx.wo_groupchat.destroy({
      where: { group_id: data.group_id }
    });

    const response = {
      status: 200,
      message: "Group deleted successfully",
      group_id: data.group_id
    };

    callback(response);

    // Notify all members about group deletion
    members.forEach(member => {
      const memberSocketId = ctx.userIdHash[member.user_id];
      if (memberSocketId) {
        io.to(memberSocketId).emit('group_deleted', {
          group_id: data.group_id
        });
      }
    });

  } catch (error) {
    console.error("Error in DeleteGroupController:", error);
    callback({
      status: 500,
      error: "Failed to delete group",
      details: error.message
    });
  }
};

module.exports = { DeleteGroupController };
