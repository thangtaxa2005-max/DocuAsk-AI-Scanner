package com.docuask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int CAMERA=101, PDF=102, EXPORT=103;
    private static final int BLUE=Color.rgb(36,72,220), INK=Color.rgb(24,32,49), MUTED=Color.rgb(111,121,140), BG=Color.rgb(246,247,250);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private DocStore store; private DocumentProcessor processor; private GeminiClient gemini;
    private LinearLayout root,content; private String screen="library",search="",apiKey=""; private long currentId=-1;
    private File pendingPhoto; private final List<File> scans=new ArrayList<>(); private boolean askTab=false,busy=false;
    private String lastQuestion="",lastAnswer="",lastSources=""; private DocStore.Doc current;
    @Override public void onCreate(Bundle b) { super.onCreate(b); getWindow().setStatusBarColor(Color.WHITE); getWindow().setNavigationBarColor(Color.WHITE); getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR); store=new DocStore(this); processor=new DocumentProcessor(this); gemini=new GeminiClient(this); library(); }
    @Override protected void onDestroy() { worker.shutdown(); store.close(); super.onDestroy(); }
    private int dp(float v) { return (int)(getResources().getDisplayMetrics().density*v+.5f); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private GradientDrawable stroke(int color,int border,int radius) { GradientDrawable d=shape(color,radius); d.setStroke(dp(1),border); return d; }
    private TextView text(String value,int sp,int color,boolean bold) { TextView t=new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); if(bold)t.setTypeface(null,Typeface.BOLD); t.setGravity(Gravity.CENTER_VERTICAL); return t; }
    private LinearLayout.LayoutParams lp(int w,int h) { return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)); }
    private void pad(View v,int x,int y) { v.setPadding(dp(x),dp(y),dp(x),dp(y)); }
    private void gap(LinearLayout parent,int h) { View v=new View(this); parent.addView(v,lp(1,h)); }
    private TextView button(String label,boolean primary,Runnable action) { TextView v=text(label,14,primary?Color.WHITE:INK,true); v.setGravity(Gravity.CENTER); v.setBackground(primary?shape(BLUE,11):stroke(Color.WHITE,0xffdce0e7,11)); v.setOnClickListener(x->action.run()); v.setMinHeight(dp(48)); return v; }
    private void shell(boolean dark) { root=column(); root.setBackgroundColor(dark?0xff111318:BG); setContentView(root); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private String date(long time) { return new SimpleDateFormat("dd/MM/yyyy",new Locale("vi","VN")).format(new Date(time)); }
    private String timestamp() { return new SimpleDateFormat("dd/MM/yyyy HH:mm",new Locale("vi","VN")).format(new Date()); }
    private void error(Exception e) { runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Chưa hoàn tất").setMessage(e.getMessage()==null?"Có lỗi xảy ra.":e.getMessage()).setPositiveButton("Đóng",null).show()); }
    private interface Job<T> { T run() throws Exception; }
    private interface Done<T> { void accept(T value); }
    private <T> void async(String message,Job<T> job,Done<T> done) { if(busy)return; busy=true; final AlertDialog dialog=new AlertDialog.Builder(this).setView(progressView(message)).setCancelable(false).create(); dialog.show(); worker.execute(()->{ try { T value=job.run(); runOnUiThread(()->{ busy=false; dialog.dismiss(); done.accept(value); }); } catch(Exception e) { runOnUiThread(()->{ busy=false; dialog.dismiss(); error(e); }); } }); }
    private View progressView(String message) { LinearLayout box=row(); pad(box,24,24); ProgressBar bar=new ProgressBar(this); box.addView(bar,lp(36,36)); TextView label=text("  "+message,15,INK,false); label.setTag("label"); box.addView(label); return box; }
    private void header(LinearLayout parent,String title,String action,Runnable click) { LinearLayout bar=row(); pad(bar,22,14); TextView h=text(title,23,INK,true); bar.addView(h,new LinearLayout.LayoutParams(0,dp(42),1)); if(action!=null){ TextView a=text(action,13,BLUE,true); a.setOnClickListener(v->click.run()); bar.addView(a); } parent.addView(bar); }

    private void library() {
        screen="library"; shell(false); header(root,"Tài liệu","⚙  Gemini",this::settings);
        LinearLayout intro=column(); pad(intro,22,0); intro.addView(text("Quét, lưu và hỏi đáp tài liệu của bạn",13,MUTED,false)); gap(intro,17);
        EditText find=new EditText(this); find.setSingleLine(true); find.setTextSize(14); find.setHint("⌕   Tìm trong tài liệu"); find.setBackground(stroke(Color.WHITE,0xffe2e6ed,11)); pad(find,14,8); find.setText(search); intro.addView(find,lp(-1,48)); gap(intro,24); intro.addView(text("GẦN ĐÂY",11,MUTED,true)); gap(intro,10); root.addView(intro);
        ScrollView scroll=new ScrollView(this); content=column(); pad(content,22,0); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); renderList();
        find.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int a,int c,int f){} public void onTextChanged(CharSequence s,int st,int before,int count){ search=s.toString(); renderList(); } public void afterTextChanged(Editable e){} });
        LinearLayout bottom=row(); bottom.setBackgroundColor(Color.WHITE); pad(bottom,16,12); TextView pdf=button("▤  Nhập PDF",false,this::pickPdf); TextView scan=button("▣  Quét tài liệu",true,this::scan); LinearLayout.LayoutParams first=new LinearLayout.LayoutParams(0,dp(50),1); first.rightMargin=dp(8); bottom.addView(pdf,first); bottom.addView(scan,new LinearLayout.LayoutParams(0,dp(50),1.25f)); root.addView(bottom);
    }
    private void renderList() { if(content==null)return; content.removeAllViews(); List<DocStore.Doc> docs=store.all(search); if(docs.isEmpty()) { gap(content,72); TextView empty=text(search.isEmpty()?"Chưa có tài liệu\n\nChụp trang giấy hoặc nhập PDF để bắt đầu.":"Không tìm thấy tài liệu phù hợp.",16,MUTED,false); empty.setGravity(Gravity.CENTER); content.addView(empty,lp(-1,140)); return; }
        for(DocStore.Doc d:docs) { LinearLayout card=row(); pad(card,12,13); card.setBackground(stroke(Color.WHITE,0xffe5e8ef,11)); TextView icon=text("▤",28,0xffa6b1c4,true); icon.setGravity(Gravity.CENTER); icon.setBackground(stroke(0xfff8f9fb,0xffe5e8ef,5)); card.addView(icon,lp(39,49)); LinearLayout body=column(); LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(0,-2,1); bodyLp.leftMargin=dp(12); card.addView(body,bodyLp); body.addView(text(d.title,14,INK,true)); body.addView(text(d.pages+" trang · "+date(d.created)+"   "+("PDF".equals(d.source)?"PDF":"ĐÃ OCR"),11,MUTED,false)); card.addView(text("›",25,INK,false)); card.setOnClickListener(v->detail(d.id)); LinearLayout.LayoutParams p=lp(-1,-2); p.bottomMargin=dp(8); content.addView(card,p); }
    }
    private void settings() { EditText input=new EditText(this); input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); input.setHint("Gemini API key"); input.setText(apiKey); LinearLayout box=column(); pad(box,20,10); box.addView(text("Nhập key từ Google AI Studio. Key chỉ giữ trong phiên mở app. Tài liệu gửi tới Gemini khi bạn dùng tóm tắt hoặc hỏi đáp.",13,MUTED,false)); gap(box,14); box.addView(input); new AlertDialog.Builder(this).setTitle("Cài đặt Gemini").setView(box).setPositiveButton("Lưu",(d,w)->{apiKey=input.getText().toString().trim();toast("Đã lưu key cho phiên này.");}).setNegativeButton("Hủy",null).show(); }
    private void scan() { screen="scan"; shell(true); LinearLayout top=row(); pad(top,22,18); TextView close=text("✕",22,Color.WHITE,true); close.setOnClickListener(v->library()); top.addView(close,lp(45,42)); TextView page=text("Quét tài liệu",16,Color.WHITE,true); page.setGravity(Gravity.CENTER); top.addView(page,new LinearLayout.LayoutParams(0,dp(42),1)); root.addView(top);
        TextView hint=text(scans.isEmpty()?"Đưa tài liệu vào khung và chụp từng trang":"Đã chụp "+scans.size()+" trang · Chụp tiếp hoặc hoàn tất",13,0xffcbd2df,false); hint.setGravity(Gravity.CENTER); root.addView(hint); gap(root,16);
        LinearLayout frame=column(); frame.setGravity(Gravity.CENTER); frame.setBackground(stroke(0xff191d25,0xff3f5be7,4)); LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,0,1); fp.leftMargin=dp(24); fp.rightMargin=dp(24); root.addView(frame,fp);
        if(!scans.isEmpty()) { ImageView image=new ImageView(this); image.setImageURI(Uri.fromFile(scans.get(scans.size()-1))); image.setScaleType(ImageView.ScaleType.FIT_CENTER); frame.addView(image,lp(-1,-1)); }
        else { TextView placeholder=text("▤\n\nTrang tài liệu sẽ xuất hiện ở đây",18,0xffaeb8cc,false); placeholder.setGravity(Gravity.CENTER); frame.addView(placeholder); }
        gap(root,20); LinearLayout actions=row(); pad(actions,22,4); TextView count=text(scans.size()+" trang",14,Color.WHITE,true); actions.addView(count,new LinearLayout.LayoutParams(0,dp(56),1)); TextView shutter=button("●  Chụp",false,this::takePhoto); actions.addView(shutter,lp(112,52)); TextView finish=button("✓  Xong",true,this::finishScan); LinearLayout.LayoutParams done=lp(104,52); done.leftMargin=dp(10); actions.addView(finish,done); root.addView(actions); TextView warning=text("Không quét giấy tờ nhạy cảm. Chỉ gửi tài liệu tới Gemini khi bạn chọn dùng AI.",11,0xffaeb8cc,false);warning.setGravity(Gravity.CENTER);pad(warning,22,8);root.addView(warning);gap(root,12);
    }
    private void takePhoto() { try { File dir=new File(getFilesDir(),"images"); if(!dir.exists()&&!dir.mkdirs()) throw new Exception("Không tạo được thư mục ảnh."); pendingPhoto=new File(dir,"scan_"+System.currentTimeMillis()+".jpg"); Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",pendingPhoto); Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE); intent.putExtra(MediaStore.EXTRA_OUTPUT,uri); intent.setClipData(ClipData.newRawUri("scan",uri)); intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivityForResult(intent,CAMERA); } catch(Exception e){ error(e); } }
    private void pickPdf() { Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("application/pdf"); intent.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(intent,PDF); }
    private void finishScan() { if(scans.isEmpty()){toast("Hãy chụp ít nhất một trang.");return;} List<File> files=new ArrayList<>(scans); async("Đang nhận diện chữ…",()->{List<DocStore.Page> pages=new ArrayList<>(); for(File f:files)pages.add(processor.photo(f,pages.size()+1)); return store.add("Tài liệu quét "+timestamp(),"SCAN",pages);},id->{scans.clear();detail(id);}); }
    @Override protected void onActivityResult(int request,int result,Intent data) { super.onActivityResult(request,result,data); if(result!=RESULT_OK)return;
        if(request==CAMERA) { if(pendingPhoto!=null&&pendingPhoto.length()>0) { scans.add(pendingPhoto); scan(); } else toast("Không nhận được ảnh từ camera."); }
        if(request==PDF&&data!=null&&data.getData()!=null) { Uri uri=data.getData(); String title="PDF "+timestamp(); try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) { if(c!=null&&c.moveToFirst()) title=c.getString(0).replaceFirst("(?i)\\.pdf$",""); } catch(Exception ignored) {} final String name=title; async("Đang đọc PDF và OCR…",()->store.add(name,"PDF",processor.pdf(uri,(now,total)->{})),this::detail); }
        if(request==EXPORT&&data!=null&&data.getData()!=null&&current!=null) { Uri uri=data.getData(); try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)) { if(out==null)throw new Exception("Không mở được tệp đích."); out.write(current.text.getBytes(StandardCharsets.UTF_8)); toast("Đã xuất TXT."); } catch(Exception e){error(e);} }
    }
    private void detail(long id) { if(currentId!=id){lastQuestion="";lastAnswer="";lastSources="";askTab=false;} currentId=id; current=store.get(id); if(current==null){library();return;} screen="detail"; shell(false);
        LinearLayout top=row(); pad(top,18,12); TextView back=text("‹",28,INK,false); back.setOnClickListener(v->library()); top.addView(back,lp(36,42)); TextView title=text(current.title,15,INK,true); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END); top.addView(title,new LinearLayout.LayoutParams(0,dp(42),1)); TextView menu=text("⋮",25,INK,true); menu.setGravity(Gravity.CENTER); menu.setOnClickListener(v->documentMenu()); top.addView(menu,lp(34,42)); root.addView(top);
        LinearLayout tabs=row(); pad(tabs,14,3); tabs.setBackground(shape(0xffe8eaef,9)); TextView contentTab=text("Nội dung",13,askTab?MUTED:INK,!askTab); TextView ask=text("Hỏi đáp",13,askTab?INK:MUTED,askTab); contentTab.setGravity(Gravity.CENTER);ask.setGravity(Gravity.CENTER); contentTab.setBackground(askTab?null:shape(Color.WHITE,7));ask.setBackground(askTab?shape(Color.WHITE,7):null); tabs.addView(contentTab,new LinearLayout.LayoutParams(0,dp(38),1)); tabs.addView(ask,new LinearLayout.LayoutParams(0,dp(38),1)); contentTab.setOnClickListener(v->{askTab=false;detail(currentId);}); ask.setOnClickListener(v->{askTab=true;detail(currentId);}); LinearLayout.LayoutParams tabLp=lp(-1,44); tabLp.leftMargin=dp(18);tabLp.rightMargin=dp(18);root.addView(tabs,tabLp);
        ScrollView sc=new ScrollView(this); content=column();pad(content,18,16);sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1)); if(askTab)renderAsk();else renderContent();
        if(askTab) { LinearLayout composer=row();composer.setBackgroundColor(Color.WHITE);pad(composer,12,10); EditText question=new EditText(this); question.setSingleLine(true);question.setTextSize(14);question.setHint("Hỏi về tài liệu này");question.setBackground(stroke(BG,0xffdfe3eb,24));pad(question,14,7);composer.addView(question,new LinearLayout.LayoutParams(0,dp(46),1));TextView send=button("➤",true,()->{String q=question.getText().toString().trim();if(!q.isEmpty())ask(q);});LinearLayout.LayoutParams sp=lp(46,46);sp.leftMargin=dp(8);composer.addView(send,sp);root.addView(composer); }
        else { LinearLayout bottom=row();bottom.setBackgroundColor(Color.WHITE);pad(bottom,16,10);TextView copy=button("Sao chép",false,()->copy(current.text));TextView export=button("Xuất TXT",true,this::exportTxt);bottom.addView(copy,new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,dp(46),1);xp.leftMargin=dp(8);bottom.addView(export,xp);root.addView(bottom); }
    }
    private void renderContent() { content.addView(text(current.source.equals("PDF")?"TÀI LIỆU PDF":"TÀI LIỆU ĐÃ QUÉT",11,BLUE,true));gap(content,12);List<DocStore.Page> pages=store.pages(currentId);
        for(DocStore.Page p:pages) { LinearLayout card=column();pad(card,15,14);card.setBackground(stroke(Color.WHITE,0xffe8eaf0,11));LinearLayout head=row();TextView name=text("Trang "+p.number,14,INK,true);head.addView(name,new LinearLayout.LayoutParams(0,dp(32),1));TextView edit=text("Sửa OCR",13,BLUE,true);edit.setOnClickListener(v->editPage(p));head.addView(edit);card.addView(head);gap(card,7);TextView body=text(p.text.isEmpty()?"Chưa nhận diện được chữ. Bạn có thể sửa OCR để nhập nội dung.":p.text,14,p.text.isEmpty()?MUTED:INK,false);body.setLineSpacing(dp(3),1f);card.addView(body);LinearLayout.LayoutParams cp=lp(-1,-2);cp.bottomMargin=dp(10);content.addView(card,cp); }
    }
    private String cleanAiText(String value) { return value.replace("**","").replace("__","").replaceAll("(?m)^\\s{0,3}#{1,6}\\s*","").replaceAll("(?m)^\\s*[-*]\\s+","• ").trim(); }
    private void renderAsk() { LinearLayout summary=column();pad(summary,13,12);summary.setBackground(stroke(Color.WHITE,0xffe7e9ee,11));summary.addView(text("TÓM TẮT",11,BLUE,true));TextView sum=button(current.summary.isEmpty()?"✧  Tóm tắt":"Tóm tắt lại",false,this::summarize);LinearLayout.LayoutParams b=lp(-1,43);b.topMargin=dp(9);b.bottomMargin=dp(10);summary.addView(sum,b);summary.addView(text(current.summary.isEmpty()?"Bấm Tóm tắt để AI đọc nội dung tài liệu.":cleanAiText(current.summary),13,INK,false));content.addView(summary);gap(content,16);
        if(!lastQuestion.isEmpty()) {TextView q=text(lastQuestion,14,Color.WHITE,false);pad(q,12,10);q.setBackground(shape(BLUE,12));LinearLayout.LayoutParams ql=lp(-2,-2);ql.gravity=Gravity.RIGHT;content.addView(q,ql);gap(content,10);TextView a=text(cleanAiText(lastAnswer),14,INK,false);pad(a,12,12);a.setBackground(stroke(Color.WHITE,0xffe7e9ee,11));content.addView(a,lp(-1,-2));if(!lastSources.isEmpty()){gap(content,8);TextView source=text("ĐOẠN ĐƯỢC DÙNG\n"+lastSources,11,MUTED,false);pad(source,12,10);source.setBackground(stroke(Color.WHITE,0xffe7e9ee,11));content.addView(source);} }
        else { TextView tip=text("Hỏi một câu cụ thể. Câu trả lời sẽ kèm đoạn trích từ tài liệu.",13,MUTED,false);content.addView(tip); }
    }
    private void editPage(DocStore.Page p) { EditText editor=new EditText(this);editor.setText(p.text);editor.setTextSize(15);editor.setGravity(Gravity.TOP);editor.setMinLines(12);editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);ScrollView scroll=new ScrollView(this);pad(scroll,14,4);scroll.addView(editor);new AlertDialog.Builder(this).setTitle("Sửa OCR · Trang "+p.number).setView(scroll).setPositiveButton("Lưu",(d,w)->{store.updatePage(currentId,p.number,editor.getText().toString());detail(currentId);}).setNegativeButton("Hủy",null).show().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE); }
    private void documentMenu() { String[] options={"Đổi tên","Xóa tài liệu","Cài đặt Gemini"};new AlertDialog.Builder(this).setTitle(current.title).setItems(options,(d,which)->{if(which==0)rename();if(which==1)deleteDoc();if(which==2)settings();}).show(); }
    private void rename() { EditText input=new EditText(this);input.setSingleLine(true);input.setText(current.title);new AlertDialog.Builder(this).setTitle("Đổi tên tài liệu").setView(input).setPositiveButton("Lưu",(d,w)->{String title=input.getText().toString().trim();if(!title.isEmpty()){store.updateTitle(currentId,title);detail(currentId);}}).setNegativeButton("Hủy",null).show(); }
    private void deleteDoc() { new AlertDialog.Builder(this).setTitle("Xóa tài liệu?").setMessage("Văn bản và ảnh đã lưu của tài liệu này sẽ bị xóa.").setPositiveButton("Xóa",(d,w)->{for(DocStore.Page p:store.pages(currentId))if(!p.image.isEmpty())new File(p.image).delete();store.delete(currentId);lastQuestion="";lastAnswer="";library();}).setNegativeButton("Hủy",null).show(); }
    private void summarize() { List<DocStore.Page> pages=store.pages(currentId);async("Đang tóm tắt…",()->gemini.summarize(apiKey,pages),result->{store.updateSummary(currentId,result);detail(currentId);}); }
    private void ask(String question) { List<DocStore.Page> pages=store.pages(currentId);async("Đang tìm câu trả lời…",()->gemini.answer(apiKey,pages,question),result->{lastQuestion=question;lastAnswer=result.text;lastSources=result.sources;detail(currentId);}); }
    private void copy(String value) { ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("DocuAsk",value));toast("Đã sao chép văn bản."); }
    private void exportTxt() { Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.setType("text/plain");intent.addCategory(Intent.CATEGORY_OPENABLE);intent.putExtra(Intent.EXTRA_TITLE,current.title.replaceAll("[^a-zA-Z0-9À-ỹ _-]","")+".txt");startActivityForResult(intent,EXPORT); }
    @Override public void onBackPressed() { if("detail".equals(screen)||"scan".equals(screen)) library(); else super.onBackPressed(); }
}
