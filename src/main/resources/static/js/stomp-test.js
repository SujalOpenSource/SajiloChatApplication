
let stompClient = null;

let conversationSubscription = null;
let presenceSubscription = null;
let presenceSnapshotSubscription = null;
let statusSubscription = null;
let typingSubscription = null;

let currentUsername = null;
let activeConversationId = null;

let typingTimeout = null;
let isCurrentlyTyping = false;

const presenceUsers = new Map();
const presenceChangedSinceSnapshot = new Set();

const pendingReadMessageIds = new Set();
const processedMessageIds = new Set();

const remoteTypingUsers = new Map();
const remoteTypingTimeouts = new Map();

document.addEventListener("DOMContentLoaded", function () {

    document.getElementById("connectButton")
        .addEventListener("click", connect);

    document.getElementById("subscribeButton")
        .addEventListener("click", subscribe);

    document.getElementById("sendButton")
        .addEventListener("click", sendMessage);

    const messageInput = document.getElementById("messageInput");

    messageInput.addEventListener("keydown", function (event) {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            sendMessage();
        }
    });

    messageInput.addEventListener("input", handleTypingInput);

    messageInput.addEventListener("blur", stopTyping);

    document.addEventListener("visibilitychange", function () {
        if (document.visibilityState !== "visible") {
            stopTyping();
            return;
        }

        if (!stompClient || !stompClient.connected) return;

        pendingReadMessageIds.forEach(function (messageId) {
            sendReadReceipt(messageId);
        });
    });
});


// STATUS AND DEBUG

function updateStatus(message) {
    document.getElementById("status").textContent =
        "Status: " + message;
}

function logDebug(message) {
    console.log("[SajiloChat]", message);

    const container = document.getElementById("debugLogs");
    const entry = document.createElement("div");

    entry.className = "debug-entry";
    entry.textContent =
        new Date().toLocaleTimeString() + " - " + message;

    container.appendChild(entry);
}


// JWT USERNAME EXTRACTION

function getUsernameFromToken(token) {
    try {
        const payload = token.split(".")[1];

        if (!payload) {
            throw new Error("Invalid JWT");
        }

        const base64 = payload
            .replace(/-/g, "+")
            .replace(/_/g, "/");

        const paddedBase64 =
            base64 + "=".repeat((4 - base64.length % 4) % 4);

        const decoded = JSON.parse(atob(paddedBase64));

        if (!decoded.sub) {
            throw new Error("JWT subject is missing");
        }

        return decoded.sub;

    } catch (error) {
        throw new Error("Could not read username from JWT.");
    }
}


// CONNECT TO WEBSOCKET

async function connect() {
    const token = document.getElementById("token").value.trim();

    if (!token) {
        alert("Please enter your JWT access token.");
        return;
    }

    if (stompClient && stompClient.connected) {
        alert("Already connected.");
        return;
    }

    if (stompClient) {
        const previousClient = stompClient;
        stompClient = null;

        try {
            await previousClient.deactivate();
        } catch (error) {
            console.error("Error closing previous connection:", error);
        }
    }

    try {
        currentUsername = getUsernameFromToken(token);
    } catch (error) {
        alert(error.message);
        return;
    }

    const connectButton =
        document.getElementById("connectButton");

    connectButton.disabled = true;

    stompClient = new StompJs.Client({

        brokerURL: "ws://localhost:8080/ws",

        connectHeaders: {
            Authorization: "Bearer " + token
        },

        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        reconnectDelay: 0,

        onConnect: function () {
            updateStatus("STOMP Connected as " + currentUsername);
            logDebug("STOMP connection established.");

            document.getElementById("subscribeButton").disabled = false;
            connectButton.disabled = false;

            subscribeToStatusUpdates();
        },

        onStompError: function (frame) {
            const errorMessage =
                frame.headers.message || "Unknown STOMP error";

            updateStatus("STOMP Error: " + errorMessage);
            logDebug("STOMP ERROR: " + errorMessage);

            connectButton.disabled = false;
        },

        onWebSocketError: function () {
            updateStatus("WebSocket error");
            logDebug("WebSocket error occurred.");

            connectButton.disabled = false;
        },

        onWebSocketClose: function () {
            updateStatus("WebSocket disconnected");
            logDebug("WebSocket connection closed.");

            connectButton.disabled = false;

            document.getElementById("subscribeButton").disabled = true;
            document.getElementById("sendButton").disabled = true;

            conversationSubscription = null;
            presenceSubscription = null;
            presenceSnapshotSubscription = null;
            statusSubscription = null;
            typingSubscription = null;

            pendingReadMessageIds.clear();
            processedMessageIds.clear();

            presenceUsers.clear();
            presenceChangedSinceSnapshot.clear();

            clearTypingIndicators();

            activeConversationId = null;

            document.getElementById("presenceEvents").textContent =
                "Disconnected. Presence is not available.";
        }
    });

    updateStatus("Connecting...");
    logDebug("Connecting to WebSocket...");

    stompClient.activate();
}


