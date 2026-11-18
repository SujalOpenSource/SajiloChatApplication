
"use strict";

const AUTH_API = "/api/v1/auth";

let csrfToken = null;
let csrfHeaderName = "X-XSRF-TOKEN";

async function loadCsrfToken() {
    const response = await fetch(`${AUTH_API}/csrf`, {
        method: "GET",
        credentials: "same-origin",
        headers: {
            "Accept": "application/json"
        }
    });

    if (!response.ok) {
        throw new Error("Unable to initialize security protection. Please refresh the page.");
    }

    const data = await response.json();

    csrfToken = data.token;
    csrfHeaderName = data.headerName || "X-XSRF-TOKEN";

    if (!csrfToken) {
        throw new Error("Security token was not returned by the server.");
    }
}

async function apiRequest(endpoint, options = {}) {
    if (!csrfToken) {
        await loadCsrfToken();
    }

    const headers = {
        "Accept": "application/json",
        ...(options.headers || {})
    };

    if (options.body) {
        headers["Content-Type"] = "application/json";
    }

    const method = (options.method || "GET").toUpperCase();

    if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
        headers[csrfHeaderName] = csrfToken;
    }

    const response = await fetch(`${AUTH_API}${endpoint}`, {
        ...options,
        method,
        headers,
        credentials: "same-origin"
    });

    if (response.status === 204) {
        return null;
    }

    const contentType = response.headers.get("content-type") || "";

    let data = null;

    if (contentType.includes("application/json")) {
        data = await response.json();
    } else {
        data = await response.text();
    }

    if (!response.ok) {
        const message =
            typeof data === "object" && data !== null
                ? data.message || data.detail || data.error
                : data;

        throw new Error(message || `Request failed (${response.status}).`);
    }

    return data;
}

function showMessage(elementId, message) {
    const element = document.getElementById(elementId);

    if (!element) return;

    element.textContent = message;
    element.hidden = false;
}

function hideMessage(elementId) {
    const element = document.getElementById(elementId);

    if (element) {
        element.textContent = "";
        element.hidden = true;
    }
}

async function getCurrentUser() {
    return apiRequest("/me");
}

async function initializeHomePage() {
    const status = document.getElementById("statusMessage");
    const authenticatedContent =
        document.getElementById("authenticatedContent");
    const unauthenticatedContent =
        document.getElementById("unauthenticatedContent");

    try {
        await loadCsrfToken();

        const user = await getCurrentUser();

        status.textContent = "Welcome to SajiloChat.";

        authenticatedContent.hidden = false;
        unauthenticatedContent.hidden = true;

        const logoutButton = document.getElementById("logoutButton");

        logoutButton.addEventListener("click", logout);
    } catch (error) {
        status.textContent = "Sign in to start using SajiloChat.";

        authenticatedContent.hidden = true;
        unauthenticatedContent.hidden = false;
    }
}

async function initializeLoginPage() {
    try {
        await loadCsrfToken();

        await getCurrentUser();

        window.location.replace("/");
        return;
    } catch (error) {
        // No active session. Show the login form.
    }

    const form = document.getElementById("loginForm");

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        hideMessage("errorMessage");

        const button = document.getElementById("loginButton");

        button.disabled = true;
        button.textContent = "Signing in...";

        try {
            await loadCsrfToken();

            const identifier =
                document.getElementById("identifier").value.trim();

            const password =
                document.getElementById("password").value;

            await apiRequest("/login", {
                method: "POST",
                body: JSON.stringify({
                    identifier,
                    password
                })
            });

            window.location.replace("/");
        } catch (error) {
            showMessage("errorMessage", error.message);
        } finally {
            button.disabled = false;
            button.textContent = "Login";
        }
    });
}

async function initializeRegisterPage() {
    const form = document.getElementById("registerForm");

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        hideMessage("errorMessage");
        hideMessage("successMessage");

        const button = document.getElementById("registerButton");

        button.disabled = true;
        button.textContent = "Creating account...";

        try {
            await loadCsrfToken();

            const registrationData = {
                username: document.getElementById("username").value.trim(),
                firstName: document.getElementById("firstName").value.trim(),
                lastName: document.getElementById("lastName").value.trim(),
                email: document.getElementById("email").value.trim(),
                phoneNumber: document.getElementById("phoneNumber").value.trim(),
                password: document.getElementById("registerPassword").value
            };

            await apiRequest("/register", {
                method: "POST",
                body: JSON.stringify(registrationData)
            });

            showMessage(
                "successMessage",
                "Account created successfully. Redirecting to login..."
            );

            form.reset();

            setTimeout(() => {
                window.location.href = "/login.html";
            }, 1500);

        } catch (error) {
            showMessage("errorMessage", error.message);
        } finally {
            button.disabled = false;
            button.textContent = "Create account";
        }
    });
}

async function logout() {
    try {
        await apiRequest("/logout", {
            method: "POST"
        });

        csrfToken = null;

        window.location.replace("/login.html");
    } catch (error) {
        alert(error.message || "Unable to log out. Please try again.");
    }
}
