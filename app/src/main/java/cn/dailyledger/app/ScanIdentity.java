package cn.dailyledger.app;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;

public final class ScanIdentity {
    public static String normalize(String s){return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFKC).replaceAll("[\\p{P}\\p{Z}\\s]","").toLowerCase(java.util.Locale.ROOT);}
    public static String key(String channel,String date,String time,long cents,String note){
        try{byte[] hash=MessageDigest.getInstance("SHA-256").digest((channel+"|"+date+"|"+time+"|"+cents+"|"+normalize(note)).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format("%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}
    }
}