// PRIVATE MESSAGE STATUS SUBSCRIPTION

function subscribeToStatusUpdates() {
    if (statusSubscription) {
        statusSubscription.unsubscribe();
        statusSubscription = null;
    }

    statusSubscription = stompClient.subscribe(
        "/user/queue/message-status",
        function (message) {
            try {
                const event = JSON.parse(message.body);

                logDebug(
                    "Received status notification: " +
                    event.status +
                    " for message " +
                    event.messageId
                );

                displayStatusUpdate(event);

            } catch (error) {
                logDebug("Could not parse status notification.");
            }
        },
        {
            id: "message-status-subscription"
        }
    );

    logDebug("Subscribed to private message-status queue.");
}


// CONVERSATION SUBSCRIPTIONS

function subscribe() {
    const conversationId =
        document.getElementById("conversationId").value.trim();

    if (!conversationId || !/^[1-9]\d*$/.test(conversationId)) {
        alert("Enter a valid conversation ID.");
        return;
    }

    if (!stompClient || !stompClient.connected) {
        alert("Connect first.");
        return;
    }

    // Stop typing in the previous conversation.
    stopTyping();
    clearTypingIndicators();

    // Remove previous subscriptions.
    if (conversationSubscription) {
        conversationSubscription.unsubscribe();
        conversationSubscription = null;
    }

    if (presenceSubscription) {
        presenceSubscription.unsubscribe();
        presenceSubscription = null;
    }

    if (presenceSnapshotSubscription) {
        presenceSnapshotSubscription.unsubscribe();
        presenceSnapshotSubscription = null;
    }

    if (typingSubscription) {
        typingSubscription.unsubscribe();
        typingSubscription = null;
    }

    activeConversationId = conversationId;

    pendingReadMessageIds.clear();
    processedMessageIds.clear();

    presenceUsers.clear();
    presenceChangedSinceSnapshot.clear();

    document.getElementById("messages").replaceChildren();
    document.getElementById("statusUpdates").replaceChildren();

    document.getElementById("presenceEvents").textContent =
        "Loading current presence...";

    document.getElementById("typingIndicator").textContent = "";

    const messageDestination =
        "/topic/conversations/" + conversationId + "/messages";

    const presenceDestination =
        "/topic/conversations/" + conversationId + "/presence";

    const snapshotDestination =
        "/app/conversations/" + conversationId + "/presence";

    const typingDestination =
        "/topic/conversations/" + conversationId + "/typing";


    // SUBSCRIBE TO MESSAGES

    conversationSubscription = stompClient.subscribe(
        messageDestination,
        function (message) {
            try {
                const response = JSON.parse(message.body);

                if (processedMessageIds.has(response.id)) {
                    logDebug(
                        "Duplicate message event ignored: " + response.id
                    );
                    return;
                }

                processedMessageIds.add(response.id);

                logDebug("Received message ID: " + response.id);

                displayMessage(response);

                logDebug("Message sender: " + response.sender.username);
                logDebug("Currently connected user: " + currentUsername);

                if (response.sender.username === currentUsername) {
                    logDebug(
                        "Skipping receipt because this is my own message."
                    );
                    return;
                }

                sendDeliveryReceipt(response.id);

                if (document.visibilityState === "visible") {
                    sendReadReceipt(response.id);
                } else {
                    pendingReadMessageIds.add(response.id);

                    logDebug(
                        "Message " + response.id +
                        " is pending a READ receipt."
                    );
                }

            } catch (error) {
                logDebug(
                    "Error processing received message: " + error.message
                );
            }
        }
    );


    // SUBSCRIBE TO LIVE PRESENCE

    presenceSubscription = stompClient.subscribe(
        presenceDestination,
        function (message) {
            try {
                const event = JSON.parse(message.body);

                presenceChangedSinceSnapshot.add(event.userId);

                displayPresenceEvent(event);

                logDebug(
                    event.username + " is now " + event.status
                );

                console.log("Presence event:", event);

            } catch (error) {
                logDebug(
                    "Error processing presence event: " + error.message
                );
            }
        }
    );


    // REQUEST INITIAL PRESENCE SNAPSHOT

    presenceSnapshotSubscription = stompClient.subscribe(
        snapshotDestination,
        function (message) {
            try {
                const snapshot = JSON.parse(message.body);

                displayPresenceSnapshot(snapshot);

                logDebug(
                    "Received initial presence snapshot for conversation " +
                    snapshot.conversationId +
                    ". Online users: " +
                    snapshot.onlineUsers.length
                );

            } catch (error) {
                logDebug(
                    "Error processing presence snapshot: " + error.message
                );

            } finally {
                if (presenceSnapshotSubscription) {
                    presenceSnapshotSubscription.unsubscribe();
                    presenceSnapshotSubscription = null;
                }
            }
        }
    );


    // SUBSCRIBE TO TYPING EVENTS

    typingSubscription = stompClient.subscribe(
        typingDestination,
        function (message) {
            try {
                const event = JSON.parse(message.body);

                if (event.conversationId != conversationId) {
                    return;
                }

                // Ignore our own typing event.
                if (event.username === currentUsername) {
                    return;
                }

                if (event.typing) {
                    showRemoteTyping(event);
                } else {
                    removeRemoteTyping(event.username);
                }

                logDebug(
                    event.username +
                    (event.typing
                        ? " started typing."
                        : " stopped typing.")
                );

            } catch (error) {
                logDebug(
                    "Error processing typing event: " + error.message
                );
            }
        }
    );

    document.getElementById("sendButton").disabled = false;

    updateStatus("Subscribed to conversation " + conversationId);

    logDebug("Subscribed to conversation messages: " + conversationId);
    logDebug("Subscribed to live presence: " + conversationId);
    logDebug("Requested initial presence snapshot: " + conversationId);
    logDebug("Subscribed to typing events: " + conversationId);
}


