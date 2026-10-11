package com.docuask.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class DocumentProcessor {
    private final Context context;
    DocumentProcessor(Context context) { this.context=context; }
    DocStore.Page photo(File file, int number) throws Exception {
        BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
        opts.inSampleSize=Math.max(1,Math.max(opts.outWidth,opts.outHeight)/2200); opts.inJustDecodeBounds=false;
        Bitmap image=BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
        if(image==null) throw new Exception("Không đọc được ảnh chụp.");
        try {
            Bitmap cropped=autoCrop(image);
            if(cropped!=image) {
                try(FileOutputStream out=new FileOutputStream(file)) { cropped.compress(Bitmap.CompressFormat.JPEG,90,out); }
                image.recycle(); image=cropped;
            }
            DocStore.Page page=new DocStore.Page(); page.number=number; page.image=file.getAbsolutePath(); page.text=recognize(image); return page;
        } finally { image.recycle(); }
    }
    List<DocStore.Page> pdf(Uri uri, Progress progress) throws Exception {
        List<DocStore.Page> result=new ArrayList<>();
        List<String> embedded=extractPdfText(uri);
        try(ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(uri,"r")) {
            if(fd==null) throw new Exception("Không mở được PDF.");
            try(PdfRenderer renderer=new PdfRenderer(fd)) {
                int total=renderer.getPageCount();
                if(total>50) throw new Exception("PDF có "+total+" trang. Giới hạn 50 trang mỗi lần nhập để tránh hết bộ nhớ.");
                for(int i=0;i<total;i++) {
                    progress.update(i+1,total);
                    try(PdfRenderer.Page page=renderer.openPage(i)) {
                        float scale=Math.min(3f,1800f/Math.max(page.getWidth(),page.getHeight()));
                        Bitmap bitmap=Bitmap.createBitmap(Math.max(1,(int)(page.getWidth()*scale)),Math.max(1,(int)(page.getHeight()*scale)),Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE); page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        DocStore.Page p=new DocStore.Page(); p.number=i+1;
                        String extracted=i<embedded.size()?embedded.get(i):"";
                        p.text=extracted.length()>=20?extracted:recognize(bitmap);
                        File dir=new File(context.getFilesDir(),"images"); if(!dir.exists()&&!dir.mkdirs()) throw new Exception("Không tạo được thư mục ảnh.");
                        File image=new File(dir,"pdf_"+System.currentTimeMillis()+"_"+i+".jpg");
                        try(FileOutputStream out=new FileOutputStream(image)) { bitmap.compress(Bitmap.CompressFormat.JPEG,88,out); }
                        p.image=image.getAbsolutePath(); result.add(p); bitmap.recycle();
                    }
                }
            }
        }
        return result;
    }
    private List<String> extractPdfText(Uri uri) {
        List<String> text=new ArrayList<>();
        try {
            PDFBoxResourceLoader.init(context.getApplicationContext());
            try(InputStream in=context.getContentResolver().openInputStream(uri); PDDocument doc=PDDocument.load(in)) {
                PDFTextStripper stripper=new PDFTextStripper(); stripper.setSortByPosition(true);
                for(int p=1;p<=Math.min(doc.getNumberOfPages(),50);p++) {
                    try { stripper.setStartPage(p); stripper.setEndPage(p); text.add(stripper.getText(doc).trim()); }
                    catch(Exception ignored) { text.add(""); }
                }
            }
        } catch(Exception ignored) { text.clear(); }
        return text;
    }
    interface Progress { void update(int current,int total); }
    private String recognize(Bitmap bitmap) throws Exception {
        try(TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)) {
            return Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0))).getText().trim();
        }
    }
    // Conservative page detection: crop only when the bright rectangle is clearly inside a dark frame.
    private Bitmap autoCrop(Bitmap src) {
        int width=src.getWidth(),height=src.getHeight();
        if(width<400||height<400) return src;
        int step=Math.max(4,Math.min(width,height)/150); int left=width,right=0,top=height,bottom=0,points=0;
        for(int y=0;y<height;y+=step) for(int x=0;x<width;x+=step) {
            int c=src.getPixel(x,y), l=(Color.red(c)*3+Color.green(c)*6+Color.blue(c))/10;
            if(l>185) { left=Math.min(left,x); right=Math.max(right,x); top=Math.min(top,y); bottom=Math.max(bottom,y); points++; }
        }
        float area=(right-left)*(bottom-top)/(float)(width*height);
        if(points<100||area<.35f||area>.92f||left<width*.025f||top<height*.025f||right>width*.975f||bottom>height*.975f) return src;
        int pad=Math.max(12,step*2); left=Math.max(0,left-pad); top=Math.max(0,top-pad); right=Math.min(width,right+pad); bottom=Math.min(height,bottom+pad);
        return Bitmap.createBitmap(src,left,top,right-left,bottom-top);
    }
}
