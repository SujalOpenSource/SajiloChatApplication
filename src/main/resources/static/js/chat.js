"use strict";

const API_BASE = "/api/v1";

const state = {
    csrfToken: null,
    csrfHeaderName: "X-XSRF-TOKEN",
    currentUser: null,
    conversations: [],
    activeConversationId: null,
    activeConversation: null,
    stompClient: null,
    messageSubscription: null,
    seenMessageIds: new Set(),
    searchTimeout: null,

    // Group creation
    groupSearchTimeout: null,
    groupSearchSequence: 0,
    selectedGroupMembers: [],

    // Add members to existing group
    addMemberSearchTimeout: null,
    addMemberSearchSequence: 0,
    selectedAddGroupMembers: [],

    // Typing indicator
    typingSubscription: null,
    typingStopTimeout: null,
    localTypingActive: false,
    remoteTypingUsers: new Map(),

    // In-app notifications
    notificationSubscription: null,
    notifications: [],
    unreadNotificationCount: 0,
    notificationLoading: false
};

document.addEventListener("DOMContentLoaded", initializeChat);

/* =========================================
   INITIALIZE
========================================= */

async function initializeChat() {
    bindEvents();

    try {
        await loadCsrfToken();

        const currentUserResponse = await apiRequest("/auth/me");

        const authenticatedUser =
            currentUserResponse.user ?? currentUserResponse;

        if (!authenticatedUser || authenticatedUser.id == null) {
            throw new Error("Could not identify the authenticated user.");
        }

        const profileResponse = await apiRequest("/users/me");

        state.currentUser = {
            ...authenticatedUser,
            ...profileResponse
        };

        renderCurrentUser(state.currentUser);

        await loadConversations();

        // Notification failures must not prevent the chat application from loading.
        await Promise.all([
            loadNotifications(),
            loadUnreadNotificationCount()
        ]);

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

/* =========================================
   EVENTS
========================================= */

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

    const messageInput = document.getElementById("messageInput");

    messageInput.addEventListener("input", autoResizeMessageInput);
    messageInput.addEventListener("input", handleTypingInput);
    messageInput.addEventListener("blur", stopTyping);

    document.addEventListener("visibilitychange", () => {
        if (document.hidden) {
            stopTyping();
        }
    });

    window.addEventListener("pagehide", stopTyping);

    document.getElementById("logoutButton")
        .addEventListener("click", logout);

    // In-app notification events.
    document.getElementById("notificationButton")
        ?.addEventListener("click", toggleNotificationDropdown);

    document.getElementById("markAllNotificationsReadButton")
        ?.addEventListener("click", markAllNotificationsAsRead);

    document.addEventListener("click", event => {
        const container = document.getElementById("notificationContainer");

        if (container && !container.contains(event.target)) {
            closeNotificationDropdown();
        }
    });

    // Profile picture events.
    document.getElementById("changeProfilePictureButton")
        .addEventListener("click", () => {
            document.getElementById("profilePictureInput").click();
        });

    document.getElementById("profilePictureInput")
        .addEventListener("change", handleProfilePictureUpload);

    document.getElementById("removeProfilePictureButton")
        .addEventListener("click", handleProfilePictureRemoval);

    // Edit profile events.
    document.getElementById("editProfileButton")
        ?.addEventListener("click", openEditProfileDialog);

    document.getElementById("cancelEditProfileButton")
        ?.addEventListener("click", closeEditProfileDialog);

    document.getElementById("editProfileForm")
        ?.addEventListener("submit", handleProfileUpdate);

    // Group creation events.
    document.getElementById("newGroupButton")
        .addEventListener("click", openCreateGroupDialog);

    document.getElementById("cancelCreateGroupButton")
        .addEventListener("click", closeCreateGroupDialog);

    document.getElementById("groupMemberSearchInput")
        .addEventListener("input", handleGroupMemberSearch);

    document.getElementById("createGroupForm")
        .addEventListener("submit", handleCreateGroup);

    // Add members to existing group.
    document.getElementById("addGroupMembersButton")
        ?.addEventListener("click", openAddGroupMembersDialog);

    document.getElementById("cancelAddGroupMembersButton")
        ?.addEventListener("click", closeAddGroupMembersDialog);

    document.getElementById("addGroupMemberSearchInput")
        ?.addEventListener("input", handleAddGroupMemberSearch);

    document.getElementById("addGroupMembersForm")
        ?.addEventListener("submit", handleAddGroupMembers);
}

/* =========================================
   CSRF
========================================= */

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

/* =========================================
   HTTP API
========================================= */

async function apiRequest(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();

    const headers = {
        Accept: "application/json",
        ...(options.headers || {})
    };

    const isFormData = options.body instanceof FormData;

    if (
        options.body &&
        !isFormData &&
        !headers["Content-Type"]
    ) {
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

/* =========================================
   CURRENT USER
========================================= */

function renderCurrentUser(user) {
    const profileBio = document.getElementById("profileBio");

    if (profileBio) {
        const bio = user.bio?.trim();

        profileBio.textContent = bio || "No bio added yet.";
        profileBio.classList.toggle("empty-bio", !bio);
    }

    const fullName = [user.firstName, user.lastName]
        .filter(Boolean)
        .join(" ") || user.username;

    document.getElementById("myName").textContent = fullName;

    setAvatar(
        document.getElementById("myAvatar"),
        user.profilePictureUrl,
        fullName
    );

    document.getElementById("removeProfilePictureButton")
        .classList.toggle("hidden", !user.profilePictureUrl);
}

/* =========================================
   EDIT PROFILE
========================================= */

function openEditProfileDialog() {
    const dialog = document.getElementById("editProfileDialog");
    const user = state.currentUser;

    if (!dialog || !user) {
        showToast("Unable to load profile information.");
        return;
    }

    document.getElementById("editFirstName").value =
        user.firstName ?? "";

    document.getElementById("editLastName").value =
        user.lastName ?? "";

    document.getElementById("editBio").value =
        user.bio ?? "";

    dialog.showModal();
}

function closeEditProfileDialog() {
    const dialog = document.getElementById("editProfileDialog");

    if (dialog?.open) {
        dialog.close();
    }
}

async function handleProfileUpdate(event) {
    event.preventDefault();

    const firstNameInput = document.getElementById("editFirstName");
    const lastNameInput = document.getElementById("editLastName");
    const bioInput = document.getElementById("editBio");
    const saveButton = document.getElementById("saveProfileButton");

    const firstName = firstNameInput.value.trim();
    const lastName = lastNameInput.value.trim();
    const bio = bioInput.value.trim();

    if (!firstName || !lastName) {
        showToast("First name and last name are required.");
        return;
    }

    if (firstName.length > 50 || lastName.length > 50) {
        showToast("Names cannot exceed 50 characters.");
        return;
    }

    if (bio.length > 250) {
        showToast("About section cannot exceed 250 characters.");
        return;
    }

    saveButton.disabled = true;
    saveButton.textContent = "Saving...";

    try {
        const updatedUser = await apiRequest("/users/me", {
            method: "PATCH",
            body: JSON.stringify({
                firstName,
                lastName,
                bio
            })
        });

        if (!updatedUser) {
            throw new Error("The server returned an empty profile.");
        }

        state.currentUser = {
            ...state.currentUser,
            ...updatedUser
        };

        renderCurrentUser(state.currentUser);

        closeEditProfileDialog();

        showToast("Profile updated successfully.");

    } catch (error) {
        console.error("Profile update failed:", error);
        showToast(error.message || "Unable to update your profile.");

    } finally {
        saveButton.disabled = false;
        saveButton.textContent = "Save changes";
    }
}

/* =========================================
   PROFILE PICTURES
========================================= */

function setAvatar(element, pictureUrl, fallbackName) {
    element.replaceChildren();

    if (!pictureUrl) {
        element.textContent = getInitials(fallbackName);
        return;
    }

    const image = document.createElement("img");

    image.className = "avatar-image";
    image.src = pictureUrl;
    image.alt = `${fallbackName || "User"} profile picture`;

    image.onerror = () => {
        element.replaceChildren();
        element.textContent = getInitials(fallbackName);
    };

    element.appendChild(image);
}

async function handleProfilePictureUpload(event) {
    const input = event.target;
    const file = input.files?.[0];

    if (!file) {
        return;
    }

    const allowedTypes = [
        "image/jpeg",
        "image/png",
        "image/webp"
    ];

    if (!allowedTypes.includes(file.type)) {
        showToast("Please select a JPEG, PNG, or WebP image.");
        input.value = "";
        return;
    }

    if (file.size > 5 * 1024 * 1024) {
        showToast("Profile picture must be smaller than 5 MB.");
        input.value = "";
        return;
    }

    const button = document.getElementById("changeProfilePictureButton");

    button.disabled = true;

    try {
        const formData = new FormData();
        formData.append("file", file);

        const response = await apiRequest(
            "/users/me/profile-picture",
            {
                method: "POST",
                body: formData
            }
        );

        state.currentUser.profilePictureUrl =
            response.profilePictureUrl;

        renderCurrentUser(state.currentUser);

        showToast("Profile picture updated successfully.");

    } catch (error) {
        console.error("Profile picture upload failed:", error);
        showToast(error.message || "Could not upload profile picture.");

    } finally {
        button.disabled = false;
        input.value = "";
    }
}

async function handleProfilePictureRemoval() {
    if (!state.currentUser?.profilePictureUrl) {
        return;
    }

    const confirmed = window.confirm(
        "Are you sure you want to remove your profile picture?"
    );

    if (!confirmed) {
        return;
    }

    const button = document.getElementById("removeProfilePictureButton");

    button.disabled = true;

    try {
        await apiRequest("/users/me/profile-picture", {
            method: "DELETE"
        });

        state.currentUser.profilePictureUrl = null;

        renderCurrentUser(state.currentUser);

        showToast("Profile picture removed successfully.");

    } catch (error) {
        console.error("Profile picture removal failed:", error);
        showToast(error.message || "Could not remove profile picture.");

    } finally {
        button.disabled = false;
    }
}

/* =========================================
   CONVERSATIONS
========================================= */

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

        if (
            Number(conversation.id) ===
            Number(state.activeConversationId)
        ) {
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
                ? `${conversation.members?.length || 0} members`
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
            Number(getMemberUserId(member)) !== Number(state.currentUser.id)
        );

    if (!otherMember) {
        return "Conversation";
    }

    return [otherMember.firstName, otherMember.lastName]
        .filter(Boolean)
        .join(" ") || otherMember.username;
}

function getMemberUserId(member) {
    return member?.userId ?? member?.user?.id ?? member?.id;
}

/* =========================================
   OPEN CONVERSATION
========================================= */

async function openConversation(conversation) {
    // Stop typing in the previous conversation before switching.
    stopTyping();
    clearRemoteTypingUsers();

    state.activeConversationId = conversation.id;
    state.activeConversation = conversation;

    state.seenMessageIds.clear();

    renderConversations();
    updateAddMembersButton(conversation);

    document.getElementById("emptyState").classList.add("hidden");
    document.getElementById("activeChat").classList.remove("hidden");

    const name = getConversationName(conversation);

    setAvatar(
        document.getElementById("chatAvatar"),
        null,
        name
    );

    document.getElementById("chatContactName").textContent = name;

    document.getElementById("chatContactStatus").textContent =
        conversation.type === "GROUP"
            ? `${conversation.members?.length || 0} members`
            : "Conversation";

    if (state.messageSubscription) {
        state.messageSubscription.unsubscribe();
        state.messageSubscription = null;
    }

    if (state.typingSubscription) {
        state.typingSubscription.unsubscribe();
        state.typingSubscription = null;
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

        if (
            Number(state.activeConversationId) !==
            Number(conversation.id)
        ) {
            return;
        }

        messageList.replaceChildren();

        if (Array.isArray(messages)) {
            messages.forEach(renderMessage);
        }

        scrollMessagesToBottom();

    } catch (error) {
        console.error("Message loading failed:", error);

        if (
            Number(state.activeConversationId) !==
            Number(conversation.id)
        ) {
            return;
        }

        showToast(error.message);

        messageList.replaceChildren();

        const failure = document.createElement("div");
        failure.className = "loading-state";
        failure.textContent = "Could not load messages.";

        messageList.appendChild(failure);
    }
}

/* =========================================
   ADD MEMBERS BUTTON VISIBILITY
========================================= */

function updateAddMembersButton(conversation) {
    const button = document.getElementById("addGroupMembersButton");

    if (!button) {
        return;
    }

    const isGroup = conversation?.type === "GROUP";

    button.classList.toggle("hidden", !isGroup);
}

/* =========================================
   WEBSOCKET
========================================= */

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

            subscribeToNotifications();

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

    if (state.typingSubscription) {
        state.typingSubscription.unsubscribe();
        state.typingSubscription = null;
    }

    // Subscribe to incoming messages.
    state.messageSubscription = state.stompClient.subscribe(
        `/topic/conversations/${conversationId}/messages`,
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
                console.error("Could not process incoming message:", error);
            }
        }
    );

    // Subscribe to typing events.
    state.typingSubscription = state.stompClient.subscribe(
        `/topic/conversations/${conversationId}/typing`,
        frame => {
            try {
                const event = JSON.parse(frame.body);
                handleIncomingTypingEvent(event);

            } catch (error) {
                console.error("Could not process typing event:", error);
            }
        }
    );
}

