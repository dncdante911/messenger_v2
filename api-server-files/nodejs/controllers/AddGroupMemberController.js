const funcs = require('../functions/functions');
const { Sequelize, Op, DataTypes } = require("sequelize");

const AddGroupMemberController = async (ctx, data, io, socket, callback) => {
  try {
    // Validation
    if (!data.group_id) {
      return callback({
        status: 400,
        error: "Group ID is required"
      });
    }

    if (!data.user_id_to_add) {
      return callback({
        status: 400,
        error: "User ID to add is required"
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

    // Parse settings to check permissions
    let canAddMembers = false;
    if (group.settings) {
      try {
        const settings = JSON.parse(group.settings);
        canAddMembers = settings.members_can_invite === true || settings.members_can_invite === '1';
      } catch (e) {
        canAddMembers = false;
      }
    }

    // Check if user has permission to add members
    const membership = await ctx.wo_groupchatusers.findOne({
      where: {
        group_id: data.group_id,
        user_id: userId,
        active: '1'
      }
    });

    if (!membership) {
      return callback({
        status: 403,
        error: "You are not a member of this group"
      });
    }

    // Only owner, admin, or if settings allow, any member can add
    const canAdd = membership.role === 'owner' ||
                   membership.role === 'admin' ||
                   canAddMembers;

    if (!canAdd) {
      return callback({
        status: 403,
        error: "You don't have permission to add members"
      });
    }

    // Check if user to add exists
    const userToAdd = await ctx.wo_users.findOne({
      where: { user_id: data.user_id_to_add }
    });

    if (!userToAdd) {
      return callback({
        status: 404,
        error: "User to add not found"
      });
    }

    // Check if user is already a member
    const existingMember = await ctx.wo_groupchatusers.findOne({
      where: {
        group_id: data.group_id,
        user_id: data.user_id_to_add
      }
    });

    if (existingMember) {
      if (existingMember.active === '1') {
        return callback({
          status: 400,
          error: "User is already a member of this group"
        });
      } else {
        // Reactivate member
        await ctx.wo_groupchatusers.update({
          active: '1',
          role: 'member',
          last_seen: Math.floor(Date.now() / 1000).toString()
        }, {
          where: { id: existingMember.id }
        });
      }
    } else {
      // Add new member
      await ctx.wo_groupchatusers.create({
        user_id: data.user_id_to_add,
        group_id: data.group_id,
        role: 'member',
        active: '1',
        last_seen: Math.floor(Date.now() / 1000).toString()
      });
    }

    // Get updated member data
    const updatedMember = await ctx.wo_groupchatusers.findOne({
      where: {
        group_id: data.group_id,
        user_id: data.user_id_to_add
      }
    });

    const response = {
      status: 200,
      message: "Member added successfully",
      member: {
        id: updatedMember.id,
        user_id: updatedMember.user_id,
        group_id: updatedMember.group_id,
        role: updatedMember.role,
        active: updatedMember.active,
        user: {
          user_id: userToAdd.user_id,
          username: userToAdd.username,
          name: userToAdd.name,
          avatar: userToAdd.avatar
        }
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
        io.to(memberSocketId).emit('group_member_added', {
          group_id: data.group_id,
          member: response.member
        });
      }
    });

  } catch (error) {
    console.error("Error in AddGroupMemberController:", error);
    callback({
      status: 500,
      error: "Failed to add member",
      details: error.message
    });
  }
};

module.exports = { AddGroupMemberController };
