const funcs = require('../functions/functions');
const { Sequelize, Op, DataTypes } = require("sequelize");

const UpdateGroupController = async (ctx, data, io, socket, callback) => {
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

    // Check if user is owner or admin
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
        error: "Only group owner or admin can update group settings"
      });
    }

    // Prepare update data
    const updateData = {};

    if (data.group_name !== undefined && data.group_name.trim() !== "") {
      if (data.group_name.length > 255) {
        return callback({
          status: 400,
          error: "Group name is too long (max 255 characters)"
        });
      }
      updateData.group_name = data.group_name.trim();
    }

    if (data.description !== undefined) {
      updateData.description = data.description;
    }

    if (data.is_private !== undefined) {
      updateData.is_private = data.is_private === true || data.is_private === '1' ? '1' : '0';
    }

    if (data.settings !== undefined) {
      try {
        updateData.settings = typeof data.settings === 'string' ? data.settings : JSON.stringify(data.settings);
      } catch (e) {
        updateData.settings = null;
      }
    }

    if (data.avatar !== undefined) {
      updateData.avatar = data.avatar;
    }

    // Only owner can change certain settings
    if (membership.role !== 'owner') {
      delete updateData.is_private;
    }

    // Update group
    await ctx.wo_groupchat.update(updateData, {
      where: { group_id: data.group_id }
    });

    // Get updated group data
    const updatedGroup = await ctx.wo_groupchat.findOne({
      where: { group_id: data.group_id }
    });

    const response = {
      status: 200,
      message: "Group updated successfully",
      group: {
        group_id: updatedGroup.group_id,
        user_id: updatedGroup.user_id,
        group_name: updatedGroup.group_name,
        description: updatedGroup.description,
        is_private: updatedGroup.is_private,
        settings: updatedGroup.settings ? JSON.parse(updatedGroup.settings) : null,
        avatar: await funcs.Wo_GetMedia(ctx, updatedGroup.avatar),
        time: updatedGroup.time,
        type: updatedGroup.type
      }
    };

    callback(response);

    // Notify all group members about update
    const members = await ctx.wo_groupchatusers.findAll({
      where: { group_id: data.group_id, active: '1' }
    });

    members.forEach(member => {
      const memberSocketId = ctx.userIdHash[member.user_id];
      if (memberSocketId && member.user_id !== userId) {
        io.to(memberSocketId).emit('group_updated', {
          group: response.group
        });
      }
    });

  } catch (error) {
    console.error("Error in UpdateGroupController:", error);
    callback({
      status: 500,
      error: "Failed to update group",
      details: error.message
    });
  }
};

module.exports = { UpdateGroupController };