/* =========================================
   TYPING INDICATOR
========================================= */

function handleTypingInput() {
    const input = document.getElementById("messageInput");

    if (!input.value.trim() || !state.activeConversationId) {
        stopTyping();
        return;
    }

    startTyping();

    clearTimeout(state.typingStopTimeout);

    state.typingStopTimeout = setTimeout(() => {
        stopTyping();
    }, 1500);
}

function startTyping() {
    if (
        !state.activeConversationId ||
        !state.stompClient ||
        !state.stompClient.connected
    ) {
        return;
    }

    if (state.localTypingActive) {
        return;
    }

    state.localTypingActive = true;

    publishTypingStatus(true);
}

function stopTyping() {
    clearTimeout(state.typingStopTimeout);
    state.typingStopTimeout = null;

    if (!state.localTypingActive) {
        return;
    }

    state.localTypingActive = false;

    publishTypingStatus(false);
}

function publishTypingStatus(typing) {
    if (
        !state.stompClient ||
        !state.stompClient.connected ||
        !state.activeConversationId
    ) {
        return;
    }

    try {
        state.stompClient.publish({
            destination:
                `/app/conversations/${state.activeConversationId}/typing`,

            body: JSON.stringify({
                conversationId: Number(state.activeConversationId),
                typing
            })
        });

    } catch (error) {
        console.error("Could not publish typing status:", error);
    }
}

