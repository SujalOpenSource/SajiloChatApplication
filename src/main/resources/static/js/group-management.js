
"use strict";

(() => {
    let currentGroup = null;
    let isLoading = false;

    const elements = {};

    document.addEventListener("DOMContentLoaded", initializeGroupManagement);

    function initializeGroupManagement() {
        elements.button = document.getElementById("groupManagementButton");
        elements.dialog = document.getElementById("groupManagementDialog");
        elements.title = document.getElementById("groupManagementTitle");
        elements.memberCount = document.getElementById("groupMemberCount");
        elements.memberList = document.getElementById("groupManagementMemberList");
        elements.leaveButton = document.getElementById("leaveGroupButton");
        elements.closeButton = document.getElementById("closeGroupManagementButton");

        const requiredElements = Object.values(elements);

        if (requiredElements.some(element => !element)) {
            console.error("Group management could not initialize. Check the group management HTML element IDs.");
            return;
        }

        elements.button.addEventListener("click", openGroupManagement);
        elements.closeButton.addEventListener("click", closeGroupManagement);
        elements.leaveButton.addEventListener("click", leaveGroup);

        elements.memberList.addEventListener("click", handleMemberAction);

        elements.dialog.addEventListener("click", event => {
            if (event.target === elements.dialog) {
                closeGroupManagement();
            }
        });

        // chat.js updates this element whenever a conversation is opened.
        const contactName = document.getElementById("chatContactName");

        if (contactName) {
            const observer = new MutationObserver(updateButtonVisibility);

            observer.observe(contactName, {
                childList: true,
                characterData: true,
                subtree: true
            });
        }

        updateButtonVisibility();
    }

    function getActiveGroupId() {
        if (
            !state.activeConversation ||
            state.activeConversation.type !== "GROUP"
        ) {
            return null;
        }

        return state.activeConversationId;
    }

    function updateButtonVisibility() {
        if (!elements.button) {
            return;
        }

        elements.button.classList.toggle(
            "hidden",
            !getActiveGroupId()
        );
    }

    async function openGroupManagement() {
        const groupId = getActiveGroupId();

        if (!groupId || isLoading) {
            return;
        }

        elements.dialog.showModal();
        elements.memberList.replaceChildren();

        const loading = document.createElement("p");
        loading.className = "loading-state";
        loading.textContent = "Loading group members...";

        elements.memberList.appendChild(loading);

        await loadGroupDetails(groupId);
    }

    async function loadGroupDetails(groupId) {
        if (isLoading) {
            return;
        }

        isLoading = true;

        try {
            const group = await apiRequest(`/groups/${groupId}`);

            // Ignore the response if the user switched conversations.
            if (Number(state.activeConversationId) !== Number(groupId)) {
                closeGroupManagement();
                return;
            }

            currentGroup = group;
            renderGroupDetails(group);

        } catch (error) {
            console.error("Could not load group details:", error);
            showToast(error.message || "Could not load group details.");
            elements.memberList.replaceChildren();

        } finally {
            isLoading = false;
        }
    }

    function renderGroupDetails(group) {
        const members = Array.isArray(group.members)
            ? group.members
            : [];

        const currentUserId = Number(state.currentUser.id);

        const currentMember = members.find(
            member => Number(member.userId) === currentUserId
        );

        const isAdmin = currentMember?.role === "ADMIN";

        elements.title.textContent = group.name || "Group settings";

        elements.memberCount.textContent =
            `${members.length} ${members.length === 1 ? "member" : "members"}`;

        elements.leaveButton.disabled = false;
        elements.leaveButton.textContent = "Leave group";

        elements.memberList.replaceChildren();

        if (members.length === 0) {
            const empty = document.createElement("p");
            empty.className = "loading-state";
            empty.textContent = "No group members found.";
            elements.memberList.appendChild(empty);
            return;
        }

        members.forEach(member => {
            const row = document.createElement("div");
            row.className = "group-member-row";

            const details = document.createElement("div");
            details.className = "group-member-details";

            const name = document.createElement("strong");

            const fullName = [
                member.firstName,
                member.lastName
            ].filter(Boolean).join(" ");

            name.textContent =
                fullName || member.username || `User ${member.userId}`;

            const role = document.createElement("span");
            role.className = "group-member-role";
            role.textContent = member.role === "ADMIN" ? "Admin" : "Member";

            if (Number(member.userId) === currentUserId) {
                const you = document.createElement("span");
                you.className = "group-member-you";
                you.textContent = "You";
                name.append(" ", you);
            }

            details.append(name, role);
            row.appendChild(details);

            const isCurrentUser =
                Number(member.userId) === currentUserId;

            if (isAdmin && !isCurrentUser) {
                const actions = document.createElement("div");
                actions.className = "group-member-actions";

                const roleButton = document.createElement("button");
                roleButton.type = "button";
                roleButton.className = "group-action-button";

                roleButton.dataset.action = "change-role";
                roleButton.dataset.userId = member.userId;

                roleButton.textContent =
                    member.role === "ADMIN"
                        ? "Make member"
                        : "Make admin";

                actions.appendChild(roleButton);

                // The backend does not allow removing another admin.
                // Demote an admin first, then the remove action becomes available.
                if (member.role !== "ADMIN") {
                    const removeButton = document.createElement("button");
                    removeButton.type = "button";
                    removeButton.className = "group-action-button danger-button";

                    removeButton.dataset.action = "remove-member";
                    removeButton.dataset.userId = member.userId;
                    removeButton.textContent = "Remove";

                    actions.appendChild(removeButton);
                }

                row.appendChild(actions);
            }

            elements.memberList.appendChild(row);
        });
    }

    async function handleMemberAction(event) {
        const button = event.target.closest("button[data-action]");

        if (!button || !currentGroup || isLoading) {
            return;
        }

        const userId = button.dataset.userId;
        const action = button.dataset.action;

        if (!userId) {
            return;
        }

        if (action === "change-role") {
            await changeMemberRole(userId);
        } else if (action === "remove-member") {
            await removeMember(userId);
        }
    }

    async function changeMemberRole(userId) {
        const member = currentGroup.members.find(
            item => Number(item.userId) === Number(userId)
        );

        if (!member) {
            return;
        }

        const newRole = member.role === "ADMIN" ? "MEMBER" : "ADMIN";

        const actionText = newRole === "ADMIN"
            ? "promote this member to admin"
            : "demote this admin to member";

        if (!window.confirm(`Are you sure you want to ${actionText}?`)) {
            return;
        }

        await performGroupAction(async () => {
            await apiRequest(
                `/groups/${currentGroup.id}/members/${userId}/role`,
                {
                    method: "PATCH",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify({
                        role: newRole
                    })
                }
            );

            showToast(
                newRole === "ADMIN"
                    ? "Member promoted to admin."
                    : "Admin demoted to member."
            );
        });
    }

    async function removeMember(userId) {
        const member = currentGroup.members.find(
            item => Number(item.userId) === Number(userId)
        );

        if (!member) {
            return;
        }

        const memberName =
            [member.firstName, member.lastName]
                .filter(Boolean)
                .join(" ") || member.username || "this member";

        if (!window.confirm(`Remove ${memberName} from this group?`)) {
            return;
        }

        await performGroupAction(async () => {
            await apiRequest(
                `/groups/${currentGroup.id}/members/${userId}`,
                {
                    method: "DELETE"
                }
            );

            showToast(`${memberName} was removed from the group.`);
        });
    }

    async function performGroupAction(action) {
        if (isLoading || !currentGroup) {
            return;
        }

        isLoading = true;

        try {
            await action();

            await loadGroupDetailsAfterAction();

        } catch (error) {
            console.error("Group management action failed:", error);
            showToast(error.message || "The group action failed.");

        } finally {
            isLoading = false;
        }
    }

    async function loadGroupDetailsAfterAction() {
        const groupId = currentGroup.id;

        const group = await apiRequest(`/groups/${groupId}`);

        if (Number(state.activeConversationId) !== Number(groupId)) {
            closeGroupManagement();
            return;
        }

        currentGroup = group;
        renderGroupDetails(group);
    }

    async function leaveGroup() {
        const groupId = getActiveGroupId();

        if (!groupId || isLoading) {
            return;
        }

        if (!window.confirm("Are you sure you want to leave this group?")) {
            return;
        }

        isLoading = true;
        elements.leaveButton.disabled = true;
        elements.leaveButton.textContent = "Leaving...";

        try {
            await apiRequest(`/groups/${groupId}/leave`, {
                method: "POST"
            });

            // Stop subscriptions to the group the user has left.
            if (state.messageSubscription) {
                state.messageSubscription.unsubscribe();
                state.messageSubscription = null;
            }

            if (state.typingSubscription) {
                state.typingSubscription.unsubscribe();
                state.typingSubscription = null;
            }

            state.activeConversationId = null;
            state.activeConversation = null;

            closeGroupManagement();

            document.getElementById("activeChat").classList.add("hidden");
            document.getElementById("emptyState").classList.remove("hidden");
            document.getElementById("messageList").replaceChildren();

            await loadConversations();

            updateButtonVisibility();
            showToast("You have left the group.");

        } catch (error) {
            console.error("Could not leave group:", error);
            showToast(error.message || "Could not leave the group.");

        } finally {
            isLoading = false;
            elements.leaveButton.disabled = false;
            elements.leaveButton.textContent = "Leave group";
        }
    }

    function closeGroupManagement() {
        if (elements.dialog?.open) {
            elements.dialog.close();
        }

        currentGroup = null;
    }
})();
