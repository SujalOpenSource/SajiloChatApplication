
"use strict";

/* =========================================
   SAJILOCHAT - ATTACHMENT MANAGEMENT
========================================= */

const AttachmentManager = (() => {

    const API_BASE = "/api/v1";

    const ALLOWED_EXTENSIONS = [
        "jpg", "jpeg", "png", "gif", "webp",
        "pdf", "txt", "doc", "docx"
    ];

    const MAX_FILE_SIZE = 10 * 1024 * 1024;

    let attachmentSubscription = null;
    let uploadInProgress = false;

    const loadedMessageIds = new Set();
    const pendingMessageLoads = new Set();
    const pendingAttachments = new Map();

    /* =========================================
       INITIALIZATION
    ========================================= */

    function initialize() {
        const messageForm = document.getElementById("messageForm");

        if (!messageForm) {
            console.error("AttachmentManager: message form not found.");
            return;
        }

        addAttachmentStyles();
        addAttachmentControls();
        observeMessageList();

        console.log("SajiloChat attachment manager initialized.");
    }

    /* =========================================
       ATTACHMENT BUTTON AND INPUT
    ========================================= */

    function addAttachmentControls() {

        const messageForm = document.getElementById("messageForm");

        if (document.getElementById("attachmentButton")) {
            return;
        }

        const button = document.createElement("button");

        button.id = "attachmentButton";
        button.type = "button";
        button.className = "attachment-button";
        button.title = "Attach image or document";
        button.setAttribute("aria-label", "Attach image or document");
        button.textContent = "📎";

        const input = document.createElement("input");

        input.id = "attachmentInput";
        input.type = "file";
        input.accept = ALLOWED_EXTENSIONS
            .map(extension => "." + extension)
            .join(",");

        input.hidden = true;

        button.addEventListener("click", () => {
            if (!state.activeConversationId) {
                showMessage("Open a conversation first.");
                return;
            }

            input.click();
        });

        input.addEventListener("change", async () => {

            const file = input.files?.[0];

            if (!file) {
                return;
            }

            await uploadFile(file);

            input.value = "";
        });

        const sendButton = document.getElementById("sendButton");

        if (sendButton) {
            messageForm.insertBefore(button, sendButton);
        } else {
            messageForm.appendChild(button);
        }

        messageForm.appendChild(input);

    }

    /* =========================================
       FILE VALIDATION
    ========================================= */

    function validateFile(file) {

        if (!file) {
            throw new Error("Please select a file.");
        }

        if (file.size === 0) {
            throw new Error("Empty files cannot be uploaded.");
        }

        if (file.size > MAX_FILE_SIZE) {
            throw new Error("Maximum attachment size is 10 MB.");
        }

        const extension = file.name
            .split(".")
            .pop()
            .toLowerCase();

        if (!ALLOWED_EXTENSIONS.includes(extension)) {
            throw new Error("This file type is not supported.");
        }

    }

    /* =========================================
       CSRF TOKEN
    ========================================= */

    function getCsrfToken() {

        const cookie = document.cookie
            .split("; ")
            .find(row => row.startsWith("XSRF-TOKEN="));

        if (cookie) {
            return decodeURIComponent(
                cookie.substring("XSRF-TOKEN=".length)
            );
        }

        return state.csrfToken;
    }

    /* =========================================
       UPLOAD FILE
    ========================================= */

    async function uploadFile(file) {

        if (uploadInProgress) {
            showMessage("Please wait for the current upload.");
            return;
        }

        try {

            validateFile(file);

            if (!state.activeConversationId) {
                throw new Error("No active conversation.");
            }

            uploadInProgress = true;

            const messageId = await createAttachmentMessage();

            const formData = new FormData();

            formData.append("file", file);

            const csrfToken = getCsrfToken();

            const headers = {};

            if (csrfToken) {
                headers["X-XSRF-TOKEN"] = csrfToken;
            }

            showMessage("Uploading " + file.name + "...");

            const response = await fetch(
                `${API_BASE}/messages/${messageId}/attachments`,
                {
                    method: "POST",
                    credentials: "same-origin",
                    headers,
                    body: formData
                }
            );

            const result = await response.json().catch(() => null);

            if (!response.ok) {

                throw new Error(
                    result?.message ||
                    result?.detail ||
                    "Attachment upload failed."
                );

            }

            displayAttachment(messageId, result);

            showMessage("File uploaded successfully.");

            loadedMessageIds.add(messageId);

        } catch (error) {

            console.error("Attachment upload failed:", error);

            showMessage(
                error.message || "Could not upload attachment."
            );

        } finally {

            uploadInProgress = false;

        }

    }

    /* =========================================
       CREATE MESSAGE FOR ATTACHMENT
    ========================================= */

    async function createAttachmentMessage() {

        const conversationId = state.activeConversationId;

        const csrfToken = getCsrfToken();

        const headers = {
            "Content-Type": "application/json"
        };

        if (csrfToken) {
            headers["X-XSRF-TOKEN"] = csrfToken;
        }

        /*
         * The attachment API requires an existing message.
         * Create a normal message first, then upload the file.
         */

        const response = await fetch(
            `${API_BASE}/conversations/${conversationId}/messages`,
            {
                method: "POST",
                credentials: "same-origin",
                headers,
                body: JSON.stringify({
                    content: "📎 Attachment"
                })
            }
        );

        const result = await response.json().catch(() => null);

        if (!response.ok || !result?.id) {
            throw new Error(
                result?.message ||
                "Could not create the attachment message."
            );
        }

        return result.id;

    }

    /* =========================================
       MESSAGE LIST OBSERVER
    ========================================= */

    function observeMessageList() {

        const messageList = document.getElementById("messageList");

        if (!messageList) {
            return;
        }

        const observer = new MutationObserver(records => {

            records.forEach(record => {

                record.addedNodes.forEach(node => {

                    if (!(node instanceof HTMLElement)) {
                        return;
                    }

                    const rows = [];

                    if (node.classList.contains("message-row")) {
                        rows.push(node);
                    }

                    rows.push(
                        ...node.querySelectorAll(".message-row")
                    );

                    rows.forEach(row => {

                        const messageId = Number(row.dataset.messageId);

                        if (!messageId) {
                            return;
                        }

                        // Display attachments that arrived before this message row.
                        flushPendingAttachmentsForRow(row);

                        // Load attachments already saved on the server.
                        loadExistingAttachments(messageId, row);

                    });

                });

            });

        });

        observer.observe(messageList, {
            childList: true,
            subtree: true
        });

    }

    /* =========================================
       LOAD EXISTING ATTACHMENTS
    ========================================= */

    async function loadExistingAttachments(messageId, row) {

        if (
            loadedMessageIds.has(messageId) ||
            pendingMessageLoads.has(messageId)
        ) {
            return;
        }

        pendingMessageLoads.add(messageId);

        try {

            const response = await fetch(
                `${API_BASE}/messages/${messageId}/attachments`,
                {
                    method: "GET",
                    credentials: "same-origin"
                }
            );

            if (!response.ok) {
                return;
            }

            const attachments = await response.json();

            if (Array.isArray(attachments)) {

                attachments.forEach(attachment => {
                    displayAttachment(messageId, attachment, row);
                });

            }

            loadedMessageIds.add(messageId);

        } catch (error) {

            console.error(
                "Could not load attachments for message:",
                messageId,
                error
            );

        } finally {

            pendingMessageLoads.delete(messageId);

        }

    }

    /* =========================================
   PENDING ATTACHMENTS
========================================= */

    function queueAttachment(messageId, attachment) {

        const id = Number(messageId);

        if (!pendingAttachments.has(id)) {
            pendingAttachments.set(id, []);
        }

        const pending = pendingAttachments.get(id);

        // Prevent the same attachment from being queued twice.
        if (!pending.some(item => Number(item.id) === Number(attachment.id))) {
            pending.push(attachment);
        }

    }

    function flushPendingAttachmentsForRow(row) {

        const messageId = Number(row.dataset.messageId);

        if (!messageId || !pendingAttachments.has(messageId)) {
            return;
        }

        if (!row.querySelector(".message-bubble")) {
            return;
        }

        const attachments = pendingAttachments.get(messageId);

        pendingAttachments.delete(messageId);

        attachments.forEach(attachment => {
            displayAttachment(messageId, attachment, row);
        });

    }

    /* =========================================
       DISPLAY ATTACHMENT
    ========================================= */

    function displayAttachment(messageId, attachment, existingRow = null) {

        const row = existingRow ||
            findMessageRow(messageId);

        if (!row) {
            console.warn(
                "Message row not found. Queuing attachment:",
                messageId
            );

            queueAttachment(messageId, attachment);
            return;
        }

        const bubble = row.querySelector(".message-bubble");

        if (!bubble) {
            console.warn(
                "Message bubble not found. Queuing attachment:",
                messageId
            );

            queueAttachment(messageId, attachment);
            return;
        }
        if (bubble.querySelector(
            `[data-attachment-id="${attachment.id}"]`
        )) {
            return;
        }

        const wrapper = document.createElement("div");

        wrapper.className = "attachment-preview";
        wrapper.dataset.attachmentId = attachment.id;

        const extension = attachment.originalFileName
            ?.split(".")
            .pop()
            .toLowerCase();

        const isImage = [
            "jpg", "jpeg", "png", "gif", "webp"
        ].includes(extension);

        if (isImage) {

            const image = document.createElement("img");

            image.className = "attachment-image";
            image.src =
                `${API_BASE}/attachments/${attachment.id}/preview`;

            image.alt = attachment.originalFileName || "Shared image";
            image.loading = "lazy";

            image.addEventListener("error", () => {
                image.replaceWith(
                    createDownloadLink(attachment)
                );
            });

            wrapper.appendChild(image);

        } else {

            const card = document.createElement("div");

            card.className = "attachment-document";

            const icon = document.createElement("span");

            icon.className = "attachment-document-icon";
            icon.textContent = getFileIcon(extension);

            const details = document.createElement("div");

            details.className = "attachment-document-details";

            const name = document.createElement("strong");

            name.textContent =
                attachment.originalFileName || "Document";

            const size = document.createElement("small");

            size.textContent = formatFileSize(attachment.fileSize);

            details.append(name, size);

            card.append(icon, details);

            card.appendChild(
                createDownloadLink(attachment)
            );

            wrapper.appendChild(card);

        }

        const meta = bubble.querySelector(".message-meta");

        if (meta) {
            bubble.insertBefore(wrapper, meta);
        } else {
            bubble.appendChild(wrapper);
        }

    }

    /* =========================================
       FIND MESSAGE ROW
    ========================================= */

    function findMessageRow(messageId) {

        const rows = document.querySelectorAll(
            "#messageList .message-row"
        );

        /*
         * The existing chat renderer does not currently add
         * message IDs to message rows. We will add a data attribute
         * through the renderer integration in the next step.
         */

        return Array.from(rows).find(row =>
            Number(row.dataset.messageId) === Number(messageId)
        ) || null;

    }

    /* =========================================
       DOWNLOAD LINK
    ========================================= */

    function createDownloadLink(attachment) {

        const link = document.createElement("a");

        link.className = "attachment-download";
        link.href =
            `${API_BASE}/attachments/${attachment.id}/download`;

        link.textContent = "Download";
        link.setAttribute("download", attachment.originalFileName || "");

        return link;

    }

    /* =========================================
       FILE ICON
    ========================================= */

    function getFileIcon(extension) {

        const icons = {
            pdf: "📕",
            doc: "📘",
            docx: "📘",
            txt: "📄"
        };

        return icons[extension] || "📎";

    }

    /* =========================================
       FILE SIZE
    ========================================= */

    function formatFileSize(bytes) {

        if (!bytes) {
            return "0 KB";
        }

        if (bytes < 1024 * 1024) {
            return (bytes / 1024).toFixed(1) + " KB";
        }

        return (bytes / (1024 * 1024)).toFixed(2) + " MB";

    }

    /* =========================================
       STATUS MESSAGE
    ========================================= */

    function showMessage(message) {

        let status = document.getElementById("attachmentStatus");

        if (!status) {

            status = document.createElement("div");

            status.id = "attachmentStatus";
            status.className = "attachment-status";

            const messageForm =
                document.getElementById("messageForm");

            messageForm?.insertAdjacentElement("beforebegin", status);

        }

        status.textContent = message;

    }

    /* =========================================
       STYLES
    ========================================= */

    function addAttachmentStyles() {

        if (document.getElementById("attachmentStyles")) {
            return;
        }

        const style = document.createElement("style");

        style.id = "attachmentStyles";

        style.textContent = `
            .attachment-button {
                border: none;
                background: transparent;
                font-size: 23px;
                cursor: pointer;
                padding: 8px;
                color: inherit;
            }

            .attachment-button:hover {
                opacity: 0.7;
            }

            .attachment-preview {
                margin-top: 10px;
                max-width: 280px;
            }

            .attachment-image {
                display: block;
                max-width: 100%;
                max-height: 280px;
                object-fit: contain;
                border-radius: 10px;
            }

            .attachment-document {
                display: flex;
                align-items: center;
                gap: 10px;
                padding: 10px;
                border: 1px solid rgba(128,128,128,.3);
                border-radius: 10px;
                min-width: 200px;
            }

            .attachment-document-icon {
                font-size: 28px;
            }

            .attachment-document-details {
                display: flex;
                flex-direction: column;
                flex: 1;
                min-width: 0;
            }

            .attachment-document-details strong {
                overflow-wrap: anywhere;
                font-size: 13px;
            }

            .attachment-document-details small {
                opacity: .7;
                font-size: 11px;
            }

            .attachment-download {
                font-size: 12px;
                white-space: nowrap;
                text-decoration: none;
            }

            .attachment-status {
                padding: 5px 12px;
                font-size: 12px;
                opacity: .8;
            }
        `;

        document.head.appendChild(style);

    }

    /* =========================================
       PUBLIC API
    ========================================= */

    return {
        initialize,
        loadExistingAttachments,
        displayAttachment,
        queueAttachment
    };

})();

