
"use strict";

const API_BASE = "/api/v1";

const state = {
    csrfToken: null,
    csrfHeaderName: "X-XSRF-TOKEN",
    currentUser: null,
    conversations: [],
    activeConversationId: null,
    stompClient: null,
    messageSubscription: null,
    seenMessageIds: new Set(),
    searchTimeout: null
};

document.addEventListener("DOMContentLoaded", initializeChat);

async function initializeChat() {
    bindEvents();

    try {
        await loadCsrfToken();

        const currentUserResponse = await apiRequest("/auth/me");

        state.currentUser =
            currentUserResponse.user ?? currentUserResponse;

        if (!state.currentUser || state.currentUser.id == null) {
            throw new Error("Could not identify the authenticated user.");
        }

        renderCurrentUser(state.currentUser);

        await loadConversations();

        connectWebSocket();

    } catch (error) {
        console.error("Chat initialization failed:", error);

        if (error.status === 401 || error.status === 403) {
            window.location.replace("/login.html");
            return;
        }

        showToast(error.message || "Could not load SajiloChat.");
    }
}

/* Events */

function bindEvents() {
    document.getElementById("newChatButton")
        .addEventListener("click", toggleSearch);

    document.getElementById("emptyNewChatButton")
        .addEventListener("click", toggleSearch);

    document.getElementById("userSearchInput")
        .addEventListener("input", handleUserSearch);

    document.getElementById("messageForm")
        .addEventListener("submit", handleSendMessage);

    document.getElementById("messageInput")
        .addEventListener("keydown", event => {
            if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                document.getElementById("messageForm").requestSubmit();
            }
        });

    document.getElementById("messageInput")
        .addEventListener("input", autoResizeMessageInput);

    document.getElementById("logoutButton")
        .addEventListener("click", logout);
}

/* CSRF */

async function loadCsrfToken() {
    const response = await fetch(`${API_BASE}/auth/csrf`, {
        method: "GET",
        credentials: "same-origin",
        headers: {
            Accept: "application/json"
        }
    });

    if (!response.ok) {
        throw new Error("Unable to initialize security token.");
    }

    const data = await response.json();

    const cookieToken = readCookie("XSRF-TOKEN");

    state.csrfToken =
        data.token ||
        data.csrfToken ||
        cookieToken;

    state.csrfHeaderName =
        data.headerName ||
        "X-XSRF-TOKEN";

    if (!state.csrfToken) {
        throw new Error("Security token was not provided by the server.");
    }
}

function readCookie(name) {
    const prefix = `${name}=`;

    const cookie = document.cookie
        .split("; ")
        .find(value => value.startsWith(prefix));

    return cookie
        ? decodeURIComponent(cookie.substring(prefix.length))
        : null;
}

/* HTTP API */

async function apiRequest(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();

    const headers = {
        Accept: "application/json",
        ...(options.headers || {})
    };

    if (options.body && !headers["Content-Type"]) {
        headers["Content-Type"] = "application/json";
    }

    if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
        if (!state.csrfToken) {
            await loadCsrfToken();
        }

        headers[state.csrfHeaderName] = state.csrfToken;
    }

    const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        method,
        headers,
        credentials: "same-origin"
    });

    if (response.status === 401) {
        window.location.replace("/login.html");

        const error = new Error(
            "Your session has expired. Please log in again."
        );

        error.status = 401;
        throw error;
    }

    if (response.status === 204) {
        return null;
    }

    const contentType = response.headers.get("content-type") || "";

    const data = contentType.includes("application/json")
        ? await response.json()
        : await response.text();

    if (!response.ok) {
        const message =
            data?.message ||
            data?.detail ||
            data?.error ||
            `Request failed with status ${response.status}`;

        const error = new Error(message);
        error.status = response.status;
        throw error;
    }

    return data;
}

/* Current user */

function renderCurrentUser(user) {
    const fullName = [user.firstName, user.lastName]
        .filter(Boolean)
        .join(" ") || user.username;

    document.getElementById("myName").textContent = fullName;

    document.getElementById("myAvatar").textContent =
        getInitials(fullName);
}

/* Conversations */

async function loadConversations() {
    const conversations = await apiRequest("/conversations");

    state.conversations = Array.isArray(conversations)
        ? conversations
        : [];

    renderConversations();
}

function renderConversations() {
    const container = document.getElementById("conversationList");

    container.replaceChildren();

    if (state.conversations.length === 0) {
        const empty = document.createElement("div");
        empty.className = "loading-state";
        empty.textContent = "No conversations yet. Start a new chat.";

        container.appendChild(empty);
        return;
    }

    state.conversations.forEach(conversation => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "conversation-item";

        if (conversation.id === state.activeConversationId) {
            button.classList.add("active");
        }

        const name = getConversationName(conversation);

        const avatar = document.createElement("div");
        avatar.className = "avatar";
        avatar.textContent = getInitials(name);

        const details = document.createElement("div");
        details.className = "conversation-details";

        const title = document.createElement("strong");
        title.textContent = name;

        const subtitle = document.createElement("small");
        subtitle.textContent =
            conversation.type === "GROUP"
                ? "Group conversation"
                : "Private conversation";

        details.append(title, subtitle);
        button.append(avatar, details);

        button.addEventListener("click", () => {
            openConversation(conversation);
        });

        container.appendChild(button);
    });
}

