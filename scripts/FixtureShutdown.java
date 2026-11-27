import com.sun.tools.attach.VirtualMachine;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/** Test-only helper for a real Spring context shutdown on the owned fixture JVM. */
class FixtureShutdown {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].equals(System.getenv("BASE_ACCEPTANCE_BACKEND_PID"))) {
            throw new IllegalArgumentException("Missing owned fixture process ID");
        }
        VirtualMachine fixture = VirtualMachine.attach(args[0]);
        String address;
        try {
            address = fixture.startLocalManagementAgent();
        } finally {
            fixture.detach();
        }
        try (var connector = JMXConnectorFactory.connect(new JMXServiceURL(address))) {
            var server = connector.getMBeanServerConnection();
            var admin = new ObjectName("org.springframework.boot:type=Admin,name=SpringApplication");
            if (!server.isRegistered(admin)) throw new IllegalStateException("Fixture Spring admin bean unavailable");
            System.out.println("FIXTURE_READY");
            if (System.in.read() < 0) return;
            try {
                server.invoke(admin, "shutdown", new Object[0], new String[0]);
            } catch (java.io.IOException closedDuringShutdown) {
                // The target can release its connector before returning control.
                // The browser fixture separately verifies completion and exit.
            }
        }
    }
}
