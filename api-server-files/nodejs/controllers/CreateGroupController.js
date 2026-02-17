const funcs = require('../functions/functions');
const { Sequelize, Op, DataTypes } = require("sequelize");

const CreateGroupController = async (ctx, data, io, socket, callback) => {
  try {
    // Validation
    if (!data.group_name || data.group_name.trim() === "") {
      return callback({
        status: 400,
        error: "Group name is required"
      });
    }

    if (data.group_name.length > 255) {
      return callback({
        status: 400,
        error: "Group name is too long (max 255 characters)"
      });
    }

    const userId = ctx.userHashUserId[data.from_id];
    if (!userId) {
      return callback({
        status: 401,
        error: "User not authenticated"
      });
    }

    // Validate member IDs if provided
    let memberIds = [];
    if (data.member_ids && Array.isArray(data.member_ids)) {
      memberIds = data.member_ids.filter(id => id && id > 0);
    }

    // Parse settings if provided
    let settings = null;
    if (data.settings) {
      try {
        settings = typeof data.settings === 'string' ? data.settings : JSON.stringify(data.settings);
      } catch (e) {
        settings = null;
      }
    }

    // Create group
    const newGroup = await ctx.wo_groupchat.create({
      user_id: userId,
      group_name: data.group_name.trim(),
      description: data.description || null,
      is_private: data.is_private === true || data.is_private === '1' ? '1' : '0',
      settings: settings,
      avatar: data.avatar || "upload/photos/d-group.jpg",
      time: Math.floor(Date.now() / 1000).toString(),
      type: 'group'
    });

    // Add creator as owner
    await ctx.wo_groupchatusers.create({
      user_id: userId,
      group_id: newGroup.group_id,
      role: 'owner',
      active: '1',
      last_seen: Math.floor(Date.now() / 1000).toString()
    });

    // Add members if provided
    if (memberIds.length > 0) {
      const memberPromises = memberIds.map(memberId =>
        ctx.wo_groupchatusers.create({
          user_id: memberId,
          group_id: newGroup.group_id,
          role: 'member',
          active: '1',
          last_seen: '0'
        }).catch(err => {
          console.log(`Failed to add member ${memberId}:`, err.message);
          return null;
        })
      );
      await Promise.all(memberPromises);
    }

    // Get full group data with members
    const groupData = await ctx.wo_groupchat.findOne({
      where: { group_id: newGroup.group_id }
    });

    const members = await ctx.wo_groupchatusers.findAll({
      where: { group_id: newGroup.group_id, active: '1' }
    });

    // Get user details for members
    const memberUserIds = members.map(m => m.user_id);
    const memberUsers = await ctx.wo_users.findAll({
      where: { user_id: { [Op.in]: memberUserIds } },
      attributes: ['user_id', 'username', 'name', 'avatar', 'last_seen']
    });

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
          last_seen: user.last_seen
        } : null
      };
    });

    const response = {
      status: 200,
      message: "Group created successfully",
      group: {
        group_id: groupData.group_id,
        user_id: groupData.user_id,
        group_name: groupData.group_name,
        description: groupData.description,
        is_private: groupData.is_private,
        settings: groupData.settings ? JSON.parse(groupData.settings) : null,
        avatar: await funcs.Wo_GetMedia(ctx, groupData.avatar),
        time: groupData.time,
        type: groupData.type,
        members_count: members.length,
        members: membersWithDetails
      }
    };

    callback(response);

    // Notify all members about new group
    members.forEach(member => {
      const memberSocketId = ctx.userIdHash[member.user_id];
      if (memberSocketId) {
        io.to(memberSocketId).emit('group_created', {
          group: response.group
        });
      }
    });

  } catch (error) {
    console.error("Error in CreateGroupController:", error);
    callback({
      status: 500,
      error: "Failed to create group",
      details: error.message
    });
  }
};

module.exports = { CreateGroupController };