function handleIncomingTypingEvent(event) {
    if (
        !event ||
        Number(event.conversationId) !==
        Number(state.activeConversationId)
    ) {
        return;
    }

    const username = event.username;

    if (!username) {
        return;
    }

    // Ignore our own typing events.
    if (
        String(username).toLowerCase() ===
        String(state.currentUser?.username || "").toLowerCase()
    ) {
        return;
    }

    if (event.typing) {
        showRemoteTyping(username);
    } else {
        removeRemoteTyping(username);
    }
}

function showRemoteTyping(username) {
    // Reset this user's previous timeout.
    removeRemoteTyping(username, false);

    // Fallback in case a stop event is missed.
    const timeout = setTimeout(() => {
        removeRemoteTyping(username);
    }, 5000);

    state.remoteTypingUsers.set(username, timeout);

    renderTypingIndicator();
}

function removeRemoteTyping(username, updateUI = true) {
    const timeout = state.remoteTypingUsers.get(username);

    if (timeout) {
        clearTimeout(timeout);
    }

    state.remoteTypingUsers.delete(username);

    if (updateUI) {
        renderTypingIndicator();
    }
}

function getTypingDisplayName(username) {
    const members = state.activeConversation?.members || [];

    const member = members.find(item => {
        const memberUsername =
            item.username || item.user?.username;

        return (
            String(memberUsername || "").toLowerCase() ===
            String(username).toLowerCase()
        );
    });

    if (!member) {
        return username;
    }

    const firstName =
        member.firstName || member.user?.firstName || "";

    const lastName =
        member.lastName || member.user?.lastName || "";

    return (
        [firstName, lastName].filter(Boolean).join(" ") ||
        username
    );
}

function renderTypingIndicator() {
    const indicator = document.getElementById("typingIndicator");

    if (!indicator) {
        return;
    }

    const usernames = [...state.remoteTypingUsers.keys()];

    if (usernames.length === 0) {
        indicator.textContent = "";
        indicator.classList.remove("visible");
        return;
    }

    const names = usernames.map(getTypingDisplayName);

    let message;

    if (names.length === 1) {
        message = `${names[0]} is typing…`;
    } else if (names.length === 2) {
        message = `${names[0]} and ${names[1]} are typing…`;
    } else {
        message = "Several people are typing…";
    }

    indicator.textContent = message;
    indicator.classList.add("visible");
}

function clearRemoteTypingUsers() {
    state.remoteTypingUsers.forEach(timeout => {
        clearTimeout(timeout);
    });

    state.remoteTypingUsers.clear();

    renderTypingIndicator();
}

/* =========================================
   SEND MESSAGE
========================================= */

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

    // Stop typing before sending the message.
    stopTyping();

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

/* =========================================
   RENDER MESSAGES
========================================= */

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

    const sender = message.sender || {};

    const senderId =
        sender.id ??
        sender.userId ??
        message.senderId;

    const currentUserId = state.currentUser?.id;

    const isOwnMessage =
        senderId != null &&
        currentUserId != null &&
        Number(senderId) === Number(currentUserId);

    const senderName =
        [sender.firstName, sender.lastName]
            .filter(Boolean)
            .join(" ") || sender.username || "User";

    row.className = isOwnMessage
        ? "message-row own"
        : "message-row";

    const avatar = document.createElement("div");
    avatar.className = "avatar message-avatar";

    setAvatar(avatar, sender.profilePictureUrl, senderName);

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

    if (isOwnMessage) {
        row.append(bubble, avatar);
    } else {
        row.append(avatar, bubble);
    }

    container.appendChild(row);
}

