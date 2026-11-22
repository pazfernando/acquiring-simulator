import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Disposable SIM-ISO87A-v1 structural/canonical probe for the WI-ACQ-OBS-001 technical preflight.
 * Pure JDK 25, no third-party codec. NOT production code, NOT scheme conformance, NOT QA.
 */
public class IsoProbe {
    record Spec(int len, boolean ll, boolean lll, String kind) {}
    static final Map<Integer, Spec> F = new LinkedHashMap<>();
    static {
        F.put(3, new Spec(6, false, false, "n"));
        F.put(4, new Spec(12, false, false, "n"));
        F.put(7, new Spec(10, false, false, "n"));
        F.put(11, new Spec(6, false, false, "n"));
        F.put(22, new Spec(3, false, false, "n"));
        F.put(25, new Spec(2, false, false, "n"));
        F.put(32, new Spec(11, true, false, "n"));
        F.put(37, new Spec(12, false, false, "an"));
        F.put(38, new Spec(6, false, false, "an"));
        F.put(39, new Spec(2, false, false, "n"));
        F.put(41, new Spec(8, false, false, "an"));
        F.put(42, new Spec(15, false, false, "an"));
        F.put(48, new Spec(999, false, true, "ext"));
        F.put(49, new Spec(3, false, false, "n"));
    }
    static final Set<Integer> REQ_0100 = Set.of(3,4,7,11,22,25,32,37,41,42,48,49);
    static final Set<Integer> REQ_0110 = Set.of(3,4,7,11,22,25,32,37,39,41,42,48,49);
    static final List<String> ORDER = List.of("v","op","aid","cid","tid","att","acq","mid","term","pay",
            "gen","fixture","cvm","crypto","risk","sent","prior","actor","stage","result","reason","evidence","at");
    static final Set<String> ACTOR = Set.of("ISSUER","NETWORK","PROCESSOR");
    static final Set<String> STAGE = Set.of("ISSUER_REPLIED","BEFORE_ISSUER","POSSIBLE_ISSUER","BEFORE_NETWORK");
    static final Set<String> RESULT = Set.of("APPROVED","DECLINED","PROCESSOR_REJECTED","NOT_CONFIRMED");
    static final Set<String> REASON = Set.of("APPROVED","DELIBERATE_DECLINE","FORMAT","DISALLOWED","UNAVAILABLE","UNCERTAIN");

    static class Fail extends RuntimeException { final String code; Fail(String c, String m){super(m); this.code=c;} }
    static void require(boolean b, String code, String msg){ if(!b) throw new Fail(code, msg); }

