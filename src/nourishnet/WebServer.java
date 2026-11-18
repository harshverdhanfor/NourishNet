package nourishnet;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class WebServer {
    private static final Path WEB_ROOT = Paths.get(
            System.getProperty("nourishnet.web.dir", "web")).toAbsolutePath().normalize();
    private final DonationDAO donationDAO = new DonationDAO();
    private final AccountDAO accountDAO = new AccountDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final Map<String, UserAccount> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("NOURISHNET_PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        WebServer app = new WebServer();
        server.createContext("/", app::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("NourishNet is running at http://127.0.0.1:" + port);
        System.out.println("Keep this window open while using the website.");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) handleApi(exchange, path);
            else serveStatic(exchange, path);
        } catch (ApiException e) {
            sendJson(exchange, e.status, errorJson(e.getMessage()));
        } catch (SQLException e) {
            System.err.println("[NourishNet database error] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            sendJson(exchange, 503, errorJson("Database connection failed. Check the server terminal for the database error."));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, errorJson("Something went wrong while handling the request."));
        }
    }

    private void handleApi(HttpExchange exchange, String path) throws IOException, SQLException, GeneralSecurityException {
        String method = exchange.getRequestMethod();
        if (path.equals("/api/auth/organizations") && method.equals("GET")) {
            sendJson(exchange, 200, organizationsJson(accountDAO.getOrganizations()));
            return;
        }
        if (path.equals("/api/auth/register") && method.equals("POST")) {
            Map<String, String> form = formParameters(exchange);
            int orgId = parseOptionalInt(form.get("organizationId"), 0, "Choose an organization.");
            UserAccount user = accountDAO.register(required(form, "name", "Enter your name."),
                    required(form, "email", "Enter your email."), form.getOrDefault("phone", ""),
                    required(form, "password", "Enter a password."), required(form, "role", "Choose an account type."),
                    form.getOrDefault("organizationName", ""), form.getOrDefault("address", ""), orgId);
            createSession(exchange, user);
            sendJson(exchange, 201, userJson(user));
            return;
        }
        if (path.equals("/api/auth/login") && method.equals("POST")) {
            Map<String, String> form = formParameters(exchange);
            UserAccount user = accountDAO.authenticate(form.getOrDefault("email", ""), form.getOrDefault("password", ""));
            if (user == null) throw new ApiException(401, "Email or password is incorrect.");
            createSession(exchange, user);
            sendJson(exchange, 200, userJson(user));
            return;
        }
        if (path.equals("/api/auth/logout") && method.equals("POST")) {
            String token = sessionToken(exchange);
            if (token != null) sessions.remove(token);
            exchange.getResponseHeaders().add("Set-Cookie", "NOURISHNET_SESSION=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
            sendJson(exchange, 200, "{\"message\":\"Signed out.\"}");
            return;
        }
        if (path.equals("/api/auth/me") && method.equals("GET")) {
            UserAccount user = currentUser(exchange);
            sendJson(exchange, 200, user == null ? "null" : userJson(user));
            return;
        }
        if (path.equals("/api/stats") && method.equals("GET")) {
            sendJson(exchange, 200, statsJson(donationDAO.getDashboardStats()));
            return;
        }
        if (path.equals("/api/donations") && method.equals("GET")) {
            String status = queryParameters(exchange).getOrDefault("status", "AVAILABLE");
            if (!status.equalsIgnoreCase("ALL") && !status.equalsIgnoreCase("AVAILABLE")
                    && !status.equalsIgnoreCase("ACCEPTED") && !status.equalsIgnoreCase("COLLECTED")
                    && !status.equalsIgnoreCase("DELIVERED")) throw new IllegalArgumentException("Choose a valid donation status.");
            UserAccount user = currentUser(exchange);
            sendJson(exchange, 200, donationsJson(donationDAO.getDonations(status), user));
            return;
        }
        if (path.equals("/api/donations") && method.equals("POST")) {
            UserAccount user = requireRole(exchange, "DONOR");
            Map<String, String> form = formParameters(exchange);
            DonationInput input = donationInput(form);
            if (!donationDAO.addDonation(user.getUserId(), input.foodType, input.quantity,
                    input.location, input.pickupTime, input.bestBy)) throw new ApiException(500, "The donation could not be saved.");
            sendJson(exchange, 201, "{\"message\":\"Donation posted successfully.\"}");
            return;
        }
        if (path.equals("/api/notifications") && method.equals("GET")) {
            UserAccount user = requireUser(exchange);
            sendJson(exchange, 200, notificationsJson(notificationDAO.listForUser(user.getUserId())));
            return;
        }
        if (path.equals("/api/reports/ngo") && method.equals("GET")) {
            UserAccount user = requireRole(exchange, "NGO");
            sendJson(exchange, 200, reportJson(donationDAO.getOrganizationReport(user.getOrgId())));
            return;
        }

        String[] parts = path.split("/");
        if (parts.length == 5 && parts[1].equals("api") && parts[2].equals("notifications") && parts[4].equals("read")) {
            UserAccount user = requireUser(exchange);
            int notificationId = parseId(parts[3], "Notification ID is invalid.");
            if (!method.equals("POST")) throw new ApiException(405, "Use POST to mark a notification as read.");
            notificationDAO.markRead(notificationId, user.getUserId());
            sendJson(exchange, 200, "{\"message\":\"Notification marked as read.\"}");
            return;
        }
        if (parts.length == 4 && parts[1].equals("api") && parts[2].equals("donations")) {
            UserAccount user = requireRole(exchange, "DONOR");
            int donationId = parseId(parts[3], "Donation ID is invalid.");
            if (!method.equals("PUT")) throw new ApiException(405, "Use PUT to edit a donation.");
            DonationInput input = donationInput(formParameters(exchange));
            if (!donationDAO.updateDonation(donationId, user.getUserId(), input.foodType, input.quantity,
                    input.location, input.pickupTime, input.bestBy)) {
                throw new ApiException(409, "Only your available donations can be edited. Refresh and try again.");
            }
            sendJson(exchange, 200, "{\"message\":\"Donation details updated.\"}");
            return;
        }
        if (parts.length == 5 && parts[1].equals("api") && parts[2].equals("donations")) {
            int donationId = parseId(parts[3], "Donation ID is invalid.");
            if (!method.equals("POST")) throw new ApiException(405, "Use POST for this action.");
            UserAccount user = requireRole(exchange, "VOLUNTEER");
            boolean updated;
            switch (parts[4]) {
                case "accept": updated = donationDAO.acceptDonation(donationId, user.getVolunteerId()); break;
                case "collect": updated = donationDAO.markCollected(donationId, user.getVolunteerId()); break;
                case "deliver": updated = donationDAO.markDelivered(donationId, user.getVolunteerId()); break;
                default: throw new ApiException(404, "Action not found.");
            }
            if (!updated) throw new ApiException(409, "This donation has changed or is not ready for that step. Refresh and try again.");
            Donation donation = donationDAO.getDonationById(donationId);
            if (donation != null) {
                String message = parts[4].equals("accept") ? "A volunteer accepted your " + donation.getFoodType() + " donation."
                        : parts[4].equals("collect") ? "Your " + donation.getFoodType() + " donation has been collected."
                        : "Your " + donation.getFoodType() + " donation was delivered.";
                notificationDAO.add(donation.getDonorId(), message);
                if (parts[4].equals("deliver")) notificationDAO.addToOrganization(user.getOrgId(),
                        donation.getFoodType() + " donation delivered: " + donation.getQuantity() + " meals.");
            }
            sendJson(exchange, 200, "{\"message\":\"Donation status updated.\"}");
            return;
        }
        sendJson(exchange, 404, errorJson("API endpoint not found."));
    }

    private UserAccount requireUser(HttpExchange exchange) {
        UserAccount user = currentUser(exchange);
        if (user == null) throw new ApiException(401, "Sign in or create an account to use this feature.");
        return user;
    }

    private UserAccount requireRole(HttpExchange exchange, String role) {
        UserAccount user = requireUser(exchange);
        if (!role.equals(user.getRole())) throw new ApiException(403, "This feature is available to " + role.toLowerCase() + " accounts.");
        return user;
    }

    private UserAccount currentUser(HttpExchange exchange) {
        String token = sessionToken(exchange);
        return token == null ? null : sessions.get(token);
    }

    private static String sessionToken(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        for (String cookie : cookieHeader.split(";")) {
            String[] pair = cookie.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals("NOURISHNET_SESSION")) return pair[1];
        }
        return null;
    }

    private void createSession(HttpExchange exchange, UserAccount user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, user);
        exchange.getResponseHeaders().add("Set-Cookie", "NOURISHNET_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Lax");
    }

    private static DonationInput donationInput(Map<String, String> form) {
        String foodType = required(form, "foodType", "Enter the food type.");
        String location = required(form, "location", "Enter a pickup location.");
        String pickupTime = required(form, "pickupTime", "Choose a pickup date and time.");
        String bestBy = form.getOrDefault("bestBy", "").trim();
        int quantity;
        try { quantity = Integer.parseInt(required(form, "quantity", "Enter a quantity.")); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Quantity must be a whole number."); }
        validateDateTime(pickupTime, "Choose a valid pickup date and time.");
        if (!bestBy.isEmpty()) {
            validateDateTime(bestBy, "Choose a valid best-before date and time.");
            if (LocalDateTime.parse(bestBy).isBefore(LocalDateTime.parse(pickupTime))) {
                throw new IllegalArgumentException("Best-before time cannot be earlier than the pickup time.");
            }
        }
        DonationValidator.validateDonation(foodType, quantity, location, pickupTime, bestBy);
        return new DonationInput(foodType, quantity, location, pickupTime, bestBy);
    }

    private static void validateDateTime(String value, String message) {
        try { LocalDateTime.parse(value); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException(message); }
    }

    private static int parseOptionalInt(String value, int fallback, String message) {
        if (value == null || value.isBlank()) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(message); }
    }

    private static int parseId(String value, String message) {
        try { int id = Integer.parseInt(value); if (id > 0) return id; }
        catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(message);
    }

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendText(exchange, 405, "Method not allowed.", "text/plain; charset=utf-8"); return;
        }
        String filename;
        switch (path) {
            case "/": case "/index.html": filename = "index.html"; break;
            case "/styles.css": filename = "styles.css"; break;
            case "/app.js": filename = "app.js"; break;
            default: sendText(exchange, 404, "Page not found.", "text/plain; charset=utf-8"); return;
        }
        Path file = WEB_ROOT.resolve(filename).normalize();
        if (!file.startsWith(WEB_ROOT) || !Files.isRegularFile(file)) {
            sendText(exchange, 404, "Website files were not found. Start the server from the NourishNet folder.", "text/plain; charset=utf-8"); return;
        }
        String type = filename.endsWith(".css") ? "text/css; charset=utf-8"
                : filename.endsWith(".js") ? "text/javascript; charset=utf-8" : "text/html; charset=utf-8";
        byte[] content = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, content.length);
        exchange.getResponseBody().write(content); exchange.close();
    }

    private static String donationsJson(List<Donation> donations, UserAccount user) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < donations.size(); i++) {
            Donation d = donations.get(i); if (i > 0) json.append(',');
            json.append("{\"id\":").append(d.getId()).append(",\"donorId\":").append(d.getDonorId())
                    .append(",\"mine\":").append(user != null && user.getUserId() == d.getDonorId())
                    .append(",\"foodType\":\"").append(jsonEscape(d.getFoodType())).append("\"")
                    .append(",\"quantity\":").append(d.getQuantity())
                    .append(",\"location\":\"").append(jsonEscape(d.getLocation())).append("\"")
                    .append(",\"pickupTime\":\"").append(jsonEscape(d.getPickupTime())).append("\"")
                    .append(",\"bestBy\":\"").append(jsonEscape(d.getBestBy())).append("\"")
                    .append(",\"status\":\"").append(jsonEscape(d.getStatus())).append("\"}");
        }
        return json.append(']').toString();
    }

    private static String organizationsJson(List<OrganizationOption> organizations) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < organizations.size(); i++) {
            OrganizationOption org = organizations.get(i); if (i > 0) json.append(',');
            json.append("{\"id\":").append(org.getId()).append(",\"name\":\"").append(jsonEscape(org.getName())).append("\"}");
        }
        return json.append(']').toString();
    }

    private static String notificationsJson(List<UserNotification> notifications) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < notifications.size(); i++) {
            UserNotification n = notifications.get(i); if (i > 0) json.append(',');
            json.append("{\"id\":").append(n.getId()).append(",\"message\":\"").append(jsonEscape(n.getMessage()))
                    .append("\",\"createdAt\":\"").append(jsonEscape(n.getCreatedAt())).append("\",\"read\":").append(n.isRead()).append('}');
        }
        return json.append(']').toString();
    }

    private static String statsJson(DashboardStats stats) {
        return "{\"totalDonations\":" + stats.getTotalDonations() + ",\"available\":" + stats.getAvailable()
                + ",\"accepted\":" + stats.getAccepted() + ",\"collected\":" + stats.getCollected()
                + ",\"delivered\":" + stats.getDelivered() + ",\"mealsAvailable\":" + stats.getMealsAvailable() + "}";
    }

    private static String reportJson(OrganizationReport report) {
        return "{\"assigned\":" + report.getAssigned() + ",\"delivered\":" + report.getDelivered()
                + ",\"mealsDelivered\":" + report.getMealsDelivered() + "}";
    }

    private static Map<String, String> queryParameters(HttpExchange exchange) { return decodeParameters(exchange.getRequestURI().getRawQuery()); }
    private static Map<String, String> formParameters(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        if (body.length > 65536) throw new IllegalArgumentException("Form is too large.");
        return decodeParameters(new String(body, StandardCharsets.UTF_8));
    }
    private static Map<String, String> decodeParameters(String encoded) {
        Map<String, String> values = new HashMap<>();
        if (encoded == null || encoded.isEmpty()) return values;
        for (String pair : encoded.split("&")) {
            String[] pieces = pair.split("=", 2);
            String key = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8);
            String value = pieces.length == 2 ? URLDecoder.decode(pieces[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }
    private static String required(Map<String, String> values, String key, String message) {
        String value = values.getOrDefault(key, "").trim(); if (value.isEmpty()) throw new IllegalArgumentException(message); return value;
    }
    private static String userJson(UserAccount user) {
        return "{\"userId\":" + user.getUserId() + ",\"name\":\"" + jsonEscape(user.getName()) + "\",\"email\":\""
                + jsonEscape(user.getEmail()) + "\",\"role\":\"" + jsonEscape(user.getRole()) + "\",\"volunteerId\":"
                + user.getVolunteerId() + ",\"orgId\":" + user.getOrgId() + "}";
    }
    private static String errorJson(String message) { return "{\"error\":\"" + jsonEscape(message) + "\"}"; }
    private static String jsonEscape(String value) {
        if (value == null) return "";
        StringBuilder escaped = new StringBuilder();
        for (char ch : value.toCharArray()) switch (ch) {
            case '"': escaped.append("\\\""); break; case '\\': escaped.append("\\\\"); break;
            case '\n': escaped.append("\\n"); break; case '\r': escaped.append("\\r"); break; case '\t': escaped.append("\\t"); break;
            default: if (ch < 0x20) escaped.append(String.format("\\u%04x", (int) ch)); else escaped.append(ch);
        }
        return escaped.toString();
    }
    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException { sendText(exchange, status, json, "application/json; charset=utf-8"); }
    private static void sendText(HttpExchange exchange, int status, String text, String contentType) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store"); exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body); exchange.close();
    }

    private static final class DonationInput {
        final String foodType, location, pickupTime, bestBy; final int quantity;
        DonationInput(String foodType, int quantity, String location, String pickupTime, String bestBy) {
            this.foodType = foodType; this.quantity = quantity; this.location = location; this.pickupTime = pickupTime; this.bestBy = bestBy;
        }
    }
    private static final class ApiException extends RuntimeException {
        final int status; ApiException(int status, String message) { super(message); this.status = status; }
    }
}

