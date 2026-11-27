package com.baseielts.platform.identity;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

/** Explicit commands for isolated synthetic governance fixtures. Never invoked on server startup. */
public final class DevelopmentAdminCommand {
    private DevelopmentAdminCommand() {}

    public static void main(String[] args) {
        try {
            if (args.length != 1 || !"true".equals(System.getenv("BASE_ADMIN_SYNTHETIC_TEST_ONLY"))) {
                throw new IllegalArgumentException();
            }
            String url = required("BASE_ADMIN_JDBC_URL");
            if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:5432/base_ielts_m1test_[0-9]+")) {
                throw new IllegalArgumentException();
            }
            String operation = args[0].toUpperCase(Locale.ROOT);
            if (operation.equals("RECOVERY_OUTCOME") || operation.equals("RESET_OUTCOME") || operation.equals("RESET_CREDENTIAL")) {
                executeManagementOperation(url, operation);
                return;
            }
            Command command = Command.valueOf(operation);
            try (Connection connection = DriverManager.getConnection(url, command.login, required("BASE_ADMIN_DB_PASSWORD"))) {
                execute(connection, command);
            }
        } catch (RuntimeException | SQLException failure) {
            System.err.println("Admin test command failed. Verify isolated fixture and credentials.");
            System.exit(1);
        }
    }

    private static void executeManagementOperation(String url, String operation) throws SQLException {
        boolean recovery = operation.equals("RECOVERY_OUTCOME");
        boolean reset = operation.equals("RESET_CREDENTIAL");
        UUID reference = id(recovery ? "BASE_ADMIN_APPROVAL" : "BASE_ADMIN_OPERATION");
        String login = recovery ? "deployment_operator" : "identity_lifecycle_operator";
        try (Connection connection = DriverManager.getConnection(url, login, required("BASE_ADMIN_DB_PASSWORD"))) {
            connection.setReadOnly(!reset);
            connection.setAutoCommit(false);
            if (reset) {
                try (var statement = connection.prepareStatement("select identity_lifecycle.reset_credential(?, ?, ?, ?)")) {
                    statement.setObject(1, reference);
                    statement.setObject(2, id("BASE_ADMIN_TARGET"));
                    statement.setLong(3, Long.parseLong(required("BASE_ADMIN_REVISION")));
                    statement.setString(4, managedHash());
                    try (var result = statement.executeQuery()) {
                        result.next();
                        boolean applied = result.getBoolean(1);
                        try {
                            connection.commit();
                        } catch (SQLException unknown) {
                            System.err.println("Reset outcome unknown. Run RESET_OUTCOME with the original operation reference.");
                            throw unknown;
                        }
                        System.out.println(applied ? "COMMITTED" : "NOT_APPLIED; reconcile the original reference");
                    }
                }
            } else {
                String outcome = reconciliationOutput(connection, recovery, reference);
                connection.rollback();
                System.out.println(outcome);
            }
        }
    }

    static String reconciliationOutput(Connection connection, boolean recovery, UUID reference) throws SQLException {
        String sql = recovery ? "select * from deployment_operator.recovery_outcome(?)"
            : "select * from identity_lifecycle.reset_outcome(?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, reference);
            try (var result = statement.executeQuery()) {
                if (!result.next()) return "UNKNOWN";
                Object revision = result.getObject("revision");
                return result.getString("outcome") + (revision == null ? "" : " revision=" + revision);
            }
        }
    }

    private static void execute(Connection connection, Command command) throws SQLException {
        UUID target = command == Command.APPROVE || command == Command.RECOVER ? id("BASE_ADMIN_TARGET") : null;
        UUID approval = command == Command.CANCEL || command == Command.RECOVER ? id("BASE_ADMIN_APPROVAL") : null;
        connection.setAutoCommit(false);
        boolean commitAttempted = false;
        try (PreparedStatement statement = connection.prepareStatement(command.sql)) {
            switch (command) {
                case BOOTSTRAP -> {
                    statement.setString(1, syntheticEmail(required("BASE_ADMIN_EMAIL")));
                    statement.setString(2, managedHash());
                    statement.setString(3, evidence());
                }
                case APPROVE, RECOVER -> {
                    int parameter = 1;
                    if (command == Command.RECOVER) statement.setObject(parameter++, approval);
                    statement.setObject(parameter++, target);
                    statement.setLong(parameter++, Long.parseLong(required("BASE_ADMIN_REVISION")));
                    RecoveryAction action = RecoveryAction.valueOf(required("BASE_ADMIN_ACTION"));
                    statement.setObject(parameter++, action.name(), java.sql.Types.OTHER);
                    statement.setString(parameter++, action == RecoveryAction.REPLACE_DEDICATED
                        ? syntheticEmail(required("BASE_ADMIN_REPLACEMENT_EMAIL")) : null);
                    if (command == Command.APPROVE) {
                        statement.setString(parameter++, evidence());
                        statement.setObject(parameter, OffsetDateTime.now(ZoneOffset.UTC).plus(Duration.ofHours(1)));
                    } else {
                        statement.setString(parameter, managedHash());
                    }
                }
                case CANCEL -> {
                    statement.setObject(1, approval);
                    statement.setString(2, required("BASE_ADMIN_CANCEL_REASON"));
                }
            }
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Missing governance result");
                String outcome = result.getString(1);
                commitAttempted = true;
                connection.commit();
                System.out.println(outcome == null ? "Admin test bootstrap was already completed." : outcome);
            }
        } catch (SQLException failure) {
            if (commitAttempted) {
                System.err.println(command == Command.RECOVER
                    ? "Recovery outcome unknown. Run RECOVERY_OUTCOME with the original approval UUID."
                    : "Admin completion is uncertain. Check the committed audit before retrying.");
            } else {
                appendDenial(connection, command, target, approval);
            }
            throw failure;
        }
    }

    private static void appendDenial(Connection connection, Command command, UUID target, UUID approval) {
        try {
            connection.rollback();
            try (var audit = connection.prepareStatement("""
                select governance_audit."appendDenied"(?, ?::governance_audit.denial_event, 'TRANSACTION_FAILED', ?, ?)
                """)) {
                audit.setObject(1, UUID.randomUUID());
                audit.setString(2, command.denial);
                audit.setObject(3, target);
                audit.setObject(4, approval);
                audit.execute();
                connection.commit();
            }
        } catch (SQLException unavailable) {
            System.err.println("Admin denial audit unavailable.");
        }
    }

    private static String syntheticEmail(String raw) {
        String email = raw.strip().toLowerCase(Locale.ROOT);
        if (!email.matches("[a-z0-9][a-z0-9._+-]{0,120}@identity-test\\.invalid")) {
            throw new IllegalArgumentException();
        }
        return email;
    }

    private static String managedHash() {
        String password = required("BASE_ADMIN_PASSWORD");
        var passwords = new IdentityPasswords();
        if (!passwords.isAccepted(password)) throw new IllegalArgumentException();
        return passwords.encode(password);
    }

    private static String evidence() {
        String value = required("BASE_ADMIN_MAILBOX_EVIDENCE");
        if (!value.matches("synthetic-fixture-[a-zA-Z0-9_-]{1,80}")) throw new IllegalArgumentException();
        return value;
    }

    private static UUID id(String name) {
        return UUID.fromString(required(name));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException();
        return value;
    }

    private enum RecoveryAction { RESET_DEDICATED, REPLACE_DEDICATED }

    private enum Command {
        BOOTSTRAP("deployment_operator", "BOOTSTRAP_DENIED",
            "select deployment_operator.bootstrap(?, ?, ?)"),
        APPROVE("operator_approver", "APPROVAL_ISSUE_DENIED",
            "select operator_approvals.approve(?, ?, ?, ?, 'deployment_operator', ?, ?)"),
        CANCEL("operator_approver", "APPROVAL_CANCEL_DENIED",
            "select operator_approvals.\"cancelApproval\"(?, ?::operator_approvals.cancel_reason)"),
        RECOVER("deployment_operator", "RECOVERY_DENIED",
            "select deployment_operator.recover(?, ?, ?, ?, ?, ?)");

        private final String login;
        private final String denial;
        private final String sql;

        Command(String login, String denial, String sql) {
            this.login = login;
            this.denial = denial;
            this.sql = sql;
        }
    }
}
