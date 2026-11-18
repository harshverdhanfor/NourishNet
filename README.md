# NourishNet

NourishNet is a food-rescue project built with Java, JDBC, MySQL, HTML, CSS and JavaScript. The browser dashboard uses a small Java HTTP server, so the web page talks to the same Java data-access classes and MySQL database as the original Swing app.

## What it can do

- Show community totals for available, accepted, collected and delivered donations.
- Post a food donation with quantity, pickup location, pickup time and optional best-before details.
- Validate every donation field and show a clear message for invalid input.
- Search available donations and accept one as a volunteer.
- Track each donation through accepted, collected and delivered.
- Record completed deliveries in the Deliveries table.
- Register and sign in as a donor, volunteer or NGO with role-based access.
- Edit your available donations, including pickup and best-before date/time.
- Receive notifications when pickups and deliveries change.
- Review NGO pickup, delivery and meal impact reports.
- Run the original Java Swing desktop interface as well.

## Setup

You need Java 11 or newer, MySQL, and the MySQL Connector/J driver.

1. Start MySQL and run schema.sql once in MySQL Workbench or the MySQL command line. The script creates the nourishnet database and inserts example records.
   If this database already exists, run upgrade_final.sql from the MySQL command line. It adds account password hashes, structured date/time fields and notifications; it is safe to run again.
2. Place the Connector/J jar in the NourishNet project folder and name it mysql-connector-j.jar.
3. Open PowerShell in the NourishNet project folder and set the database credentials. Replace the example password with your MySQL root password:

        $env:NOURISHNET_DB_USER = 'root'
        $env:NOURISHNET_DB_PASS = 'your_mysql_password'

The database URL defaults to jdbc:mysql://localhost:3306/nourishnet. You can override it with NOURISHNET_DB_URL.

## Run the browser app

From the NourishNet project folder in PowerShell, compile the Java source and start the web server:

        if (!(Test-Path .\out)) { New-Item -ItemType Directory .\out | Out-Null }
        $javaFiles = Get-ChildItem .\src\nourishnet\*.java | ForEach-Object FullName
        javac --release 11 --add-modules jdk.httpserver -cp ".;mysql-connector-j.jar" -d out $javaFiles
        java --add-modules jdk.httpserver -cp "out;mysql-connector-j.jar" nourishnet.WebServer

Open http://127.0.0.1:8080 in a browser. Keep the command window open while using the app. To use another port, set NOURISHNET_PORT before starting the server.

## Run the Swing desktop app

Use the same database setup and compiled files, then run:

        java -cp "out;mysql-connector-j.jar" nourishnet.Main

## Accounts and roles

Create an account from the website. Donors can post and edit their own available donations. Volunteers join an organization and can accept, collect and deliver pickups. NGOs can view their organization impact report. New accounts use PBKDF2 password hashes and an in-memory sign-in session; the seeded demo records are not login accounts, so register a fresh email to sign in.

Pickup and best-before values are stored in the DATETIME columns. The original text columns remain so older project data can still be displayed. Notification records are created for donors when their donation changes status and for the NGO when food is delivered.

## Suggested project demo

1. Register an NGO account and note its organization name.
2. Sign out and register a volunteer account, selecting that NGO.
3. Register a donor account, then post a donation with pickup and best-before date/time.
4. Open Find food as the volunteer and accept the donation; Track deliveries lets that volunteer mark it collected and delivered.
5. Sign back in as the donor to show the status updates and notifications. Post a second available donation and edit its pickup location/time from Track deliveries.
6. Sign in as the NGO to show its notification and impact report.
7. Explain that WebServer authenticates each browser request, checks the account role, calls the DAO, and sends a JSON response.

## OOP structure

- `Donation`, `UserAccount`, `UserNotification`, `DashboardStats` and `OrganizationReport` represent the app's data.
- `DonationValidator` applies shared input rules and throws clear `IllegalArgumentException` messages for invalid donation details.
- `AccountDAO`, `DonationDAO` and `NotificationDAO` keep database work in focused classes and use JDBC prepared statements.
- `PasswordUtil` stores passwords as salted PBKDF2 hashes. `DBConnection` centralizes the JDBC URL and environment-based credentials.
- `WebServer` receives HTTP requests from the browser, checks the signed-in user and role, calls the relevant DAO, then responds with JSON. Sessions are held in memory while the server runs.
- Donor and volunteer actions are checked on the server. Donors can edit only their own available donations; volunteers can update only pickups assigned to their volunteer account.
- Donation accept and delivery operations use database transactions. SQL failures are caught and returned as a database error; invalid fields return a clear client error.
- The Swing app remains available through `Main`, `DonorPanel` and `NGOPanel`.

The browser validates donation values for immediate feedback, and Java validates them again before database writes. Pickup and best-before date/time values are stored in DATETIME columns. The upgrade script preserves older donation rows and their original text values.