function scrollMessagesToBottom() {
    const container = document.getElementById("messageList");

    requestAnimationFrame(() => {
        container.scrollTop = container.scrollHeight;
    });
}

/* =========================================
   PRIVATE USER SEARCH
========================================= */

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

        const users = Array.isArray(result)
            ? result
            : result.content || [];

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

            const fullName =
                [user.firstName, user.lastName]
                    .filter(Boolean)
                    .join(" ") || user.username;

            const avatar = document.createElement("div");
            avatar.className = "avatar";

            setAvatar(avatar, user.profilePictureUrl, fullName);

            const details = document.createElement("span");

            const name = document.createElement("strong");
            name.textContent = fullName;

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

/* =========================================
   START PRIVATE CONVERSATION
========================================= */

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
        console.error("Private conversation creation failed:", error);
        showToast(error.message);
    }
}

/* =========================================
   GROUP CREATION
========================================= */

function openCreateGroupDialog() {
    const dialog = document.getElementById("createGroupDialog");

    if (!dialog) {
        showToast("Group creation interface is unavailable.");
        return;
    }

    clearTimeout(state.groupSearchTimeout);
    state.groupSearchSequence++;

    state.selectedGroupMembers = [];

    document.getElementById("createGroupForm").reset();
    document.getElementById("groupMemberSearchResults").replaceChildren();

    renderSelectedGroupMembers();

    dialog.showModal();

    document.getElementById("groupNameInput").focus();
}

function closeCreateGroupDialog() {
    const dialog = document.getElementById("createGroupDialog");

    clearTimeout(state.groupSearchTimeout);
    state.groupSearchSequence++;

    if (dialog?.open) {
        dialog.close();
    }

    state.selectedGroupMembers = [];

    document.getElementById("groupMemberSearchResults").replaceChildren();
    document.getElementById("selectedGroupMembers").replaceChildren();
}

function handleGroupMemberSearch(event) {
    const query = event.target.value.trim();

    clearTimeout(state.groupSearchTimeout);
    state.groupSearchSequence++;

    const resultsContainer =
        document.getElementById("groupMemberSearchResults");

    if (query.length < 2) {
        resultsContainer.replaceChildren();
        return;
    }

    state.groupSearchTimeout = setTimeout(() => {
        searchGroupMembers(query);
    }, 300);
}

async function searchGroupMembers(query) {
    const resultsContainer =
        document.getElementById("groupMemberSearchResults");

    const searchSequence = ++state.groupSearchSequence;

    try {
        const params = new URLSearchParams({
            q: query,
            page: "0",
            size: "20"
        });

        const result = await apiRequest(
            `/users/search?${params.toString()}`
        );

        if (searchSequence !== state.groupSearchSequence) {
            return;
        }

        resultsContainer.replaceChildren();

        const users = Array.isArray(result)
            ? result
            : result?.content || [];

        const availableUsers = users.filter(user => {
            const isCurrentUser =
                Number(user.id) === Number(state.currentUser.id);

            const alreadySelected =
                state.selectedGroupMembers.some(
                    member => Number(member.id) === Number(user.id)
                );

            return !isCurrentUser && !alreadySelected;
        });

        if (availableUsers.length === 0) {
            const empty = document.createElement("div");
            empty.className = "loading-state";
            empty.textContent = "No available users found.";

            resultsContainer.appendChild(empty);
            return;
        }

        availableUsers.forEach(user => {
            const button = document.createElement("button");

            button.type = "button";
            button.className = "group-member-result";

            const fullName =
                [user.firstName, user.lastName]
                    .filter(Boolean)
                    .join(" ") || user.username;

            const avatar = document.createElement("div");
            avatar.className = "avatar";

            setAvatar(avatar, user.profilePictureUrl, fullName);

            const details = document.createElement("div");
            details.className = "member-details";

            const name = document.createElement("strong");
            name.textContent = fullName;

            const username = document.createElement("small");
            username.textContent = `@${user.username}`;

            details.append(name, username);

            const addIcon = document.createElement("span");
            addIcon.className = "member-add-icon";
            addIcon.textContent = "+";

            button.append(avatar, details, addIcon);

            button.addEventListener("click", () => {
                addGroupMember(user);
            });

            resultsContainer.appendChild(button);
        });

    } catch (error) {
        console.error("Group member search failed:", error);

        if (searchSequence === state.groupSearchSequence) {
            showToast(error.message);
        }
    }
}

function addGroupMember(user) {
    const alreadySelected = state.selectedGroupMembers.some(
        member => Number(member.id) === Number(user.id)
    );

    if (
        alreadySelected ||
        Number(user.id) === Number(state.currentUser.id)
    ) {
        return;
    }

    state.selectedGroupMembers.push(user);

    renderSelectedGroupMembers();

    const query = document.getElementById("groupMemberSearchInput")
        .value.trim();

    if (query.length >= 2) {
        searchGroupMembers(query);
    }
}

function removeGroupMember(userId) {
    state.selectedGroupMembers =
        state.selectedGroupMembers.filter(
            member => Number(member.id) !== Number(userId)
        );

    renderSelectedGroupMembers();

    const query = document.getElementById("groupMemberSearchInput")
        .value.trim();

    if (query.length >= 2) {
        searchGroupMembers(query);
    }
}

function renderSelectedGroupMembers() {
    const container =
        document.getElementById("selectedGroupMembers");

    container.replaceChildren();

    if (state.selectedGroupMembers.length === 0) {
        const empty = document.createElement("p");

        empty.className = "group-empty-selection";
        empty.textContent = "No members selected yet.";

        container.appendChild(empty);
        return;
    }

    state.selectedGroupMembers.forEach(member => {
        const chip = document.createElement("div");
        chip.className = "selected-member-chip";

        const name = document.createElement("span");

        name.textContent =
            [member.firstName, member.lastName]
                .filter(Boolean)
                .join(" ") || member.username;

        const removeButton = document.createElement("button");

        removeButton.type = "button";
        removeButton.className = "remove-selected-member";
        removeButton.textContent = "×";
        removeButton.title = `Remove ${name.textContent}`;

        removeButton.addEventListener("click", () => {
            removeGroupMember(member.id);
        });

        chip.append(name, removeButton);
        container.appendChild(chip);
    });
}

