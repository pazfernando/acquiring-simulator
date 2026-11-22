# P2b7 evidence manifest

HEAD: cfc456f4f469d9558a78fda8ff9b770b00817315
Branch: feat/wi-acq-auth-001
Model: DeepSeek V4 Flash (provider `nan`), configured default, no override.

## New source under test (only two files)

```
b012051a8f572f1b6f7ca091a196c86f9c096b60c8ec0a5975ed7b0976b5916d  src/main/java/com/acquiring/simulator/adapter/aws/SignedEvidenceAdmissionService.java
33f8b29dfe79d4beca5935cce2ae5bc5c67a961a43aad3a5a5e686d4763e2719  src/test/java/com/acquiring/simulator/channel/SignedEvidenceAdmissionServiceIntegrationTest.java
```

## Evidence files (sha256)

```
767e36fa1a2f4c159c131db79df74aca024dcf16582da44576054352630c37e2  BINDING.txt
963e5dbe5b0462f5e36d3e0fbd31f8baff49a40bac39e44863c2cda9672493ad  branch.txt
c1eb2278a6a75b8f9a9e0f3d7f401dbe7b630a1de7bdabd2861b064e77531494  com.acquiring.simulator.channel.SignedEvidenceAdmissionServiceIntegrationTest.txt
63544fdbb882864cfbff8831a216198bec0b4b167f76f5e05b54b181421d672a  git-status-after.txt
63544fdbb882864cfbff8831a216198bec0b4b167f76f5e05b54b181421d672a  git-status-before.txt
df2dad8fdac4f8ffefe651351e12d91fd2baa1fd7de7a54915d584a0a482bd1e  hashes-after.sha256
df2dad8fdac4f8ffefe651351e12d91fd2baa1fd7de7a54915d584a0a482bd1e  hashes-before.sha256
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  hashes.diff
1244ab5a8bb9a777450128221325968c13c9045f94b8fcf9bb40229a62f81ec7  head-after.txt
1244ab5a8bb9a777450128221325968c13c9045f94b8fcf9bb40229a62f81ec7  head-before.txt
7a49039c485de70a5e0b383c9fe586ab9d3a9a92301f64b192605641f49bd7af  PROGRESS.md
d99f441bae1eb076e0be11f76531ca6cc8a73fc34048c52a527bf89e1545dc47  README.md
e214309d977edb29824baf90d70bdf55d13b270df686c0332720d7a310d6aff4  RESULTS.md
9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa  targeted-exit.txt
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  targeted-stderr.txt
2185d1a26a15e087596dca8578b6a64a0b49a64d0a8f287b1f78c483844727e2  targeted-stdout.txt
0d37ee56ea6bd11b2f4356bd46bc90a398d26ca5395339fa1933001ff00e58b4  TEST-com.acquiring.simulator.channel.SignedEvidenceAdmissionServiceIntegrationTest.xml
f0081a72be2f640675aed676d769e2730160bc7c6e73c37ad0f4d9bec9106977  test-command.txt
87452ed337b95577413af5d6fae34cb91e8d71bec565efef9cf67888ac97f0a3  toolchain.txt
1f5acefc01976ee8cd57f91855fb60ed4d06961d782e312b45d979cf79ecc76e  wiring.txt
```

## Frozen oracle set

`hashes-before.sha256` and `hashes-after.sha256` cover 42 inputs: every file under
`src/main/java/com/acquiring/simulator/adapter/aws/`, every file under
`src/test/java/com/acquiring/simulator/channel/` (including the two new files) and `pom.xml`.
`hashes.diff` is empty (exit 0). `head-before.txt` == `head-after.txt` and
`git-status-before.txt` == `git-status-after.txt`.

## Delivered-file re-verification

The two source hashes above were re-read after the run and match the frozen set. Only the facade
and its test reference `SignedEvidenceAdmissionService` (`wiring.txt`).