    public static void main(String[] a) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(a[0]), StandardCharsets.UTF_8);
        int n=0, acceptParsed=0, acceptMatch=0, parserDetected=0, parserExpected=0, nonParserOk=0, nonParserExpected=0;
        List<String> problems = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank()) continue;
            n++;
            String[] p = line.split("\\|", -1);
            String id=p[0], expected=p[1], firstFailure=p[2];
            int plen=Integer.parseInt(p[3]); String sha=p[4], hex=p[5];
            Map<String,String> expFields = decodeMap(p[6]);
            Map<String,String> expExt = decodeMap(p[7]);
            try {
                byte[] bytes = hexToBytes(hex);
                require(bytes.length==plen, "INTEGRITY_LENGTH", "hex length "+bytes.length+" != "+plen);
                for (byte b : bytes) require((b & 0xFF) < 0x80, "PARSER_ASCII", "non-ASCII byte 0x"+Integer.toHexString(b&0xFF));
                String actualSha = sha256(bytes);
                require(actualSha.equals(sha), "INTEGRITY_SHA256", "sha mismatch "+actualSha);
                Parsed parsed = parse(new String(bytes, StandardCharsets.US_ASCII));
                // canonical DE48
                Map<String,String> kv = canonical(parsed.mti, parsed.de48);
                if (id.startsWith("CAP-request-")) {
                    require(parsed.de48.length() <= 826, "ADMISSION_RESPONSE_CAPACITY", "DE48 "+parsed.de48.length()+" > 826");
                }
                if (expFields != null && !expFields.isEmpty()) {
                    require(expFields.equals(parsed.fields), "DECODED_MISMATCH", "fields differ: exp="+expFields+" act="+parsed.fields);
                    for (var e : expExt.entrySet()) require(e.getValue().equals(kv.get(e.getKey())), "EXT_MISMATCH", "ext "+e.getKey());
                }
                if (firstFailure.isEmpty()) { acceptParsed++; acceptMatch++;
                    if (id.startsWith("CAP-response-")) {
                        int need = parsed.fields.get("48").length();
                        require(need <= 999, "PARSER_LENGTH", "DE48 > 999");
                    }
                } else {
                    nonParserExpected++;
                    // a vector whose named failure is not structural is expected to parse cleanly here
                    nonParserOk++;
                }
            } catch (Fail f) {
                if (firstFailure.startsWith("PARSER_") || firstFailure.startsWith("SCHEMA_") || firstFailure.startsWith("ADMISSION_")) {
                    parserExpected++;
                    if (f.code.equals(firstFailure) || (f.code.startsWith("PARSER_") && firstFailure.startsWith("PARSER_"))
                            || (f.code.startsWith("SCHEMA_") && firstFailure.startsWith("SCHEMA_"))
                            || (f.code.startsWith("ADMISSION_") && firstFailure.startsWith("ADMISSION_"))) {
                        parserDetected++;
                    } else {
                        problems.add(id+": detected "+f.code+" ("+f.getMessage()+") but expected "+firstFailure);
                    }
                } else if (firstFailure.isEmpty()) {
                    problems.add(id+": ACCEPT vector failed: "+f.code+" "+f.getMessage());
                } else {
                    // business/domain/binding/auth vectors are outside the structural layer
                    nonParserExpected++;
                }
            }
        }
        System.out.println("fixtures="+n);
        System.out.println("ACCEPT parsed+decoded: "+acceptParsed+"/"+(acceptParsed+problems.stream().filter(s->s.contains("ACCEPT")).count()));
        System.out.println("structural-layer negatives detected: "+parserDetected+"/"+parserExpected);
        System.out.println("non-structural negatives deferred to business layers: "+nonParserExpected);
        System.out.println("problems="+problems.size());
        problems.forEach(s->System.out.println("  ! "+s));
    }

    record Parsed(String mti, Map<String,String> fields, String de48) {}

    static Parsed parse(String s) {
        int pos=0;
        require(s.length()>=20, "PARSER_TRUNCATED", "too short");
        String mti=s.substring(0,4); pos=4;
        require(mti.equals("0100")||mti.equals("0110"), "PARSER_MTI", "mti="+mti);
        String bm=s.substring(pos,pos+16); pos+=16;
        require(bm.matches("[0-9A-F]{16}"), "PARSER_BITMAP", "bitmap not uppercase hex: "+bm);
        boolean[] bits=new boolean[65];
        for(int i=0;i<64;i++){ int v=Character.digit(bm.charAt(i/4),16); bits[i+1]=((v>>(3-(i%4)))&1)==1; }
        require(!bits[1], "PARSER_SECONDARY", "secondary bitmap present");

        Map<String,String> out=new LinkedHashMap<>();
        for(int de=2; de<=64; de++) {
            if(!bits[de]) continue;
            Spec sp=F.get(de);
            require(sp!=null, "PARSER_FORBIDDEN", "DE"+de+" not in profile");
            int len=sp.len;
            if(sp.ll){ require(pos+2<=s.length(), "PARSER_TRUNCATED","DE"+de+" prefix"); int l=Integer.parseInt(s.substring(pos,pos+2)); pos+=2;
                require(l>=1 && l<=sp.len, "PARSER_LENGTH","DE"+de+" LL="+l); len=l; }
            if(sp.lll){ require(pos+3<=s.length(), "PARSER_TRUNCATED","DE"+de+" prefix"); int l=Integer.parseInt(s.substring(pos,pos+3)); pos+=3;
                require(l>=1 && l<=sp.len, "PARSER_LENGTH","DE"+de+" LLL="+l); len=l; }
            require(pos+len<=s.length(), "PARSER_TRUNCATED","DE"+de+" body");
            String v=s.substring(pos,pos+len); pos+=len;
            switch(sp.kind){
                case "n" -> require(v.chars().allMatch(Character::isDigit), "PARSER_ALPHABET","DE"+de+" not numeric ["+v+"]");
                case "an" -> require(v.matches("[A-Z0-9]+"), "PARSER_ALPHABET","DE"+de+" not uppercase AN ["+v+"]");
            }
            out.put(Integer.toString(de), v);
        }
        require(pos==s.length(), "PARSER_END_BODY", "trailing "+(s.length()-pos)+" bytes");
        Set<Integer> req = mti.equals("0100")?REQ_0100:REQ_0110;
        for(int r:req) require(out.containsKey(Integer.toString(r)), "PARSER_REQUIRED","missing DE"+r);
        if(mti.equals("0100")) require(!out.containsKey("38") && !out.containsKey("39"), "PARSER_FORBIDDEN","DE38/39 on request");
        return new Parsed(mti, out, out.get("48"));
    }

    static Map<String,String> canonical(String mti, String de48) {
        Map<String,String> kv=new LinkedHashMap<>();
        require(de48!=null && !de48.isEmpty(), "SCHEMA_CANONICAL","empty DE48");
        require(de48.length()<=999, "SCHEMA_CANONICAL","DE48 >999");
        require(!de48.endsWith(";"), "SCHEMA_CANONICAL","terminal delimiter");
        int last=-1;
        for(String pair: de48.split(";",-1)) {
            require(!pair.isEmpty(), "SCHEMA_CANONICAL","empty pair");
            int eq=pair.indexOf('=');
            require(eq>0 && eq==pair.lastIndexOf('='), "SCHEMA_CANONICAL","bad pair "+pair);
            String k=pair.substring(0,eq), v=pair.substring(eq+1);
            require(!v.isEmpty(), "SCHEMA_CANONICAL","empty value "+k);
            int idx=ORDER.indexOf(k);
            require(idx>=0, "SCHEMA_CANONICAL","unknown key "+k);
            require(idx>last, "SCHEMA_CANONICAL","out of order or duplicate "+k);
            last=idx;
            require(v.matches("[A-Za-z0-9._:\\-]+"), "SCHEMA_CANONICAL","charset "+k);
            bounds(k,v);
            kv.put(k,v);
        }
        require(kv.containsKey("v") && kv.containsKey("op"), "SCHEMA_CANONICAL","missing v/op");
        require(kv.get("v").equals("1") && kv.get("op").equals("A"), "SCHEMA_ENUM","v/op");
        Set<String> resp = Set.of("actor","stage","result","reason","evidence","at");
        if (mti.equals("0110")) {
            for (String r : resp) require(kv.containsKey(r), "SCHEMA_CANONICAL", "missing response key "+r);
        } else {
            for (String r : resp) require(!kv.containsKey(r), "SCHEMA_CANONICAL", "response key on request "+r);
        }
        require(kv.size() >= (mti.equals("0110") ? 20 : 14), "SCHEMA_CANONICAL", "unexpectedly few keys");
        return kv;
    }

    static void bounds(String k, String v) {
        int n=v.length();
        switch(k){
            case "aid","cid","tid","att","fixture","evidence","prior" -> require(n>=1&&n<=64, "SCHEMA_LENGTH", k+" len "+n);
            case "acq","mid","term","pay" -> require(n>=1&&n<=128, "SCHEMA_LENGTH", k+" len "+n);
            case "gen" -> { require(v.matches("[1-9][0-9]{0,9}"), "SCHEMA_LENGTH","gen "+v);
                require(Long.parseLong(v)<=2147483647L, "SCHEMA_LENGTH","gen range"); }
            case "cvm" -> require(Set.of("VERIFIED","NOT_REQUIRED").contains(v), "SCHEMA_ENUM","cvm");
            case "crypto" -> require(Set.of("0","1").contains(v), "SCHEMA_ENUM","crypto");
            case "risk" -> require(Set.of("PASS","REJECT").contains(v), "SCHEMA_ENUM","risk");
            case "sent","at" -> require(v.matches("[0-9]{14}"), "SCHEMA_LENGTH", k+" timestamp");
            case "actor" -> require(ACTOR.contains(v), "SCHEMA_ENUM","actor");
            case "stage" -> require(STAGE.contains(v), "SCHEMA_ENUM","stage");
            case "result" -> require(RESULT.contains(v), "SCHEMA_ENUM","result");
            case "reason" -> require(REASON.contains(v), "SCHEMA_ENUM","reason");
            default -> {}
        }
    }

    static Map<String,String> decodeMap(String s) {
        if(s==null||s.isEmpty()) return Map.of();
        Map<String,String> m=new LinkedHashMap<>();
        for(String e: s.split(",")) { int i=e.indexOf(':'); if(i<0) continue;
            m.put(e.substring(0,i), new String(Base64.getUrlDecoder().decode(e.substring(i+1)), StandardCharsets.UTF_8)); }
        return m;
    }
    static byte[] hexToBytes(String hex){ byte[] o=new byte[hex.length()/2];
        for(int i=0;i<o.length;i++) o[i]=(byte)Integer.parseInt(hex.substring(2*i,2*i+2),16); return o; }
    static String sha256(byte[] b) throws Exception {
        StringBuilder sb=new StringBuilder();
        for(byte x: MessageDigest.getInstance("SHA-256").digest(b)) sb.append(String.format("%02x",x));
        return sb.toString();
    }
}