async function handleCreateGroup(event) {
    event.preventDefault();

    const nameInput = document.getElementById("groupNameInput");
    const createButton = document.getElementById("createGroupButton");

    const name = nameInput.value.trim();

    if (!name) {
        showToast("Please enter a group name.");
        nameInput.focus();
        return;
    }

    if (name.length > 100) {
        showToast("Group name cannot exceed 100 characters.");
        return;
    }

    if (state.selectedGroupMembers.length === 0) {
        showToast("Please select at least one group member.");
        return;
    }

    const memberIds = state.selectedGroupMembers.map(member => member.id);

    createButton.disabled = true;
    createButton.textContent = "Creating...";

    try {
        const createdGroup = await apiRequest("/groups", {
            method: "POST",
            body: JSON.stringify({
                name,
                memberIds
            })
        });

        if (!createdGroup?.id) {
            throw new Error("The server did not return the created group.");
        }

        closeCreateGroupDialog();

        let conversation = null;

        try {
            await loadConversations();

            conversation = state.conversations.find(
                item => Number(item.id) === Number(createdGroup.id)
            ) || null;

        } catch (refreshError) {
            console.warn("Could not refresh conversations:", refreshError);
        }

        if (!conversation) {
            conversation = await apiRequest(
                `/conversations/${createdGroup.id}`
            );

            if (conversation) {
                const alreadyExists = state.conversations.some(
                    item => Number(item.id) === Number(conversation.id)
                );

                if (!alreadyExists) {
                    state.conversations.push(conversation);
                    renderConversations();
                }
            }
        }

        if (!conversation || conversation.type !== "GROUP") {
            throw new Error(
                "The group was created, but its conversation could not be loaded."
            );
        }

        await openConversation(conversation);

        showToast("Group created successfully.");

    } catch (error) {
        console.error("Group creation failed:", error);
        showToast(error.message || "Could not create or open the group.");

    } finally {
        createButton.disabled = false;
        createButton.textContent = "Create group";
    }
}

/* =========================================
   ADD MEMBERS TO EXISTING GROUP
========================================= */

function openAddGroupMembersDialog() {
    const dialog = document.getElementById("addGroupMembersDialog");

    if (!dialog) {
        showToast("Add members interface is unavailable.");
        return;
    }

    if (
        !state.activeConversation ||
        state.activeConversation.type !== "GROUP"
    ) {
        showToast("Please open a group conversation first.");
        return;
    }

    clearTimeout(state.addMemberSearchTimeout);
    state.addMemberSearchSequence++;

    state.selectedAddGroupMembers = [];

    document.getElementById("addGroupMembersForm").reset();

    document.getElementById("addGroupMemberSearchResults")
        .replaceChildren();

    renderSelectedAddGroupMembers();

    dialog.showModal();

    document.getElementById("addGroupMemberSearchInput").focus();
}

function closeAddGroupMembersDialog() {
    const dialog = document.getElementById("addGroupMembersDialog");

    clearTimeout(state.addMemberSearchTimeout);
    state.addMemberSearchSequence++;

    if (dialog?.open) {
        dialog.close();
    }

    state.selectedAddGroupMembers = [];

    document.getElementById("addGroupMemberSearchResults")
        ?.replaceChildren();

    document.getElementById("selectedAddGroupMembers")
        ?.replaceChildren();
}

function getExistingGroupMemberIds() {
    const members = state.activeConversation?.members || [];

    return new Set(
        members
            .map(getMemberUserId)
            .filter(id => id != null)
            .map(id => Number(id))
    );
}

function handleAddGroupMemberSearch(event) {
    const query = event.target.value.trim();

    clearTimeout(state.addMemberSearchTimeout);
    state.addMemberSearchSequence++;

    const resultsContainer =
        document.getElementById("addGroupMemberSearchResults");

    if (query.length < 2) {
        resultsContainer.replaceChildren();
        return;
    }

    state.addMemberSearchTimeout = setTimeout(() => {
        searchAddGroupMembers(query);
    }, 300);
}

async function searchAddGroupMembers(query) {
    const resultsContainer =
        document.getElementById("addGroupMemberSearchResults");

    const searchSequence = ++state.addMemberSearchSequence;

    try {
        const params = new URLSearchParams({
            q: query,
            page: "0",
            size: "20"
        });

        const result = await apiRequest(
            `/users/search?${params.toString()}`
        );

        if (searchSequence !== state.addMemberSearchSequence) {
            return;
        }

        resultsContainer.replaceChildren();

        const users = Array.isArray(result)
            ? result
            : result?.content || [];

        const existingMemberIds = getExistingGroupMemberIds();

        const availableUsers = users.filter(user => {
            const userId = Number(user.id);

            const isCurrentUser =
                userId === Number(state.currentUser.id);

            const isAlreadyMember =
                existingMemberIds.has(userId);

            const isAlreadySelected =
                state.selectedAddGroupMembers.some(
                    member => Number(member.id) === userId
                );

            return (
                !isCurrentUser &&
                !isAlreadyMember &&
                !isAlreadySelected
            );
        });

        if (availableUsers.length === 0) {
            const empty = document.createElement("div");

            empty.className = "loading-state";
            empty.textContent = "No available users found.";

            resultsContainer.appendChild(empty);
            return;
        }

        availableUsers.forEach(user => {
            const button = document.createElement("button");

            button.type = "button";
            button.className = "group-member-result";

            const fullName =
                [user.firstName, user.lastName]
                    .filter(Boolean)
                    .join(" ") || user.username;

            const avatar = document.createElement("div");
            avatar.className = "avatar";

            setAvatar(avatar, user.profilePictureUrl, fullName);

            const details = document.createElement("div");
            details.className = "member-details";

            const name = document.createElement("strong");
            name.textContent = fullName;

            const username = document.createElement("small");
            username.textContent = `@${user.username}`;

            details.append(name, username);

            const addIcon = document.createElement("span");
            addIcon.className = "member-add-icon";
            addIcon.textContent = "+";

            button.append(avatar, details, addIcon);

            button.addEventListener("click", () => {
                addExistingGroupMemberToSelection(user);
            });

            resultsContainer.appendChild(button);
        });

    } catch (error) {
        console.error("Existing group member search failed:", error);

        if (searchSequence === state.addMemberSearchSequence) {
            showToast(error.message);
        }
    }
}