// SEND MESSAGE

function sendMessage() {
    const conversationId =
        document.getElementById("conversationId").value;

    const content =
        document.getElementById("messageInput").value.trim();

    if (!content) {
        alert("Enter a message.");
        return;
    }

    if (!stompClient || !stompClient.connected) {
        alert("Connect first.");
        return;
    }

    if (!conversationSubscription) {
        alert("Subscribe to a conversation first.");
        return;
    }

    // Stop typing before sending the message.
    stopTyping();

    stompClient.publish({
        destination:
            "/app/conversations/" + conversationId + "/messages",

        body: JSON.stringify({
            content: content
        })
    });

    logDebug("Message sent to conversation " + conversationId);

    document.getElementById("messageInput").value = "";
}


// DELIVERY RECEIPTS

function sendDeliveryReceipt(messageId) {
    if (!stompClient || !stompClient.connected) {
        logDebug(
            "Cannot send DELIVERED receipt: STOMP disconnected."
        );
        return;
    }

    logDebug("Sending DELIVERED receipt for message: " + messageId);

    stompClient.publish({
        destination:
            "/app/messages/" + messageId + "/delivered",
        body: ""
    });
}


// READ RECEIPTS

function sendReadReceipt(messageId) {
    if (!stompClient || !stompClient.connected) {
        logDebug(
            "Cannot send READ receipt: STOMP disconnected."
        );
        return;
    }

    logDebug("Sending READ receipt for message: " + messageId);

    stompClient.publish({
        destination:
            "/app/messages/" + messageId + "/read",
        body: ""
    });

    pendingReadMessageIds.delete(messageId);
}


// DISPLAY MESSAGES

function displayMessage(message) {
    const container = document.getElementById("messages");

    const messageElement = document.createElement("div");
    messageElement.className = "message";

    const sender = document.createElement("strong");
    sender.textContent = message.sender.username + ": ";

    const content = document.createElement("span");
    content.textContent = message.content;

    const messageId = document.createElement("small");
    messageId.textContent = " (Message ID: " + message.id + ")";

    messageElement.appendChild(sender);
    messageElement.appendChild(content);
    messageElement.appendChild(messageId);

    container.appendChild(messageElement);
}


// DISPLAY DELIVERY / READ STATUS

function displayStatusUpdate(event) {
    const container = document.getElementById("statusUpdates");

    const element = document.createElement("div");
    element.className = "status-event";

    element.textContent =
        "Message #" + event.messageId +
        " | Recipient: " + event.recipientUsername +
        " | Status: " + event.status;

    container.appendChild(element);
}


// INITIAL PRESENCE SNAPSHOT

