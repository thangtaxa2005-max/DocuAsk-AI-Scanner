package com.docuask.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class GeminiClient {
    static final String MODEL="gemini-3.6-flash";
    static final String FALLBACK_MODEL="gemini-3.8-flash";
    private final Context context;
    GeminiClient(Context context) { this.context=context; }
    static final class Answer { String text, sources; Answer(String text,String sources) { this.text=text; this.sources=sources; } }
    private static final class Chunk { int page,part; String text; double score; }
    Answer answer(String key, List<DocStore.Page> pages, String question) throws Exception {
        List<Chunk> chunks=chunks(pages); Set<String> terms=terms(question);
        for(Chunk c:chunks) { Set<String> words=terms(c.text); for(String t:terms) if(words.contains(t)) c.score+=1; c.score/=Math.sqrt(words.size()+1); }
        chunks.sort(Comparator.comparingDouble((Chunk c)->c.score).reversed());
        if(chunks.isEmpty()||chunks.get(0).score<=0) return new Answer("Mình chưa tìm thấy đoạn liên quan trong tài liệu. Hãy kiểm tra lại văn bản OCR hoặc hỏi cụ thể hơn.","");
        StringBuilder contextText=new StringBuilder(); List<String> labels=new ArrayList<>();
        int count=Math.min(4,chunks.size());
        for(int i=0;i<count;i++) { Chunk c=chunks.get(i); String label="[Trang "+c.page+", đoạn "+c.part+"]"; labels.add(label); contextText.append(label).append('\n').append(c.text).append("\n\n"); }
        String prompt="Bạn là trợ lý đọc tài liệu. Chỉ dùng các đoạn trích bên dưới để trả lời trực tiếp bằng tiếng Việt, tối đa 100 từ. "
                +"Nếu không đủ thông tin, nói rõ không tìm thấy. Gắn nhãn [Trang N, đoạn M] sau câu được đoạn đó hỗ trợ; "
                +"gom các ý cùng nguồn để không lặp nhãn quá nhiều. Không bịa số liệu hoặc nguồn.\n\nCÂU HỎI: "+question+"\n\nĐOẠN TRÍCH:\n"+contextText;
        String response=generate(key,prompt);StringBuilder sources=new StringBuilder();
        for(String label:labels)if(response.contains(label)){if(sources.length()>0)sources.append('\n');sources.append(label);}
        if(sources.length()==0)sources.append(labels.get(0));
        return new Answer(response,sources.toString());
    }
    String summarize(String key, List<DocStore.Page> pages) throws Exception {
        List<Chunk> chunks=chunks(pages); if(chunks.isEmpty()) throw new Exception("Tài liệu chưa có văn bản để tóm tắt.");
        StringBuilder body=new StringBuilder(); int max=22000;
        // Sample across the document, so late pages are represented in long PDFs.
        int take=Math.min(chunks.size(),Math.max(1,max/900));
        for(int i=0;i<take;i++) { Chunk c=chunks.get(take==1?0:(int)Math.round(i*(chunks.size()-1.0)/(take-1))); body.append("[Trang ").append(c.page).append("] ").append(c.text).append("\n"); }
        String prompt="Tóm tắt tài liệu bằng tiếng Việt trong một đoạn văn tối đa 100 từ. Nêu mục đích và gom các chủ đề chính; giữ lời khuyên cuối nếu có. "
                +"Chỉ dùng nội dung được cung cấp, bỏ qua lỗi đánh máy OCR rõ ràng nhưng không suy đoán phần không chắc. "
                +"Không viết tiêu đề, danh sách, Markdown hoặc mục số liệu khi nguồn không có số liệu. "
                +(take<chunks.size()?"Đây chỉ là một số đoạn được chọn từ tài liệu dài; nói rõ giới hạn đó.\n\n":"\n\n")+body;
        return generate(key,prompt);
    }
    private List<Chunk> chunks(List<DocStore.Page> pages) {
        List<Chunk> out=new ArrayList<>();
        for(DocStore.Page p:pages) { String text=p.text==null?"":p.text.trim(); int pos=0,part=1;
            while(pos<text.length()) { int end=Math.min(text.length(),pos+900); if(end<text.length()) { int split=text.lastIndexOf(' ',end); if(split>pos+500) end=split; }
                Chunk c=new Chunk(); c.page=p.number; c.part=part++; c.text=text.substring(pos,end).trim(); if(!c.text.isEmpty()) out.add(c); pos=end;
            }
        }
        return out;
    }
    private Set<String> terms(String text) {
        String normalized=Normalizer.normalize(text.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d');
        Set<String> set=new HashSet<>(); for(String word:normalized.split("[^a-z0-9]+")) if(word.length()>2) set.add(word); return set;
    }
    private String generate(String key,String prompt) throws Exception {
        if(key==null||key.trim().isEmpty()) throw new Exception("Hãy nhập Gemini API key trong phần cài đặt.");
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());
        if(capabilities==null||!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) throw new Exception("Không có kết nối mạng. OCR và tài liệu đã lưu vẫn dùng được ngoại tuyến.");
        String[] models={MODEL,FALLBACK_MODEL};
        for(int attempt=0;attempt<models.length;attempt++) {
            try { return generateOnce(key,prompt,models[attempt]); }
            catch(SocketTimeoutException e) {
                if(attempt==models.length-1) throw new Exception("Gemini phản hồi quá chậm trên cả hai model. Hãy kiểm tra mạng và thử lại sau ít phút.");
            }
            catch(GeminiHttpException e) {
                if(e.status==429) throw new Exception("Gemini đang giới hạn lượt gọi hoặc đã hết hạn mức. Hãy kiểm tra Usage trong Google AI Studio và thử lại sau.");
                if(!retryable(e.status)) throw e;
                if(attempt==models.length-1) throw new Exception("Gemini đang bận hoặc phản hồi quá chậm trên cả hai model. Hãy thử lại sau ít phút.");
            }
        }
        throw new Exception("Không thể kết nối Gemini. Hãy thử lại sau.");
    }
    private static boolean retryable(int status) { return status==408||status==500||status==502||status==503||status==504; }
    private static final class GeminiHttpException extends Exception {
        final int status;
        GeminiHttpException(int status,String message) { super(message); this.status=status; }
    }
    private String generateOnce(String key,String prompt,String model) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL("https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent").openConnection();
        conn.setRequestMethod("POST"); conn.setConnectTimeout(10000); conn.setReadTimeout(20000); conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type","application/json; charset=utf-8"); conn.setRequestProperty("x-goog-api-key",key.trim());
        JSONObject payload=new JSONObject().put("contents",new JSONArray().put(new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",prompt)))));
        try {
            try(OutputStream out=conn.getOutputStream()) { out.write(payload.toString().getBytes(StandardCharsets.UTF_8)); }
            int status=conn.getResponseCode(); StringBuilder raw=new StringBuilder();
            java.io.InputStream stream=status<400?conn.getInputStream():conn.getErrorStream();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream==null?new java.io.ByteArrayInputStream(new byte[0]):stream,StandardCharsets.UTF_8))) {
                String line; while((line=reader.readLine())!=null) raw.append(line);
            }
            if(status==401||status==403) throw new GeminiHttpException(status,"API key không hợp lệ hoặc chưa được cấp quyền Gemini.");
            if(status>=400) { JSONObject error=raw.length()==0?null:new JSONObject(raw.toString()).optJSONObject("error"); String message=error==null?"HTTP "+status:error.optString("message","HTTP "+status); throw new GeminiHttpException(status,"Gemini: "+message); }
            JSONObject response=new JSONObject(raw.toString()); JSONArray candidates=response.optJSONArray("candidates");
            if(candidates==null||candidates.length()==0) throw new Exception("Gemini không trả lời câu hỏi này.");
            JSONArray parts=candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts"); StringBuilder answer=new StringBuilder();
            for(int i=0;i<parts.length();i++) answer.append(parts.getJSONObject(i).optString("text"));
            if(answer.length()==0) throw new Exception("Gemini trả về nội dung rỗng."); return answer.toString().trim();
        } finally { conn.disconnect(); }
    }
}