function addExistingGroupMemberToSelection(user) {
    const userId = Number(user.id);

    if (
        userId === Number(state.currentUser.id) ||
        getExistingGroupMemberIds().has(userId)
    ) {
        return;
    }

    const alreadySelected = state.selectedAddGroupMembers.some(
        member => Number(member.id) === userId
    );

    if (alreadySelected) {
        return;
    }

    state.selectedAddGroupMembers.push(user);

    renderSelectedAddGroupMembers();

    const query = document.getElementById("addGroupMemberSearchInput")
        .value.trim();

    if (query.length >= 2) {
        searchAddGroupMembers(query);
    }
}

function removeExistingGroupMemberFromSelection(userId) {
    state.selectedAddGroupMembers =
        state.selectedAddGroupMembers.filter(
            member => Number(member.id) !== Number(userId)
        );

    renderSelectedAddGroupMembers();

    const query = document.getElementById("addGroupMemberSearchInput")
        .value.trim();

    if (query.length >= 2) {
        searchAddGroupMembers(query);
    }
}

function renderSelectedAddGroupMembers() {
    const container =
        document.getElementById("selectedAddGroupMembers");

    if (!container) {
        return;
    }

    container.replaceChildren();

    if (state.selectedAddGroupMembers.length === 0) {
        const empty = document.createElement("p");

        empty.className = "group-empty-selection";
        empty.textContent = "No members selected yet.";

        container.appendChild(empty);
        return;
    }

    state.selectedAddGroupMembers.forEach(member => {
        const chip = document.createElement("div");
        chip.className = "selected-member-chip";

        const name = document.createElement("span");

        name.textContent =
            [member.firstName, member.lastName]
                .filter(Boolean)
                .join(" ") || member.username;

        const removeButton = document.createElement("button");

        removeButton.type = "button";
        removeButton.className = "remove-selected-member";
        removeButton.textContent = "×";
        removeButton.title = `Remove ${name.textContent}`;

        removeButton.addEventListener("click", () => {
            removeExistingGroupMemberFromSelection(member.id);
        });

        chip.append(name, removeButton);

        container.appendChild(chip);
    });
}

async function handleAddGroupMembers(event) {
    event.preventDefault();

    const conversation = state.activeConversation;

    const submitButton = document.getElementById(
        "confirmAddGroupMembersButton"
    );

    if (!conversation || conversation.type !== "GROUP") {
        showToast("No group conversation is currently selected.");
        return;
    }

    if (state.selectedAddGroupMembers.length === 0) {
        showToast("Please select at least one person.");
        return;
    }

    const groupId = conversation.id;

    const memberIds = state.selectedAddGroupMembers.map(
        member => member.id
    );

    submitButton.disabled = true;
    submitButton.textContent = "Adding...";

    try {
        await apiRequest(`/groups/${groupId}/members`, {
            method: "POST",
            body: JSON.stringify({
                memberIds
            })
        });

        closeAddGroupMembersDialog();

        await loadConversations();

        const updatedConversation = await apiRequest(
            `/conversations/${groupId}`
        );

        if (!updatedConversation) {
            throw new Error(
                "Members were added, but the updated group could not be loaded."
            );
        }

        const index = state.conversations.findIndex(
            item => Number(item.id) === Number(groupId)
        );

        if (index !== -1) {
            state.conversations[index] = updatedConversation;
        } else {
            state.conversations.push(updatedConversation);
        }

        renderConversations();

        await openConversation(updatedConversation);

        showToast("Group members added successfully.");

    } catch (error) {
        console.error("Adding group members failed:", error);

        if (error.status === 403) {
            showToast("You do not have permission to add members to this group.");
        } else {
            showToast(
                error.message || "Could not add members to this group."
            );
        }

    } finally {
        submitButton.disabled = false;
        submitButton.textContent = "Add members";
    }
}


/* =========================================
   IN-APP NOTIFICATIONS
========================================= */

async function loadNotifications() {
    if (state.notificationLoading) {
        return;
    }

    state.notificationLoading = true;

    try {
        const response = await apiRequest("/notifications?page=0&size=20");

        const notifications = Array.isArray(response)
            ? response
            : response?.content || response?.items || [];

        state.notifications = notifications.slice(0, 20);

        renderNotifications();

    } catch (error) {
        console.error("Could not load notifications:", error);
    } finally {
        state.notificationLoading = false;
    }
}

async function loadUnreadNotificationCount() {
    try {
        const response = await apiRequest("/notifications/unread/count");

        updateUnreadNotificationCount(
            Number(response?.unreadCount ?? response?.count ?? 0)
        );

    } catch (error) {
        console.error("Could not load unread notification count:", error);
    }
}

function updateUnreadNotificationCount(count) {
    const normalizedCount = Number.isFinite(Number(count))
        ? Math.max(0, Number(count))
        : 0;

    state.unreadNotificationCount = normalizedCount;

    const badge = document.getElementById("notificationBadge");

    const markAllButton = document.getElementById(
        "markAllNotificationsReadButton"
    );

    if (badge) {
        badge.textContent = normalizedCount > 99
            ? "99+"
            : String(normalizedCount);

        badge.classList.toggle("hidden", normalizedCount === 0);

        badge.setAttribute(
            "aria-label",
            `${normalizedCount} unread notification${normalizedCount === 1 ? "" : "s"}`
        );
    }

    if (markAllButton) {
        markAllButton.disabled = normalizedCount === 0;
    }
}

