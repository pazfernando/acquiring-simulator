import java.time.Duration;
import java.util.Properties;

/**
 * Pre-fix defect probe. Reproduces the exact expression in
 * AwsRuntime.processor() at HEAD cfc456f4f469d9558a78fda8ff9b770b00817315
 * and compares it with the approved AUTH-001 R20 default and application.properties.
 *
 * <p>No AWS call, no repository source modification. Exit 1 when the effective
 * AWS default diverges from the approved value (the pre-fix expectation).</p>
 */
public class ResponseTimeoutDefaultProbe {

    public static void main(String[] args) throws Exception {
        String raw = System.getenv("RESPONSE_TIMEOUT_MS");

        // Exact pre-fix expression from AwsRuntime.processor() line 37.
        Duration effectiveDefault = Duration.ofMillis(
                Long.parseLong(System.getenv().getOrDefault("RESPONSE_TIMEOUT_MS", "2000")));

        Duration approvedDefault = Duration.ofSeconds(30);

        String propertiesPath = args.length > 0 ? args[0] : "src/main/resources/application.properties";
        Properties properties = new Properties();
        try (var in = java.nio.file.Files.newInputStream(java.nio.file.Path.of(propertiesPath))) {
            properties.load(in);
        }
        String declared = properties.getProperty("simulator.authorization.response-timeout");

        System.out.println("RESPONSE_TIMEOUT_MS present in environment = " + (raw != null));
        System.out.println("pre-fix AwsRuntime effective default = " + effectiveDefault.toMillis() + " ms");
        System.out.println("approved AUTH-001 R20 default       = " + approvedDefault.toMillis() + " ms");
        System.out.println("application.properties              = " + declared);

        boolean matches = effectiveDefault.equals(approvedDefault);
        System.out.println("MATCH = " + matches);
        if (!matches) {
            System.err.println("DIVERGENCE: effective AwsRuntime default "
                    + effectiveDefault.toMillis() + " ms != approved "
                    + approvedDefault.toMillis() + " ms (application.properties " + declared + ")");
            System.exit(1);
        }
    }
}