/* =========================================
   START
========================================= */

document.addEventListener(
    "DOMContentLoaded",
    () => AttachmentManager.initialize()
);



// =====================================================
// REAL-TIME ATTACHMENT WEBSOCKET SUBSCRIPTION
// =====================================================

(function installAttachmentWebSocket() {
    if (window.__attachmentWebSocketInstalled) {
        return;
    }

    if (typeof window.subscribeToConversation !== "function") {
        console.error(
            "Attachment WebSocket: subscribeToConversation() was not found."
        );
        return;
    }

    const originalSubscribeToConversation =
        window.subscribeToConversation;

    let attachmentSubscription = null;

    window.subscribeToConversation = function (conversationId) {

        // Keep the existing message and typing subscriptions working.
        originalSubscribeToConversation.apply(this, arguments);

        // Remove the previous conversation's attachment subscription.
        if (attachmentSubscription) {
            attachmentSubscription.unsubscribe();
            attachmentSubscription = null;
        }

        if (!state.stompClient || !state.stompClient.connected) {
            return;
        }

        const destination =
            `/topic/conversations/${conversationId}/attachments`;

        attachmentSubscription = state.stompClient.subscribe(
            destination,
            function (frame) {
                try {
                    const attachment = JSON.parse(frame.body);

                    // Ignore events from conversations that are not active.
                    if (
                        Number(attachment.conversationId) !==
                        Number(state.activeConversationId)
                    ) {
                        return;
                    }

                    const messageRow = document.querySelector(
                        `#messageList .message-row[data-message-id="${attachment.messageId}"]`
                    );

                    if (!messageRow) {
                        console.warn(
                            "Attachment received before message row. Queuing:",
                            attachment.messageId
                        );

                        AttachmentManager.queueAttachment(
                            attachment.messageId,
                            attachment
                        );

                        return;
                    }

                    // Render the attachment using the existing attachment UI.
                    AttachmentManager.displayAttachment(
                        attachment.messageId,
                        attachment,
                        messageRow
                    );

                } catch (error) {
                    console.error(
                        "Error processing real-time attachment:",
                        error
                    );
                }
            }
        );

        console.log(
            "Subscribed to attachment events:",
            destination
        );
    };

    window.__attachmentWebSocketInstalled = true;

    console.log("Attachment WebSocket integration installed.");
})();