function subscribeToNotifications() {
    if (!state.stompClient || !state.stompClient.connected) {
        return;
    }

    if (state.notificationSubscription) {
        state.notificationSubscription.unsubscribe();
        state.notificationSubscription = null;
    }

    state.notificationSubscription = state.stompClient.subscribe(
        "/user/queue/notifications",
        frame => {
            try {
                const notification = JSON.parse(frame.body);

                handleIncomingNotification(notification);

            } catch (error) {
                console.error(
                    "Could not process incoming notification:",
                    error
                );
            }
        }
    );
}

function handleIncomingNotification(notification) {
    if (!notification || notification.id == null) {
        console.warn("Received an invalid notification:", notification);
        return;
    }

    const alreadyExists = state.notifications.some(
        item => String(item.id) === String(notification.id)
    );

    if (alreadyExists) {
        return;
    }

    state.notifications.unshift(notification);

    state.notifications = state.notifications.slice(0, 20);

    if (!notification.read) {
        updateUnreadNotificationCount(
            state.unreadNotificationCount + 1
        );
    }

    renderNotifications();

    const actor = notification.actorUsername || "Someone";

    const conversationName =
        notification.conversationName || "a conversation";

    if (
        notification.type === "NEW_MESSAGE" &&
        notification.conversationType === "GROUP"
    ) {
        showToast(`${actor} sent a message in ${conversationName}.`);
    } else {
        showToast(`${actor} sent you a new message.`);
    }
}

function toggleNotificationDropdown() {
    const dropdown = document.getElementById("notificationDropdown");

    const button = document.getElementById("notificationButton");

    if (!dropdown) {
        return;
    }

    const willOpen = dropdown.classList.contains("hidden");

    dropdown.classList.toggle("hidden", !willOpen);

    button?.setAttribute("aria-expanded", String(willOpen));

    if (willOpen) {
        loadNotifications();
    }
}

function closeNotificationDropdown() {
    const dropdown = document.getElementById("notificationDropdown");

    const button = document.getElementById("notificationButton");

    dropdown?.classList.add("hidden");

    button?.setAttribute("aria-expanded", "false");
}

/*
 * Group messages belonging to the same group conversation.
 *
 * Private messages remain individual notifications.
 * Other notification types remain individual notifications.
 */
function getGroupedNotifications() {
    const groupedItems = [];

    const groupMap = new Map();

    state.notifications.forEach(notification => {
        const isGroupMessage =
            notification.type === "NEW_MESSAGE" &&
            notification.conversationType === "GROUP" &&
            notification.conversationId != null;

        if (!isGroupMessage) {
            groupedItems.push({
                type: "single",
                notification
            });

            return;
        }

        const conversationId = String(notification.conversationId);

        if (!groupMap.has(conversationId)) {
            const groupItem = {
                type: "group",
                conversationId: notification.conversationId,
                conversationName:
                    notification.conversationName || "Group conversation",
                notifications: [],
                latestNotification: notification
            };

            groupMap.set(conversationId, groupItem);

            groupedItems.push(groupItem);
        }

        const groupItem = groupMap.get(conversationId);

        groupItem.notifications.push(notification);

        if (
            new Date(notification.createdAt || 0).getTime() >
            new Date(groupItem.latestNotification.createdAt || 0).getTime()
        ) {
            groupItem.latestNotification = notification;
        }

        if (notification.conversationName) {
            groupItem.conversationName = notification.conversationName;
        }
    });

    return groupedItems;
}

function renderNotifications() {
    const container = document.getElementById("notificationList");

    if (!container) {
        return;
    }

    container.replaceChildren();

    if (state.notifications.length === 0) {
        const empty = document.createElement("div");

        empty.className = "notification-empty";

        empty.textContent = state.notificationLoading
            ? "Loading notifications..."
            : "You're all caught up.";

        container.appendChild(empty);

        return;
    }

    const groupedItems = getGroupedNotifications();

    groupedItems.forEach(groupItem => {
        if (groupItem.type === "group") {
            renderGroupNotification(groupItem, container);
        } else {
            renderSingleNotification(
                groupItem.notification,
                container
            );
        }
    });
}

/*
 * Render one group notification.
 *
 * Example:
 *
 * Hardware Team
 * Rahul: Please check the new message.
 *                         (2)  •
 */
function renderGroupNotification(groupItem, container) {
    const item = document.createElement("button");

    item.type = "button";
    item.className = "notification-item group-notification-item";

    const notifications = groupItem.notifications;

    const latestNotification = groupItem.latestNotification;

    const unreadNotifications = notifications.filter(
        notification => !notification.read
    );

    const serverUnreadCount = Number(
        latestNotification.groupUnreadCount ?? 0
    );

    const unreadCount = Math.max(
        0,
        serverUnreadCount,
        unreadNotifications.length
    );

    const isUnread = unreadCount > 0;

    if (isUnread) {
        item.classList.add("unread");
    }

    const avatar = document.createElement("div");

    avatar.className = "notification-avatar group-notification-avatar";

    avatar.textContent = getInitials(groupItem.conversationName);

    const details = document.createElement("div");

    details.className = "notification-details";

    const title = document.createElement("strong");

    title.className = "notification-group-name";

    title.textContent = groupItem.conversationName;

    const message = document.createElement("span");

    message.className = "notification-message";

    const sender =
        latestNotification.actorUsername || "Someone";

    const preview =
        latestNotification.content || "Sent a message.";

    message.textContent = `${sender}: ${preview}`;

    const time = document.createElement("small");

    time.className = "notification-time";

    time.textContent = formatNotificationTime(
        latestNotification.createdAt
    );

    details.append(title, message, time);

    const status = document.createElement("div");

    status.className = "notification-status";

    if (isUnread) {
        const count = document.createElement("span");

        count.className = "notification-group-count";

        count.textContent = unreadCount > 99
            ? "99+"
            : String(unreadCount);

        count.setAttribute(
            "aria-label",
            `${unreadCount} unread messages`
        );

        status.appendChild(count);
    }

    const unreadDot = document.createElement("span");

    unreadDot.className = "notification-unread-dot";

    unreadDot.setAttribute("aria-hidden", "true");

    unreadDot.classList.toggle("hidden", !isUnread);

    status.appendChild(unreadDot);

    item.append(avatar, details, status);

    item.addEventListener("click", () => {
        openGroupNotification(groupItem);
    });

    container.appendChild(item);
}

