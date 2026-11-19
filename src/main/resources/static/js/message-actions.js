
"use strict";

(() => {
    const originalRenderMessage = renderMessage;

    /*
     * Extend the existing message renderer.
     * chat.js itself remains unchanged.
     */
    renderMessage = function (message) {
        originalRenderMessage(message);

        if (message.id == null) {
            return;
        }

        const messageList = document.getElementById("messageList");
        const row = messageList?.lastElementChild;

        if (!row || !row.classList.contains("message-row")) {
            return;
        }

        row.dataset.messageId = String(message.id);

        const senderId =
            message.sender?.id ??
            message.sender?.userId ??
            message.senderId;

        const isOwnMessage =
            senderId != null &&
            state.currentUser?.id != null &&
            Number(senderId) === Number(state.currentUser.id);

        const actions = document.createElement("div");
        actions.className = "message-actions";

        const menuButton = document.createElement("button");
        menuButton.type = "button";
        menuButton.className = "message-actions-trigger";
        menuButton.textContent = "⋯";
        menuButton.title = "Message options";
        menuButton.setAttribute("aria-label", "Message options");

        const menu = document.createElement("div");
        menu.className = "message-actions-menu hidden";

        const deleteForMe = document.createElement("button");
        deleteForMe.type = "button";
        deleteForMe.textContent = "Delete for me";
        deleteForMe.dataset.action = "delete-for-me";

        menu.appendChild(deleteForMe);

        if (isOwnMessage) {
            const deleteForEveryone = document.createElement("button");
            deleteForEveryone.type = "button";
            deleteForEveryone.textContent = "Delete for everyone";
            deleteForEveryone.dataset.action = "delete-for-everyone";

            menu.appendChild(deleteForEveryone);
        }

        menuButton.addEventListener("click", event => {
            event.stopPropagation();

            document
                .querySelectorAll(".message-actions-menu")
                .forEach(otherMenu => {
                    if (otherMenu !== menu) {
                        otherMenu.classList.add("hidden");
                    }
                });

            menu.classList.toggle("hidden");
        });

        actions.append(menuButton, menu);
        row.appendChild(actions);
    };

    document.addEventListener("click", event => {
        if (!event.target.closest(".message-actions")) {
            document
                .querySelectorAll(".message-actions-menu")
                .forEach(menu => menu.classList.add("hidden"));
        }
    });

    document.addEventListener("click", async event => {
        const actionButton = event.target.closest(
            ".message-actions-menu button[data-action]"
        );

        if (!actionButton) {
            return;
        }

        const row = actionButton.closest(".message-row");
        const messageId = row?.dataset.messageId;
        const action = actionButton.dataset.action;

        if (!messageId || !row) {
            return;
        }

        const deleteForEveryone =
            action === "delete-for-everyone";

        const confirmation = deleteForEveryone
            ? "Delete this message for everyone? This cannot be undone."
            : "Delete this message from your conversation?";

        if (!window.confirm(confirmation)) {
            return;
        }

        actionButton.disabled = true;

        try {
            const endpoint = deleteForEveryone
                ? `/messages/${messageId}`
                : `/messages/${messageId}/me`;

            await apiRequest(endpoint, {
                method: "DELETE"
            });

            row.remove();

            showToast(
                deleteForEveryone
                    ? "Message deleted for everyone."
                    : "Message deleted for you."
            );

        } catch (error) {
            console.error("Message deletion failed:", error);

            showToast(
                error.message || "Could not delete this message."
            );

            actionButton.disabled = false;
        }
    });
})();
