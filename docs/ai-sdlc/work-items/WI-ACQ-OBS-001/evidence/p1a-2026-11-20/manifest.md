# P1a evidence — 2026-11-20

Binding: branch feat/wi-acq-auth-001, HEAD cfc456f4f469d9558a78fda8ff9b770b00817315, origin https://github.com/pazfernando/acquiring-simulator.git

Commands (JDK 25.0.5, Maven 3.10.1, offline):
- mvn -o -q -DskipTests compile -> exit 0
- mvn -o -Dtest='com.acquiring.simulator.domain.event.*Test' test -> exit 0, Tests run: 42, Failures: 0, Errors: 0 (test.log)
- mvn -o test -> exit 0, Tests run: 106, Failures: 0, Errors: 0 (full-test.log)

Model: provider nan, model deepseek-v4-flash; no override.

SHA-256 of delivered files:
cee01189b22ef6723bf37feb9d1f40a4b33503037821269ebbcf45038975747e  src/main/java/com/acquiring/simulator/domain/event/CanonicalEventCodec.java
b6dfdf8d2860fd789f324b426a190b53cd790a22756fc1325722d9e7960e8ef5  src/main/java/com/acquiring/simulator/domain/event/CanonicalEvent.java
e5e544cff062ab6567d7f79a6eee6c31bf571a8781ced3772bdbdd36d6c7fa8d  src/main/java/com/acquiring/simulator/domain/event/CanonicalEventJson.java
6bfbd49374b5313645f0b23c9f477ca9c3bdeda5ac0c54f14014d4e452d6ffec  src/main/java/com/acquiring/simulator/domain/event/EventActor.java
71a84c33f731aab3401429d5589474a95eea731dcfd23494512a93df2373121f  src/main/java/com/acquiring/simulator/domain/event/EventClockQuality.java
28bc49031a33d7fbfd6cea225ad36aff91787106cd64605535ce9a27d9886693  src/main/java/com/acquiring/simulator/domain/event/EventDedupeKey.java
d4e3046dbef9f060a97a29e9604e18fd36ef36b9366e5d5c9078d9e14f169e49  src/main/java/com/acquiring/simulator/domain/event/EventDeduplication.java
b77945058c678aa9e34bf9f42215dd99b77d8601272b7d37f68cf2475e0f9c1f  src/main/java/com/acquiring/simulator/domain/event/EventDurability.java
abbb2194984218344b2b82d82348df8309337167e8efddbfd3902d901d8df6c8  src/main/java/com/acquiring/simulator/domain/event/EventGapCode.java
2fa1461246a75ee6a6823149821585a9c0211b50224a3f787de4a83e1c797fea  src/main/java/com/acquiring/simulator/domain/event/EventManifest.java
4d8f5a59c64bd4c69a41f704b6c658419bbc61a35010b4ad6d2ad1b31e7c9a9e  src/main/java/com/acquiring/simulator/domain/event/EventProvenance.java
dfafd6830c88a7503f8060939b9104b6d08812d05d162f49e21dbae74f2f7a3b  src/main/java/com/acquiring/simulator/domain/event/EventReason.java
8d56e769942c3f5460648354160b7ee496c267e1518461de7e2fac737826107a  src/main/java/com/acquiring/simulator/domain/event/EventSanitizer.java
9c9754e5e171083cd7ee5a51cdf8ca7ffbddbe9706c5d46ff39960ae50f62270  src/main/java/com/acquiring/simulator/domain/event/EventSourceWrapper.java
949e7b6e5591ea83319bf969b7dc04be0c8168c21bf7fe14411c21fe23660a7f  src/main/java/com/acquiring/simulator/domain/event/EventStatus.java
2eacb78e5a36c43c256b964964a568132edec0c6d4aeaa8e33fd0c4519d24e3b  src/main/java/com/acquiring/simulator/domain/event/EventTask.java
eec58a1f5ec28bd4629fa2ec7a0cc4b25adcecee2e3e5f57f4ccc31a89e35ef3  src/main/java/com/acquiring/simulator/domain/event/EventValidationException.java
2baf0869958c859be49e8486a2e9423e77afd9a04b9e623c3305ac7110416aa6  src/main/java/com/acquiring/simulator/domain/event/EventValidator.java
87b2290e7afae8a46a9b24fb8d4da95586f0282ab06537101cd505cc1d024cc0  src/main/java/com/acquiring/simulator/domain/event/EventViolation.java
d4f0f780f5ddd5e024a9c2ca62e264635ef74c8ea5dd5d0aca926838d4d7c8e6  src/main/java/com/acquiring/simulator/domain/event/OperationAManifest.java
ec1c4c2fbca39850ed6e5e35e8277d1381d61f04661b8336d778cc1ddb0eacd3  src/test/java/com/acquiring/simulator/domain/event/CanonicalEventCodecTest.java
a7df0350670a9f0e6c48fa6728797ad8b82dea9e15c392860498b1aa90d28baa  src/test/java/com/acquiring/simulator/domain/event/EventDeduplicationTest.java
6c38daa592f8dda975c190c7e0a094c013b7f1d4c9c73bea69a33190521e98f8  src/test/java/com/acquiring/simulator/domain/event/EventSanitizerTest.java
d21fb6bfaef0baaa5a6354fbcd39220ea021807db4a8f076c5ea28de70be4e41  src/test/java/com/acquiring/simulator/domain/event/EventSourceWrapperTest.java
9675db7a7621e0d7e519554d64f7007f6e6bbc7d0cc254afce39bee8880a3122  src/test/java/com/acquiring/simulator/domain/event/EventValidatorTest.java
28e973d91a9dcef376509dd2daab7dc55d940192c1da55940fb9a674fcdb6f40  src/test/java/com/acquiring/simulator/domain/event/OperationAManifestTest.java