function getConversationName(conversation) {
    if (conversation.name) {
        return conversation.name;
    }

    const otherMember = (conversation.members || [])
        .find(member =>
            Number(member.userId) !== Number(state.currentUser.id)
        );

    if (!otherMember) {
        return "Conversation";
    }

    return [otherMember.firstName, otherMember.lastName]
        .filter(Boolean)
        .join(" ") || otherMember.username;
}

/* Open conversation */

async function openConversation(conversation) {
    state.activeConversationId = conversation.id;
    state.seenMessageIds.clear();

    renderConversations();

    document.getElementById("emptyState").classList.add("hidden");
    document.getElementById("activeChat").classList.remove("hidden");

    const name = getConversationName(conversation);

    document.getElementById("chatContactName").textContent = name;
    document.getElementById("chatAvatar").textContent = getInitials(name);

    document.getElementById("chatContactStatus").textContent =
        conversation.type === "GROUP"
            ? "Group conversation"
            : "Conversation";

    if (state.messageSubscription) {
        state.messageSubscription.unsubscribe();
        state.messageSubscription = null;
    }

    const messageList = document.getElementById("messageList");
    messageList.replaceChildren();

    const loading = document.createElement("div");
    loading.className = "loading-state";
    loading.textContent = "Loading messages...";

    messageList.appendChild(loading);

    if (state.stompClient && state.stompClient.connected) {
        subscribeToConversation(conversation.id);
    }

    try {
        const messages = await apiRequest(
            `/conversations/${conversation.id}/messages`
        );

        if (state.activeConversationId !== conversation.id) {
            return;
        }

        messageList.replaceChildren();

        if (Array.isArray(messages)) {
            messages.forEach(renderMessage);
        }

        scrollMessagesToBottom();

    } catch (error) {
        showToast(error.message);

        messageList.replaceChildren();

        const failure = document.createElement("div");
        failure.className = "loading-state";
        failure.textContent = "Could not load messages.";

        messageList.appendChild(failure);
    }
}

/* WebSocket */

function connectWebSocket() {
    if (typeof StompJs === "undefined") {
        setConnectionStatus("Real-time library unavailable");
        showToast("Could not load the real-time messaging library.");
        return;
    }

    const protocol = window.location.protocol === "https:"
        ? "wss:"
        : "ws:";

    const brokerURL = `${protocol}//${window.location.host}/ws`;

    state.stompClient = new StompJs.Client({
        brokerURL,

        connectHeaders: {},

        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        reconnectDelay: 5000,

        onConnect: () => {
            setConnectionStatus("Connected");

            if (state.activeConversationId) {
                subscribeToConversation(state.activeConversationId);
            }
        },

        onDisconnect: () => {
            setConnectionStatus("Disconnected");
        },

        onWebSocketClose: () => {
            setConnectionStatus("Reconnecting...");
        },

        onStompError: frame => {
            console.error("STOMP error:", frame);
            setConnectionStatus("Messaging connection error");
        },

        onWebSocketError: error => {
            console.error("WebSocket error:", error);
            setConnectionStatus("Connection error");
        }
    });

    state.stompClient.activate();
}

function subscribeToConversation(conversationId) {
    if (!state.stompClient || !state.stompClient.connected) {
        return;
    }

    if (state.messageSubscription) {
        state.messageSubscription.unsubscribe();
        state.messageSubscription = null;
    }

    const destination =
        `/topic/conversations/${conversationId}/messages`;

    state.messageSubscription = state.stompClient.subscribe(
        destination,
        frame => {
            try {
                const message = JSON.parse(frame.body);

                if (
                    Number(message.conversationId) !==
                    Number(state.activeConversationId)
                ) {
                    return;
                }

                renderMessage(message);
                scrollMessagesToBottom();

            } catch (error) {
                console.error(
                    "Could not process incoming message:",
                    error
                );
            }
        }
    );
}

/* Send message */

async function handleSendMessage(event) {
    event.preventDefault();

    const input = document.getElementById("messageInput");
    const content = input.value.trim();

    if (!content || !state.activeConversationId) {
        return;
    }

    if (!state.stompClient || !state.stompClient.connected) {
        showToast("Messaging connection is not ready. Please wait.");
        return;
    }

    const sendButton = document.getElementById("sendButton");
    sendButton.disabled = true;

    try {
        state.stompClient.publish({
            destination:
                `/app/conversations/${state.activeConversationId}/messages`,
            body: JSON.stringify({ content })
        });

        input.value = "";
        autoResizeMessageInput();

    } catch (error) {
        console.error("Message send failed:", error);
        showToast("Could not send your message.");

    } finally {
        sendButton.disabled = false;
        input.focus();
    }
}

