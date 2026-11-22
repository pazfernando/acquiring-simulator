import java.time.Duration;

/**
 * Post-fix companion probe. Loads the compiled production seam from target/classes and prints
 * the resolved AWS cold-start default when RESPONSE_TIMEOUT_MS is absent. Exit 0 only when it
 * equals the approved AUTH-001 R20 default (30 seconds).
 *
 * <p>No AWS call, no Spring context, no runtime activation.</p>
 */
public class PostFixDefaultProbe {

    public static void main(String[] args) throws Exception {
        Class<?> seam = Class.forName("com.acquiring.simulator.config.ResponseTimeoutConfig");
        Duration resolved = (Duration) seam.getMethod("resolve", String.class).invoke(null, (Object) null);
        Duration approved = (Duration) seam.getField("APPROVED_DEFAULT_RESPONSE_TIMEOUT").get(null);

        System.out.println("RESPONSE_TIMEOUT_MS present in environment = "
                + (System.getenv("RESPONSE_TIMEOUT_MS") != null));
        System.out.println("post-fix resolved default = " + resolved.toMillis() + " ms");
        System.out.println("approved AUTH-001 R20    = " + approved.toMillis() + " ms");

        if (!resolved.equals(approved) || !resolved.equals(Duration.ofSeconds(30))) {
            System.err.println("DIVERGENCE: resolved " + resolved + " != approved " + approved);
            System.exit(1);
        }
        System.out.println("MATCH = true");
    }
}
