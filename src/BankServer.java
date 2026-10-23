import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class BankServer {
    private static final String STATIC_DIR = "public";

    public static void main(String[] args) throws IOException {
        int[] tryPorts = {8080, 8081, 3000, 8000};
        if (args.length > 0) {
            try { 
                int customPort = Integer.parseInt(args[0]); 
                tryPorts = new int[]{customPort, 8080, 8081, 3000};
            } catch (NumberFormatException ignored) {}
        }

        HttpServer server = null;
        int activePort = 8080;

        for (int p : tryPorts) {
            try {
                server = HttpServer.create(new InetSocketAddress(p), 0);
                activePort = p;
                break;
            } catch (java.net.BindException ex) {
                System.out.printf("Port %d is occupied, trying next port...\n", p);
            }
        }

        if (server == null) {
            System.err.println("Error: Could not bind to any test port (8080, 8081, 3000, 8000).");
            return;
        }

        server.createContext("/api/classes", new ClassesHandler());
        server.createContext("/api/create", new CreateHandler());
        server.createContext("/api/deposit", new DepositHandler());
        server.createContext("/api/withdraw", new WithdrawHandler());
        server.createContext("/api/transfer", new TransferHandler());
        server.createContext("/api/month-end", new MonthEndHandler());
        server.createContext("/api/scoreiq", new ScoreIQHandler());
        server.createContext("/api/scoreiq/statement", new StatementReviewHandler());

        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null); // Default executor
        System.out.println("==================================================================");
        System.out.println("  Tally Java Server Started successfully!");
        System.out.println("  Open your browser and navigate to: http://localhost:" + activePort + "/");
        System.out.println("  Serving Web UI from '" + STATIC_DIR + "/' directory.");
        System.out.println("  Press Ctrl + C in terminal to stop server.");
        System.out.println("==================================================================");
        server.start();
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[2048];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String json = String.format("{\"error\":\"%s\"}", escape(message));
        sendJsonResponse(exchange, statusCode, json);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    
    private static String extractJsonField(String json, String key) {
        if (json == null) return "";
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) return m.group(1);

        String numPattern = "\"" + key + "\"\\s*:\\s*([0-9.-]+)";
        java.util.regex.Matcher mNum = java.util.regex.Pattern.compile(numPattern).matcher(json);
        if (mNum.find()) return mNum.group(1);

        return "";
    }

    private static double extractJsonDouble(String json, String key, double defaultVal) {
        String val = extractJsonField(json, key);
        if (val.isEmpty()) return defaultVal;
        try { return Double.parseDouble(val); } catch (Exception e) { return defaultVal; }
    }

    private static int extractJsonInt(String json, String key, int defaultVal) {
        String val = extractJsonField(json, key);
        if (val.isEmpty()) return defaultVal;
        try { return Integer.parseInt(val); } catch (Exception e) { return defaultVal; }
    }

    private static boolean extractJsonBool(String json, String key, boolean defaultVal) {
        if (json == null) return defaultVal;
        String pattern = "\"" + key + "\"\\s*:\\s*(true|false)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) return Boolean.parseBoolean(m.group(1));
        return defaultVal;
    }

    private static String extractJsonMultilineField(String json, String key) {
        if (json == null) return "";
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return "";
        int colon = json.indexOf(":", idx);
        if (colon == -1) return "";
        int startQuote = json.indexOf("\"", colon);
        if (startQuote == -1) return "";
        int i = startQuote + 1;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == 'n') { sb.append('\n'); i += 2; continue; }
                else if (next == 'r') { sb.append('\r'); i += 2; continue; }
                else if (next == 't') { sb.append('\t'); i += 2; continue; }
                else if (next == '"') { sb.append('"'); i += 2; continue; }
                else if (next == '\\') { sb.append('\\'); i += 2; continue; }
            } else if (c == '"') {
                break;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static Account rehydrateAccount(String accountJson) {
        if (accountJson == null || accountJson.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing account payload.");
        }
        String type = extractJsonField(accountJson, "type").toLowerCase();
        String holder = extractJsonField(accountJson, "holder");
        String accNum = extractJsonField(accountJson, "account_number");
        String balStr = extractJsonField(accountJson, "balance");
        double balance = 0.0;
        try { balance = Double.parseDouble(balStr); } catch (Exception ignored) {}

        List<Transaction> txList = new ArrayList<>();

        int txStart = accountJson.indexOf("\"transactions\":[");
        if (txStart != -1) {
            int txEnd = accountJson.indexOf("]", txStart);
            if (txEnd != -1) {
                String sub = accountJson.substring(txStart + 16, txEnd);
                String[] entries = sub.split("\\},\\{");
                for (String e : entries) {
                    if (e.trim().isEmpty()) continue;
                    String tType = extractJsonField(e, "type");
                    String tAmt = extractJsonField(e, "amount");
                    String tBal = extractJsonField(e, "balance_after");
                    String tDate = extractJsonField(e, "date");
                    String tDesc = extractJsonField(e, "description");
                    double amt = 0.0;
                    double bAfter = 0.0;
                    try { amt = Double.parseDouble(tAmt); } catch (Exception ignored) {}
                    try { bAfter = Double.parseDouble(tBal); } catch (Exception ignored) {}
                    txList.add(new Transaction(tType, amt, bAfter, tDate, tDesc));
                }
            }
        }

        if (type.contains("senior")) {
            return new SeniorCitizenSavingsAccount(holder, balance, accNum, txList);
        } else if (type.contains("saving")) {
            return new SavingsAccount(holder, balance, accNum, txList);
        } else if (type.contains("current")) {
            return new CurrentAccount(holder, balance, accNum, txList);
        } else {
            return new Account(holder, balance, accNum, txList);
        }
    }

    static class ClassesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = "{\"classes\":[" +
                "{\"name\":\"BankEntity\",\"parent\":\"Object\",\"doc\":\"[ABSTRACT CLASS] Core contract defining getEntityId(), describe(), and toJson() for all banking entities.\",\"methods\":[\"getEntityId\",\"describe\",\"toJson\"],\"overrides\":[]}," +
                "{\"name\":\"Transaction\",\"parent\":\"BankEntity\",\"doc\":\"Immutable financial ledger entry with date, type, amount, balance, and description.\",\"methods\":[\"getType\",\"getAmount\",\"getBalanceAfter\",\"toJson\"],\"overrides\":[\"getEntityId\",\"describe\",\"toJson\"]}," +
                "{\"name\":\"Account\",\"parent\":\"BankEntity\",\"doc\":\"[BASE CLASS] Standard deposit, basic zero-floor withdrawal, recursion, statement summary, and file I/O.\",\"methods\":[\"deposit\",\"withdraw\",\"transfer\",\"monthEnd\",\"describe\",\"exportStatementToFile\",\"calculateCompoundInterestRecursively\"],\"overrides\":[\"getEntityId\",\"describe\",\"toJson\"]}," +
                "{\"name\":\"SavingsAccount\",\"parent\":\"Account\",\"doc\":\"[DERIVED CLASS] Enforces ₹500 minimum balance, max 5 withdrawals/month, and credits 3.5% p.a. monthly interest.\",\"methods\":[\"withdraw\",\"monthEnd\",\"describe\"],\"overrides\":[\"withdraw\",\"monthEnd\",\"describe\"]}," +
                "{\"name\":\"SeniorCitizenSavingsAccount\",\"parent\":\"SavingsAccount\",\"doc\":\"[MULTILEVEL HIERARCHY] BankEntity -> Account -> SavingsAccount -> SeniorCitizen. Offers 4.25% p.a. senior citizen interest.\",\"methods\":[\"monthEnd\",\"describe\"],\"overrides\":[\"monthEnd\",\"describe\"]}," +
                "{\"name\":\"CurrentAccount\",\"parent\":\"Account\",\"doc\":\"[DERIVED CLASS] Allows ₹10,000 overdraft protection and charges flat ₹100 monthly maintenance fee.\",\"methods\":[\"withdraw\",\"monthEnd\",\"describe\"],\"overrides\":[\"withdraw\",\"monthEnd\",\"describe\"]}," +
                "{\"name\":\"ScoreIQ\",\"parent\":\"Object\",\"doc\":\"[RULE ENGINE] Transparent scoring engine evaluating cashflow cushion, stability, payment discipline, and UPI velocity.\",\"methods\":[\"calculate\"],\"overrides\":[]}" +
                "]}";
            sendJsonResponse(exchange, 200, json);
        }
    }

    static class CreateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            String body = readRequestBody(exchange);
            String type = extractJsonField(body, "type").toLowerCase();
            String holder = extractJsonField(body, "holder");
            String openStr = extractJsonField(body, "opening_balance");
            double opening = 0.0;
            try { opening = Double.parseDouble(openStr); } catch (Exception ignored) {}

            if (holder.isEmpty()) {
                sendError(exchange, 400, "Account holder name is required.");
                return;
            }

            Account account;
            String methodUsed;
            if (type.contains("senior")) {
                if (opening < SavingsAccount.MINIMUM_BALANCE) {
                    sendError(exchange, 400, String.format("Senior Citizen accounts require an opening deposit of at least ₹%,.2f.", SavingsAccount.MINIMUM_BALANCE));
                    return;
                }
                account = new SeniorCitizenSavingsAccount(holder, 0.0);
                methodUsed = "SeniorCitizenSavingsAccount.__init__ (Multilevel Derived)";
            } else if (type.contains("saving")) {
                if (opening < SavingsAccount.MINIMUM_BALANCE) {
                    sendError(exchange, 400, String.format("Savings accounts require an opening deposit of at least ₹%,.2f.", SavingsAccount.MINIMUM_BALANCE));
                    return;
                }
                account = new SavingsAccount(holder, 0.0);
                methodUsed = "SavingsAccount.__init__";
            } else if (type.contains("current")) {
                account = new CurrentAccount(holder, 0.0);
                methodUsed = "CurrentAccount.__init__";
            } else {
                account = new Account(holder, 0.0);
                methodUsed = "Account.__init__";
            }

            if (opening > 0) {
                try {
                    account.deposit(opening, "Initial Opening Deposit");
                } catch (InvalidAmountException ignored) {}
            }

            String resp = String.format(
                "{\"account\":%s,\"method_used\":\"%s\",\"description\":\"%s\",\"message\":\"Account %s created successfully for %s.\"}",
                account.toJson(), methodUsed, escape(account.describe()), account.getAccountNumber(), escape(account.getHolder())
            );
            sendJsonResponse(exchange, 200, resp);
        }
    }

    static class DepositHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            String body = readRequestBody(exchange);
            try {
                Account account = rehydrateAccount(body);
                String amtStr = extractJsonField(body, "amount");
                String desc = extractJsonField(body, "description");
                double amount = Double.parseDouble(amtStr);

                Transaction tx = account.deposit(amount, desc.isEmpty() ? "Cash Deposit" : desc);
                String resp = String.format(
                    "{\"account\":%s,\"transaction\":%s,\"method_used\":\"%s.deposit (inherited from Account)\",\"message\":\"Deposited ₹%,.2f successfully. New balance: ₹%,.2f.\"}",
                    account.toJson(), tx.toJson(), account.getClass().getSimpleName(), amount, account.getBalance()
                );
                sendJsonResponse(exchange, 200, resp);
            } catch (BankException e) {
                sendError(exchange, 400, e.getMessage());
            } catch (Exception e) {
                sendError(exchange, 400, "Invalid deposit request: " + e.getMessage());
            }
        }
    }

    static class WithdrawHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            String body = readRequestBody(exchange);
            try {
                Account account = rehydrateAccount(body);
                String amtStr = extractJsonField(body, "amount");
                String desc = extractJsonField(body, "description");
                double amount = Double.parseDouble(amtStr);

                Transaction tx = account.withdraw(amount, desc.isEmpty() ? "Cash Withdrawal" : desc);
                String resp = String.format(
                    "{\"account\":%s,\"transaction\":%s,\"method_used\":\"%s.withdraw (overridden from Account)\",\"message\":\"Withdrew ₹%,.2f successfully. New balance: ₹%,.2f.\"}",
                    account.toJson(), tx.toJson(), account.getClass().getSimpleName(), amount, account.getBalance()
                );
                sendJsonResponse(exchange, 200, resp);
            } catch (BankException e) {
                sendError(exchange, 400, e.getMessage());
            } catch (Exception e) {
                sendError(exchange, 400, "Invalid withdrawal request: " + e.getMessage());
            }
        }
    }

    static class TransferHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            String body = readRequestBody(exchange);
            try {
                int srcStart = body.indexOf("\"source_account\":");
                int tgtStart = body.indexOf("\"target_account\":");
                if (srcStart == -1 || tgtStart == -1) {
                    sendError(exchange, 400, "Missing source or target account in payload.");
                    return;
                }
                String srcJson = body.substring(srcStart + 17, tgtStart).trim();
                if (srcJson.endsWith(",")) srcJson = srcJson.substring(0, srcJson.length() - 1);

                int amtIdx = body.indexOf("\"amount\":", tgtStart);
                String tgtJson = body.substring(tgtStart + 17, amtIdx != -1 ? amtIdx : body.length()).trim();
                if (tgtJson.endsWith(",")) tgtJson = tgtJson.substring(0, tgtJson.length() - 1);

                Account src = rehydrateAccount(srcJson);
                Account dest = rehydrateAccount(tgtJson);
                double amount = Double.parseDouble(extractJsonField(body, "amount"));

                Transaction[] txs = src.transfer(dest, amount);
                String resp = String.format(
                    "{\"source_account\":%s,\"target_account\":%s,\"source_transaction\":%s,\"target_transaction\":%s,\"method_used\":\"%s.transfer -> %s.withdraw() & %s.deposit()\",\"message\":\"Transferred ₹%,.2f from %s to %s.\"}",
                    src.toJson(), dest.toJson(), txs[0].toJson(), txs[1].toJson(),
                    src.getClass().getSimpleName(), src.getClass().getSimpleName(), dest.getClass().getSimpleName(),
                    amount, src.getHolder(), dest.getHolder()
                );
                sendJsonResponse(exchange, 200, resp);
            } catch (BankException e) {
                sendError(exchange, 400, e.getMessage());
            } catch (Exception e) {
                sendError(exchange, 400, "Transfer failed: " + e.getMessage());
            }
        }
    }

    static class MonthEndHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            String body = readRequestBody(exchange);
            try {
                Account account = rehydrateAccount(body);
                Transaction tx = account.monthEnd();
                String message;
                if (tx == null) {
                    message = String.format("Month-end processed for %s. No balance adjustments required.", account.getHolder());
                } else if ("INTEREST".equals(tx.getType())) {
                    message = String.format("Credited monthly savings interest of ₹%,.2f @ 3.5%% p.a. New balance: ₹%,.2f.", tx.getAmount(), account.getBalance());
                } else {
                    message = String.format("Deducted monthly current account fee of ₹%,.2f. New balance: ₹%,.2f.", tx.getAmount(), account.getBalance());
                }

                String txJson = (tx != null) ? tx.toJson() : "null";
                String resp = String.format(
                    "{\"account\":%s,\"transaction\":%s,\"method_used\":\"%s.month_end (overridden from Account)\",\"message\":\"%s\"}",
                    account.toJson(), txJson, account.getClass().getSimpleName(), escape(message)
                );
                sendJsonResponse(exchange, 200, resp);
            } catch (BankException e) {
                sendError(exchange, 400, e.getMessage());
            } catch (Exception e) {
                sendError(exchange, 400, "Month-end error: " + e.getMessage());
            }
        }
    }

    static class ScoreIQHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            String body = readRequestBody(exchange);
            try {
                ScoreIQ.ScoreResult res;
                if (body.contains("\"user_data\"")) {
                    String userJson = body;
                    int uStart = body.indexOf("\"user_data\":");
                    if (uStart != -1) {
                        int uEnd = body.indexOf("}", uStart);
                        if (uEnd != -1) userJson = body.substring(uStart, uEnd + 1);
                    }
                    String bankJson = body;
                    int bStart = body.indexOf("\"bank_data\":");
                    if (bStart != -1) {
                        int bEnd = body.indexOf("}", bStart);
                        if (bEnd != -1) bankJson = body.substring(bStart, bEnd + 1);
                    }

                    ScoreIQ.UserInputData user = new ScoreIQ.UserInputData(
                        extractJsonField(userJson, "name"),
                        extractJsonInt(userJson, "age", 29),
                        extractJsonDouble(userJson, "monthly_income", 35000.0),
                        extractJsonDouble(userJson, "monthly_expenses", 22000.0),
                        extractJsonField(userJson, "employment_type"),
                        extractJsonDouble(userJson, "years_employed", 2.0),
                        extractJsonInt(userJson, "missed_payments_last_12m", 0),
                        extractJsonInt(userJson, "existing_loans_count", 0),
                        extractJsonInt(userJson, "monthly_upi_transactions", 35)
                    );

                    ScoreIQ.BankData bank = new ScoreIQ.BankData(
                        extractJsonDouble(bankJson, "avg_monthly_balance", 5000.0),
                        extractJsonDouble(bankJson, "avg_monthly_credits", user.monthlyIncome),
                        extractJsonDouble(bankJson, "avg_monthly_debits", user.monthlyExpenses),
                        extractJsonDouble(bankJson, "emi_outflow", 0.0),
                        extractJsonInt(bankJson, "bounce_count_6m", 0),
                        extractJsonBool(bankJson, "salary_detected", true)
                    );

                    res = ScoreIQ.calculate(user, bank);
                } else if (body.contains("\"statement_text\"")) {
                    String stmtText = extractJsonMultilineField(body, "statement_text");
                    String appName = extractJsonField(body, "applicant_name");
                    String empType = extractJsonField(body, "employment_type");
                    ScoreIQ.StatementReview review = ScoreIQ.reviewStatement(stmtText, appName, empType);
                    sendJsonResponse(exchange, 200, review.toJson());
                    return;
                } else if (body.contains("\"account\"")) {
                    Account account = rehydrateAccount(body);
                    res = ScoreIQ.calculate(account);
                } else {
                    res = ScoreIQ.calculate(new ScoreIQ.UserInputData(), new ScoreIQ.BankData());
                }
                sendJsonResponse(exchange, 200, res.toJson());
            } catch (Exception e) {
                sendError(exchange, 400, "ScoreIQ calculation error: " + e.getMessage());
            }
        }
    }

    static class StatementReviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            String body = readRequestBody(exchange);
            try {
                String stmtText = extractJsonMultilineField(body, "statement_text");
                String appName = extractJsonField(body, "applicant_name");
                String empType = extractJsonField(body, "employment_type");
                ScoreIQ.StatementReview review = ScoreIQ.reviewStatement(stmtText, appName, empType);
                sendJsonResponse(exchange, 200, review.toJson());
            } catch (Exception e) {
                sendError(exchange, 400, "Statement review error: " + e.getMessage());
            }
        }
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String pathStr = exchange.getRequestURI().getPath();
            if (pathStr.equals("/")) pathStr = "/index.html";

            Path filePath = Paths.get(STATIC_DIR, pathStr.substring(1));
            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                filePath = Paths.get(STATIC_DIR, "index.html");
            }

            if (!Files.exists(filePath)) {
                String notFound = "404 Not Found";
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) { os.write(notFound.getBytes()); }
                return;
            }

            String contentType = "text/html; charset=UTF-8";
            if (filePath.toString().endsWith(".css")) contentType = "text/css; charset=UTF-8";
            else if (filePath.toString().endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
            else if (filePath.toString().endsWith(".svg")) contentType = "image/svg+xml";

            byte[] bytes = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