/*
 * Render an individual notification.
 *
 * Used for private messages and other notification types.
 */
function renderSingleNotification(notification, container) {
    const item = document.createElement("button");

    item.type = "button";

    item.className = "notification-item";

    if (!notification.read) {
        item.classList.add("unread");
    }

    const avatar = document.createElement("div");

    avatar.className = "notification-avatar";

    avatar.textContent = getInitials(
        notification.actorUsername || "U"
    );

    const details = document.createElement("div");

    details.className = "notification-details";

    const message = document.createElement("span");

    message.className = "notification-message";

    const actor = notification.actorUsername || "Someone";

    if (notification.type === "NEW_MESSAGE") {
        message.textContent = notification.content
            ? `${actor}: ${notification.content}`
            : `${actor} sent you a message.`;
    } else {
        message.textContent = notification.content
            || `${actor} sent you a notification.`;
    }

    const time = document.createElement("small");

    time.className = "notification-time";

    time.textContent = formatNotificationTime(
        notification.createdAt
    );

    details.append(message, time);

    const unreadDot = document.createElement("span");

    unreadDot.className = "notification-unread-dot";

    unreadDot.setAttribute("aria-hidden", "true");

    unreadDot.classList.toggle("hidden", Boolean(notification.read));

    item.append(avatar, details, unreadDot);

    item.addEventListener("click", () => {
        openNotification(notification);
    });

    container.appendChild(item);
}

function formatNotificationTime(value) {
    if (!value) {
        return "";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return "";
    }

    const now = new Date();

    const difference = Math.max(
        0,
        now.getTime() - date.getTime()
    );

    const minutes = Math.floor(difference / 60000);

    if (minutes < 1) {
        return "Just now";
    }

    if (minutes < 60) {
        return `${minutes}m ago`;
    }

    const hours = Math.floor(minutes / 60);

    if (hours < 24) {
        return `${hours}h ago`;
    }

    const days = Math.floor(hours / 24);

    if (days < 7) {
        return `${days}d ago`;
    }

    return date.toLocaleDateString([], {
        year: "numeric",
        month: "short",
        day: "numeric"
    });
}

/*
 * Mark one notification as read.
 */
async function markNotificationAsRead(notificationId) {
    const notification = state.notifications.find(
        item => String(item.id) === String(notificationId)
    );

    if (!notification || notification.read) {
        return;
    }

    try {
        await apiRequest(`/notifications/${notificationId}/read`, {
            method: "PATCH"
        });

        notification.read = true;

        await loadUnreadNotificationCount();

        renderNotifications();

    } catch (error) {
        console.error("Could not mark notification as read:", error);

        showToast(
            error.message || "Could not update notification."
        );
    }
}

/*
 * Mark all currently loaded unread notifications from one group as read.
 *
 * The backend currently exposes a per-notification read endpoint,
 * so each loaded notification is updated individually.
 */
async function markGroupNotificationsAsRead(groupItem) {
    const unreadNotifications = groupItem.notifications.filter(
        notification => !notification.read
    );

    if (unreadNotifications.length === 0) {
        return;
    }

    const results = await Promise.allSettled(
        unreadNotifications.map(notification =>
            apiRequest(
                `/notifications/${notification.id}/read`,
                {
                    method: "PATCH"
                }
            )
        )
    );

    results.forEach((result, index) => {
        if (result.status === "fulfilled") {
            unreadNotifications[index].read = true;
        }
    });

    const failedCount = results.filter(
        result => result.status === "rejected"
    ).length;

    await loadUnreadNotificationCount();

    renderNotifications();

    if (failedCount > 0) {
        showToast(
            "Some group notifications could not be marked as read."
        );
    }
}

/*
 * Open a group conversation from its grouped notification.
 */
async function openGroupNotification(groupItem) {
    closeNotificationDropdown();

    await markGroupNotificationsAsRead(groupItem);

    await openNotificationConversation(
        groupItem.conversationId
    );
}

/*
 * Open a conversation using its ID.
 */
async function openNotificationConversation(conversationId) {
    if (!conversationId) {
        return;
    }

    const existingConversation = state.conversations.find(
        conversation =>
            Number(conversation.id) === Number(conversationId)
    );

    if (existingConversation) {
        await openConversation(existingConversation);
        return;
    }

    try {
        const conversation = await apiRequest(
            `/conversations/${conversationId}`
        );

        if (!conversation) {
            throw new Error("The conversation could not be found.");
        }

        const alreadyExists = state.conversations.some(
            item => Number(item.id) === Number(conversation.id)
        );

        if (!alreadyExists) {
            state.conversations.unshift(conversation);

            renderConversations();
        }

        await openConversation(conversation);

    } catch (error) {
        console.error(
            "Could not open notification conversation:",
            error
        );

        showToast(
            error.message || "Could not open this conversation."
        );
    }
}

/*
 * Open a normal individual notification.
 */
async function openNotification(notification) {
    closeNotificationDropdown();

    await markNotificationAsRead(notification.id);

    if (!notification.conversationId) {
        return;
    }

    await openNotificationConversation(
        notification.conversationId
    );
}

/*
 * Mark all notifications as read.
 */
async function markAllNotificationsAsRead() {
    const button = document.getElementById(
        "markAllNotificationsReadButton"
    );

    if (state.unreadNotificationCount === 0) {
        return;
    }

    if (button) {
        button.disabled = true;
    }

    try {
        await apiRequest("/notifications/read-all", {
            method: "PATCH"
        });

        state.notifications = state.notifications.map(
            notification => ({
                ...notification,
                read: true
            })
        );

        updateUnreadNotificationCount(0);

        renderNotifications();

    } catch (error) {
        console.error(
            "Could not mark all notifications as read:",
            error
        );

        showToast(
            error.message || "Could not update notifications."
        );

    } finally {
        if (button) {
            button.disabled =
                state.unreadNotificationCount === 0;
        }
    }
}


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

/* =========================================
   UTILITIES
========================================= */

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