/* Render messages */

function renderMessage(message) {
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
    const row = document.createElement("div");

    const senderId =
        message.sender?.id ??
        message.sender?.userId ??
        message.senderId;

    const currentUserId = state.currentUser?.id;

    const isOwnMessage =
        senderId != null &&
        currentUserId != null &&
        Number(senderId) === Number(currentUserId);

    row.className = isOwnMessage
        ? "message-row own"
        : "message-row";

    const bubble = document.createElement("div");
    bubble.className = "message-bubble";

    const content = document.createElement("div");
    content.className = "message-content";
    content.textContent = message.content || "";

    const meta = document.createElement("small");
    meta.className = "message-meta";

    const time = message.createdAt
        ? new Date(message.createdAt).toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit"
        })
        : "";

    meta.textContent = time + (message.edited ? " · edited" : "");

    bubble.append(content, meta);
    row.appendChild(bubble);
    container.appendChild(row);
}

function scrollMessagesToBottom() {
    const container = document.getElementById("messageList");

    requestAnimationFrame(() => {
        container.scrollTop = container.scrollHeight;
    });
}

/* User search */

function toggleSearch() {
    const panel = document.getElementById("searchPanel");

    panel.classList.toggle("hidden");

    if (!panel.classList.contains("hidden")) {
        document.getElementById("userSearchInput").focus();
    }
}

function handleUserSearch(event) {
    const query = event.target.value.trim();

    clearTimeout(state.searchTimeout);

    if (query.length < 2) {
        document.getElementById("userSearchResults").replaceChildren();
        return;
    }

    state.searchTimeout = setTimeout(() => {
        searchUsers(query);
    }, 300);
}

async function searchUsers(query) {
    const resultsContainer =
        document.getElementById("userSearchResults");

    try {
        const params = new URLSearchParams({
            q: query,
            page: "0",
            size: "20"
        });

        const result = await apiRequest(
            `/users/search?${params.toString()}`
        );

        resultsContainer.replaceChildren();

        const users = result.content || [];

        if (users.length === 0) {
            const empty = document.createElement("div");
            empty.className = "loading-state";
            empty.textContent = "No users found.";

            resultsContainer.appendChild(empty);
            return;
        }

        users.forEach(user => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "search-result";

            const avatar = document.createElement("div");
            avatar.className = "avatar";
            avatar.textContent = getInitials(
                user.firstName || user.username
            );

            const details = document.createElement("span");

            const name = document.createElement("strong");
            name.textContent =
                [user.firstName, user.lastName]
                    .filter(Boolean)
                    .join(" ") || user.username;

            const username = document.createElement("small");
            username.textContent = `@${user.username}`;

            details.append(name, username);
            button.append(avatar, details);

            button.addEventListener("click", () => {
                startPrivateConversation(user.id);
            });

            resultsContainer.appendChild(button);
        });

    } catch (error) {
        console.error("User search failed:", error);
        showToast(error.message);
    }
}

/* Start private conversation */

async function startPrivateConversation(targetUserId) {
    try {
        const conversation = await apiRequest(
            "/conversations/private",
            {
                method: "POST",
                body: JSON.stringify({ targetUserId })
            }
        );

        await loadConversations();

        document.getElementById("searchPanel").classList.add("hidden");
        document.getElementById("userSearchInput").value = "";
        document.getElementById("userSearchResults").replaceChildren();

        await openConversation(conversation);

    } catch (error) {
        showToast(error.message);
    }
}

/* Logout */

async function logout() {
    const button = document.getElementById("logoutButton");
    button.disabled = true;

    try {
        await apiRequest("/auth/logout", {
            method: "POST"
        });

        if (state.stompClient) {
            await state.stompClient.deactivate();
        }

        window.location.replace("/login.html");

    } catch (error) {
        console.error("Logout failed:", error);
        showToast(error.message);
        button.disabled = false;
    }
}

/* Utilities */

function autoResizeMessageInput() {
    const input = document.getElementById("messageInput");

    input.style.height = "auto";
    input.style.height = `${Math.min(input.scrollHeight, 130)}px`;
}

function getInitials(value) {
    return String(value || "U")
        .trim()
        .split(/\s+/)
        .slice(0, 2)
        .map(part => part.charAt(0))
        .join("")
        .toUpperCase();
}

function setConnectionStatus(message) {
    document.getElementById("connectionStatus").textContent = message;
}

function showToast(message) {
    const toast = document.getElementById("toast");

    toast.textContent = message;
    toast.classList.remove("hidden");

    window.clearTimeout(showToast.timeout);

    showToast.timeout = window.setTimeout(() => {
        toast.classList.add("hidden");
    }, 3500);
}
