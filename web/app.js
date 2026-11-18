(function () {
  "use strict";
  var state = { donations: [], user: null, notifications: [], toastTimer: null };
  var labels = { AVAILABLE: "Available", ACCEPTED: "Accepted", COLLECTED: "Collected", DELIVERED: "Delivered" };
  var foodIcons = ["🥗", "🍲", "🍎", "🥘", "🥖", "🍱"];
  var donationFields = [
    { input: document.getElementById("food-type"), error: document.getElementById("food-type-error"), validate: function (v) {
      if (!v) return "Enter the food name."; if (!/\p{L}/u.test(v)) return "Enter a valid food name containing at least one letter.";
      if (v.length > 100) return "Food name must be 100 characters or fewer."; return "";
    } },
    { input: document.getElementById("quantity"), error: document.getElementById("quantity-error"), validate: function (v) {
      if (!v) return "Enter the number of meals."; if (!/^\d+$/.test(v)) return "Enter a whole-number quantity.";
      var n = Number(v); if (n < 1) return "Quantity must be at least 1."; if (n > 100000) return "Quantity cannot exceed 100,000."; return "";
    } },
    { input: document.getElementById("location"), error: document.getElementById("location-error"), validate: function (v) {
      if (!v) return "Enter a pickup location."; if (!/\p{L}/u.test(v)) return "Enter a location name or landmark, not only numbers.";
      if (v.length > 200) return "Pickup location must be 200 characters or fewer."; return "";
    } },
    { input: document.getElementById("pickup-time"), error: document.getElementById("pickup-time-error"), validate: function (v) {
      if (!v) return "Choose a pickup date and time."; if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(v)) return "Choose a valid pickup date and time."; return "";
    } },
    { input: document.getElementById("best-by"), error: document.getElementById("best-by-error"), validate: function (v) {
      if (!v) return ""; if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(v)) return "Choose a valid best-before date and time.";
      var pickup = document.getElementById("pickup-time").value; if (pickup && v < pickup) return "Best-before time cannot be earlier than pickup."; return "";
    } }
  ];

  function validateField(field) {
    var message = field.validate(field.input.value.trim());
    field.input.setCustomValidity(message); field.input.classList.toggle("invalid", Boolean(message));
    field.input.setAttribute("aria-invalid", message ? "true" : "false"); field.error.textContent = message; field.error.hidden = !message;
    return !message;
  }
  function validateDonationForm() {
    var valid = true, first = null;
    donationFields.forEach(function (field) { if (!validateField(field)) { valid = false; if (!first) first = field.input; } });
    if (first) first.focus(); return valid;
  }
  function escapeHtml(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (ch) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch];
    });
  }
  async function request(url, options) {
    var response = await fetch(url, Object.assign({ credentials: "same-origin" }, options || {}));
    var data = await response.json().catch(function () { return {}; });
    if (!response.ok) throw new Error(data.error || "The request could not be completed."); return data;
  }
  function formBody(form) { return new URLSearchParams(new FormData(form)).toString(); }
  function formOptions(form, method) { return { method: method, headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" }, body: formBody(form) }; }
  function showToast(message, isError) {
    var toast = document.getElementById("toast"); toast.textContent = message;
    toast.className = "toast show" + (isError ? " error" : ""); clearTimeout(state.toastTimer);
    state.toastTimer = setTimeout(function () { toast.className = "toast"; }, 3500);
  }
  function setConnection(connected) {
    var el = document.getElementById("connection"); el.classList.toggle("connected", connected);
    el.querySelector("span").textContent = connected ? "Database connected" : "Database offline";
  }
  function currentRole(role) { return Boolean(state.user && state.user.role === role); }
  function syncAccountUI() {
    var signedIn = Boolean(state.user), roleName = state.user ? state.user.role.toLowerCase() : "Guest";
    document.getElementById("login-open").hidden = signedIn;
    document.getElementById("register-open").hidden = signedIn;
    document.getElementById("logout-button").hidden = !signedIn;
    document.getElementById("account-label").hidden = !signedIn;
    document.getElementById("account-label").textContent = signedIn ? state.user.name + " · " + roleName : "";
    document.getElementById("sidebar-name").textContent = signedIn ? state.user.name : "Guest workspace";
    document.getElementById("sidebar-role").textContent = signedIn ? roleName + " account" : "Sign in to post or volunteer";
    document.getElementById("posting-as").textContent = signedIn ? state.user.name : "sign in required";
    document.getElementById("reports-nav").hidden = !currentRole("NGO");
    var canDonate = currentRole("DONOR");
    document.getElementById("donate-nav").hidden = !canDonate;
    document.querySelectorAll(".donor-only-action").forEach(function (el) { el.hidden = !canDonate; });
  }
  function goTo(view) {
    var names = { overview: "Overview", donate: "Donate food", volunteer: "Find food", tracking: "Track deliveries", notifications: "Notifications", reports: "NGO reports" };
    if (view === "donate" && !currentRole("DONOR")) { showToast("Sign in with a donor account to post food.", true); return; }
    if (view === "reports" && !currentRole("NGO")) { showToast("Sign in with an NGO account to view reports.", true); openAuth("login"); return; }
    document.querySelectorAll(".view-panel").forEach(function (p) { p.classList.toggle("active", p.dataset.panel === view); });
    document.querySelectorAll(".nav-link").forEach(function (b) { b.classList.toggle("active", b.dataset.view === view); });
    document.getElementById("page-crumb").textContent = names[view] || "Overview";
    window.scrollTo({ top: 0, behavior: "smooth" });
    if (["volunteer", "tracking", "notifications", "reports"].includes(view)) refreshAll();
  }
  function statusBadge(status) { return '<span class="status-badge ' + escapeHtml(String(status || "").toLowerCase()) + '">' + escapeHtml(labels[status] || status) + "</span>"; }
  function iconFor(id) { return foodIcons[Math.abs(Number(id) || 0) % foodIcons.length]; }
  function displayTime(value) {
    if (!value) return "Not specified";
    var date = new Date(value); if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
  }
  function toDateInput(value) { return value ? String(value).slice(0, 16) : ""; }
  function renderStats(stats) {
    document.getElementById("stat-available").textContent = stats.available;
    document.getElementById("stat-meals").textContent = Number(stats.mealsAvailable).toLocaleString();
    document.getElementById("stat-progress").textContent = Number(stats.accepted) + Number(stats.collected);
    document.getElementById("stat-delivered").textContent = stats.delivered;
    document.getElementById("nav-available").textContent = stats.available;
    document.getElementById("donate-total").textContent = Number(stats.totalDonations).toLocaleString();
  }
  function renderRecent() {
    var list = document.getElementById("recent-list"), latest = state.donations.slice(0, 4);
    if (!latest.length) { list.innerHTML = '<div class="empty-state">No donations yet. Be the first to share food with your community.</div>'; return; }
    list.innerHTML = latest.map(function (d) { return '<div class="recent-row"><div class="food-avatar">' + iconFor(d.id) + '</div><div><div class="recent-name">' +
      escapeHtml(d.foodType) + '</div><div class="recent-meta">' + escapeHtml(d.quantity) + ' meals · ' + escapeHtml(d.location) + '</div></div>' + statusBadge(d.status) + '</div>'; }).join("");
  }
  function renderVolunteer() {
    var list = document.getElementById("volunteer-list"), search = document.getElementById("donation-search").value.trim().toLowerCase();
    var available = state.donations.filter(function (d) { return d.status === "AVAILABLE" &&
      (String(d.foodType).toLowerCase().includes(search) || String(d.location).toLowerCase().includes(search)); });
    document.getElementById("volunteer-count").textContent = state.donations.filter(function (d) { return d.status === "AVAILABLE"; }).length;
    if (!available.length) { list.innerHTML = '<div class="surface empty-state">' + (search ? "No available donations match that search." : "There are no available donations right now. Check back soon.") + "</div>"; return; }
    list.innerHTML = available.map(function (d) { return '<article class="donation-card"><div class="donation-card-top"><span class="food-emoji">' + iconFor(d.id) + '</span>' + statusBadge(d.status) +
      '</div><h3>' + escapeHtml(d.foodType) + '</h3><div class="donation-quantity">' + escapeHtml(d.quantity) + ' meals to share</div><div class="donation-details"><div class="detail-line"><b>⌖</b><span>' +
      escapeHtml(d.location) + '</span></div><div class="detail-line"><b>◷</b><span>Pickup: ' + escapeHtml(displayTime(d.pickupTime)) + '</span></div><div class="detail-line"><b>✳</b><span>Best by: ' +
      escapeHtml(displayTime(d.bestBy)) + '</span></div></div>' + (currentRole("VOLUNTEER") ? '<button class="button button-primary accept-button" data-id="' + Number(d.id) + '">Accept pickup <span>→</span></button>' : '<button class="button button-primary require-volunteer">Sign in as a volunteer to accept</button>') + '</article>'; }).join("");
  }
  function actionFor(d) {
    if (d.mine && currentRole("DONOR") && d.status === "AVAILABLE") return '<button class="table-action edit-action" data-id="' + Number(d.id) + '">Edit details</button>';
    if (!currentRole("VOLUNTEER")) return '<span class="table-location">' + (d.status === "DELIVERED" ? "Complete" : "—") + '</span>';
    if (d.status === "AVAILABLE") return '<button class="table-action" data-action="accept" data-id="' + Number(d.id) + '">Accept pickup</button>';
    if (d.status === "ACCEPTED") return '<button class="table-action" data-action="collect" data-id="' + Number(d.id) + '">Mark collected</button>';
    if (d.status === "COLLECTED") return '<button class="table-action" data-action="deliver" data-id="' + Number(d.id) + '">Mark delivered</button>';
    return '<span class="table-location">Complete</span>';
  }
  function renderTracking() {
    var body = document.getElementById("tracking-body");
    if (!state.donations.length) { body.innerHTML = '<tr><td colspan="5" class="loading-cell">No donation activity yet. Post the first donation to start the journey.</td></tr>'; return; }
    body.innerHTML = state.donations.map(function (d) { return '<tr><td>' + escapeHtml(d.foodType) + '<div class="table-location">' + escapeHtml(d.location) + '</div></td><td>' + escapeHtml(d.quantity) + '</td><td>' +
      escapeHtml(displayTime(d.pickupTime)) + '</td><td>' + statusBadge(d.status) + '</td><td>' + actionFor(d) + '</td></tr>'; }).join("");
  }
  async function loadNotifications() {
    var box = document.getElementById("notification-list");
    if (!state.user) { state.notifications = []; document.getElementById("nav-notifications").textContent = "0"; box.innerHTML = '<div class="empty-state">Sign in to see notifications about your donations and pickups.</div>'; return; }
    try {
      state.notifications = await request("/api/notifications");
      var unread = state.notifications.filter(function (n) { return !n.read; }).length; document.getElementById("nav-notifications").textContent = unread;
      if (!state.notifications.length) { box.innerHTML = '<div class="empty-state">You are all caught up. New pickup and delivery updates will appear here.</div>'; return; }
      box.innerHTML = state.notifications.map(function (n) { return '<article class="notification-row ' + (n.read ? "read" : "unread") + '"><div class="notification-mark">' + (n.read ? "✓" : "✦") + '</div><div class="notification-copy"><strong>' + escapeHtml(n.message) + '</strong><span>' + escapeHtml(displayTime(n.createdAt)) + '</span></div>' + (n.read ? '' : '<button class="table-action notification-read" data-id="' + Number(n.id) + '">Mark read</button>') + '</article>'; }).join("");
    } catch (e) { box.innerHTML = '<div class="empty-state">' + escapeHtml(e.message) + '</div>'; }
  }
  async function loadReport() {
    if (!currentRole("NGO")) return;
    try { var report = await request("/api/reports/ngo");
      document.getElementById("report-assigned").textContent = report.assigned;
      document.getElementById("report-delivered").textContent = report.delivered;
      document.getElementById("report-meals").textContent = Number(report.mealsDelivered).toLocaleString();
    } catch (e) { showToast(e.message, true); }
  }
  async function refreshAll() {
    try {
      var results = await Promise.all([request("/api/donations?status=ALL"), request("/api/stats"), request("/api/auth/me")]);
      state.donations = results[0]; state.user = results[2]; syncAccountUI(); renderStats(results[1]); renderRecent(); renderVolunteer(); renderTracking(); setConnection(true);
      await Promise.all([loadNotifications(), loadReport()]);
    } catch (error) {
      setConnection(false); document.getElementById("recent-list").innerHTML = '<div class="empty-state">Could not load donations. Start MySQL and check the project setup.</div>';
      document.getElementById("volunteer-list").innerHTML = '<div class="surface empty-state">Database connection needed to browse donations.</div>';
      document.getElementById("tracking-body").innerHTML = '<tr><td colspan="5" class="loading-cell">Database connection needed to load donation activity.</td></tr>';
      showToast(error.message, true);
    }
  }
  async function loadOrganizations() {
    try { var orgs = await request("/api/auth/organizations"), select = document.getElementById("organization-select");
      select.innerHTML = '<option value="">Choose an organization</option>' + orgs.map(function (o) { return '<option value="' + Number(o.id) + '">' + escapeHtml(o.name) + '</option>'; }).join("");
    } catch (e) { showToast(e.message, true); }
  }
  function showAuthMode(mode) {
    var login = mode === "login"; document.getElementById("login-form").hidden = !login; document.getElementById("register-form").hidden = login;
    document.getElementById("auth-title").textContent = login ? "Welcome back" : "Join NourishNet";
    document.getElementById("auth-subtitle").textContent = login ? "Sign in to continue to your workspace." : "Create a donor, volunteer or NGO account.";
  }
  function openAuth(mode) { showAuthMode(mode || "login"); document.getElementById("auth-dialog").showModal(); }
  function syncRoleFields() {
    var role = document.getElementById("account-role").value;
    document.querySelectorAll(".org-only").forEach(function (el) { el.hidden = role !== "NGO"; el.querySelector("input").required = role === "NGO"; });
    document.querySelectorAll(".volunteer-only").forEach(function (el) { el.hidden = role !== "VOLUNTEER"; el.querySelector("select").required = role === "VOLUNTEER"; });
  }
  document.querySelectorAll(".nav-link").forEach(function (button) { button.addEventListener("click", function () { goTo(button.dataset.view); }); });
  document.querySelectorAll("[data-go]").forEach(function (button) { button.addEventListener("click", function () { goTo(button.dataset.go); }); });
  document.getElementById("donation-search").addEventListener("input", renderVolunteer);
  document.getElementById("refresh-button").addEventListener("click", refreshAll);
  donationFields.forEach(function (field) { field.input.addEventListener("input", function () { validateField(field); }); field.input.addEventListener("blur", function () { validateField(field); }); });
  document.getElementById("login-open").addEventListener("click", function () { openAuth("login"); });
  document.getElementById("register-open").addEventListener("click", function () { openAuth("register"); });
  document.querySelectorAll("[data-auth-mode]").forEach(function (button) { button.addEventListener("click", function () { showAuthMode(button.dataset.authMode); }); });
  document.getElementById("account-role").addEventListener("change", syncRoleFields);
  document.getElementById("logout-button").addEventListener("click", async function () { try { await request("/api/auth/logout", { method: "POST" }); state.user = null; syncAccountUI(); showToast("You have signed out."); await refreshAll(); } catch (e) { showToast(e.message, true); } });
  document.getElementById("login-form").addEventListener("submit", async function (event) { event.preventDefault();
    try { state.user = await request("/api/auth/login", formOptions(event.currentTarget, "POST")); document.getElementById("auth-dialog").close(); syncAccountUI(); showToast("Welcome back, " + state.user.name + "."); await refreshAll(); }
    catch (e) { showToast(e.message, true); }
  });
  document.getElementById("register-form").addEventListener("submit", async function (event) { event.preventDefault();
    try { state.user = await request("/api/auth/register", formOptions(event.currentTarget, "POST")); document.getElementById("auth-dialog").close(); syncAccountUI(); showToast("Your account is ready."); await refreshAll(); }
    catch (e) { showToast(e.message, true); }
  });
  document.getElementById("donation-form").addEventListener("submit", async function (event) {
    event.preventDefault(); if (!currentRole("DONOR")) { showToast("Sign in with a donor account to post food.", true); openAuth("login"); return; }
    if (!validateDonationForm()) { showToast("Please correct the highlighted fields.", true); return; }
    var form = event.currentTarget, button = form.querySelector('button[type="submit"]'); button.disabled = true; button.textContent = "Posting…";
    try { await request("/api/donations", formOptions(form, "POST")); form.reset(); showToast("Donation posted. Thank you for sharing!"); await refreshAll(); }
    catch (e) { showToast(e.message, true); } finally { button.disabled = false; button.innerHTML = 'Post donation <span>→</span>'; }
  });
  document.getElementById("edit-form").addEventListener("submit", async function (event) { event.preventDefault(); var form = event.currentTarget, id = form.elements.id.value;
    try { await request("/api/donations/" + encodeURIComponent(id), formOptions(form, "PUT")); document.getElementById("edit-dialog").close(); showToast("Donation details updated."); await refreshAll(); }
    catch (e) { showToast(e.message, true); }
  });
  document.addEventListener("click", async function (event) {
    var edit = event.target.closest(".edit-action");
    if (edit) { var d = state.donations.find(function (item) { return String(item.id) === edit.dataset.id; }); if (!d) return;
      var form = document.getElementById("edit-form"); form.elements.id.value = d.id; form.elements.foodType.value = d.foodType; form.elements.quantity.value = d.quantity;
      form.elements.location.value = d.location; form.elements.pickupTime.value = toDateInput(d.pickupTime); form.elements.bestBy.value = toDateInput(d.bestBy); document.getElementById("edit-dialog").showModal(); return;
    }
    var markRead = event.target.closest(".notification-read");
    if (markRead) { try { await request("/api/notifications/" + encodeURIComponent(markRead.dataset.id) + "/read", { method: "POST" }); await loadNotifications(); } catch (e) { showToast(e.message, true); } return; }
    var button = event.target.closest(".accept-button,.table-action[data-action],.require-volunteer"); if (!button) return;
    if (button.classList.contains("require-volunteer")) { openAuth("login"); return; }
    if (!currentRole("VOLUNTEER")) { showToast("Sign in with a volunteer account to manage pickups.", true); openAuth("login"); return; }
    var action = button.classList.contains("accept-button") ? "accept" : button.dataset.action, id = button.dataset.id; button.disabled = true;
    try { await request("/api/donations/" + encodeURIComponent(id) + "/" + action, { method: "POST" });
      showToast(action === "accept" ? "Pickup accepted. It is now in your queue." : action === "collect" ? "Pickup marked collected." : "Delivery recorded. Thank you!"); await refreshAll();
    } catch (e) { showToast(e.message, true); button.disabled = false; }
  });
  document.getElementById("account-role").dispatchEvent(new Event("change"));
  loadOrganizations();
  refreshAll();
}());

