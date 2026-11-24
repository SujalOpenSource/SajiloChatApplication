
"use strict";

(() => {
    const originalRenderMessage = renderMessage;

    renderMessage = function (message) {
        if (message.type !== "SYSTEM") {
            originalRenderMessage(message);
            return;
        }

        if (
            message.id != null &&
            state.seenMessageIds.has(message.id)
        ) {
            return;
        }

        if (message.id != null) {
            state.seenMessageIds.add(message.id);
        }

        const container = document.getElementById("messageList");

        const notice = document.createElement("div");
        notice.className = "group-system-message";

        const content = document.createElement("span");
        content.textContent = message.content || "Group activity";

        const time = document.createElement("small");

        time.textContent = message.createdAt
            ? new Date(message.createdAt).toLocaleTimeString([], {
                hour: "2-digit",
                minute: "2-digit"
            })
            : "";

        notice.append(content, time);
        container.appendChild(notice);
    };
})();