function displayPresenceSnapshot(snapshot) {
    if (!snapshot || !Array.isArray(snapshot.onlineUsers)) {
        throw new Error("Invalid presence snapshot received.");
    }

    snapshot.onlineUsers.forEach(function (user) {
        if (presenceChangedSinceSnapshot.has(user.userId)) {
            return;
        }

        presenceUsers.set(user.userId, {
            userId: user.userId,
            username: user.username,
            status: user.status || "ONLINE",
            lastSeenAt: user.lastSeenAt || null
        });
    });

    renderPresenceUsers();
}


// LIVE PRESENCE EVENTS

function displayPresenceEvent(event) {
    if (!event || event.userId == null || !event.username || !event.status) {
        throw new Error("Invalid presence event received.");
    }

    presenceUsers.set(event.userId, {
        userId: event.userId,
        username: event.username,
        status: event.status,
        lastSeenAt: event.lastSeenAt || null
    });

    renderPresenceUsers();
}


// RENDER PRESENCE USERS

function renderPresenceUsers() {
    const container = document.getElementById("presenceEvents");

    container.innerHTML = "";

    const users = Array.from(presenceUsers.values());

    if (users.length === 0) {
        container.textContent = "No online users currently known.";
        return;
    }

    users.sort(function (first, second) {
        return first.username.localeCompare(second.username);
    });

    users.forEach(function (user) {
        const element = document.createElement("div");

        element.className =
            "presence-event " +
            (user.status === "ONLINE" ? "online" : "offline");

        element.textContent =
            user.username + " - " + user.status +
            (user.lastSeenAt
                ? " | Last seen: " + user.lastSeenAt
                : "");

        container.appendChild(element);
    });
}


// TYPING INDICATORS

function handleTypingInput() {
    if (!stompClient || !stompClient.connected) {
        return;
    }

    if (!conversationSubscription || !activeConversationId) {
        return;
    }

    // Notify others only when typing starts.
    if (!isCurrentlyTyping) {
        publishTypingStatus(true);
        isCurrentlyTyping = true;

        logDebug("Typing started.");
    }

    // Reset inactivity timer.
    clearTimeout(typingTimeout);

    typingTimeout = setTimeout(function () {
        stopTyping();
    }, 1500);
}


function stopTyping() {
    clearTimeout(typingTimeout);
    typingTimeout = null;

    if (!isCurrentlyTyping) {
        return;
    }

    publishTypingStatus(false);

    isCurrentlyTyping = false;

    logDebug("Typing stopped.");
}


function publishTypingStatus(typing) {
    if (!stompClient || !stompClient.connected) {
        return;
    }

    if (!activeConversationId) {
        return;
    }

    stompClient.publish({
        destination:
            "/app/conversations/" +
            activeConversationId +
            "/typing",

        body: JSON.stringify({
            conversationId: Number(activeConversationId),
            typing: typing
        })
    });
}


// SHOW REMOTE TYPING

function showRemoteTyping(event) {
    const username = event.username;

    if (!username) {
        return;
    }

    remoteTypingUsers.set(username, true);

    clearTimeout(remoteTypingTimeouts.get(username));

    // Fallback if a typing=false event is missed.
    const timeout = setTimeout(function () {
        removeRemoteTyping(username);
    }, 5000);

    remoteTypingTimeouts.set(username, timeout);

    renderTypingIndicator();
}


// REMOVE REMOTE TYPING

function removeRemoteTyping(username) {
    remoteTypingUsers.delete(username);

    clearTimeout(remoteTypingTimeouts.get(username));
    remoteTypingTimeouts.delete(username);

    renderTypingIndicator();
}


// RENDER TYPING INDICATOR

function renderTypingIndicator() {
    const container =
        document.getElementById("typingIndicator");

    if (!container) {
        return;
    }

    const usernames = Array.from(remoteTypingUsers.keys());

    if (usernames.length === 0) {
        container.textContent = "";
        return;
    }

    if (usernames.length === 1) {
        container.textContent =
            usernames[0] + " is typing...";
    } else if (usernames.length === 2) {
        container.textContent =
            usernames.join(" and ") + " are typing...";
    } else {
        container.textContent =
            usernames.length + " people are typing...";
    }
}


// CLEAR TYPING STATE

function clearTypingIndicators() {
    clearTimeout(typingTimeout);
    typingTimeout = null;

    remoteTypingTimeouts.forEach(function (timeout) {
        clearTimeout(timeout);
    });

    remoteTypingTimeouts.clear();
    remoteTypingUsers.clear();

    isCurrentlyTyping = false;

    renderTypingIndicator();
}
