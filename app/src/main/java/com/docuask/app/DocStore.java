package com.docuask.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

final class DocStore extends SQLiteOpenHelper {
    static final class Doc {
        long id;
        String title, text, summary, source;
        long created;
        int pages;
    }
    static final class Page {
        int number;
        String text, image;
    }
    DocStore(Context context) { super(context, "docuask.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE documents(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,body TEXT NOT NULL DEFAULT '',summary TEXT NOT NULL DEFAULT '',source TEXT NOT NULL,created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE pages(id INTEGER PRIMARY KEY AUTOINCREMENT,document_id INTEGER NOT NULL,page_number INTEGER NOT NULL,body TEXT NOT NULL DEFAULT '',image_path TEXT NOT NULL DEFAULT '',FOREIGN KEY(document_id) REFERENCES documents(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX page_doc_idx ON pages(document_id,page_number)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    long add(String title, String source, List<Page> pages) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ContentValues v = new ContentValues(); v.put("title", title); v.put("source", source); v.put("created", System.currentTimeMillis());
            v.put("body", join(pages)); long id = db.insertOrThrow("documents", null, v);
            for (Page p : pages) {
                ContentValues pv = new ContentValues(); pv.put("document_id", id); pv.put("page_number", p.number);
                pv.put("body", p.text == null ? "" : p.text); pv.put("image_path", p.image == null ? "" : p.image);
                db.insertOrThrow("pages", null, pv);
            }
            db.setTransactionSuccessful(); return id;
        } finally { db.endTransaction(); }
    }
    static String join(List<Page> pages) {
        StringBuilder out = new StringBuilder();
        for (Page p : pages) { if (out.length() > 0) out.append("\n\n"); out.append("[Trang ").append(p.number).append("]\n").append(p.text == null ? "" : p.text); }
        return out.toString();
    }
    List<Doc> all(String query) {
        List<Doc> list = new ArrayList<>();
        String q = "%" + query.replace("%", "\\%").replace("_", "\\_") + "%";
        try (Cursor c = getReadableDatabase().rawQuery("SELECT d.id,d.title,d.body,d.summary,d.source,d.created,COUNT(p.id) FROM documents d LEFT JOIN pages p ON p.document_id=d.id WHERE d.title LIKE ? ESCAPE '\\' OR d.body LIKE ? ESCAPE '\\' GROUP BY d.id ORDER BY d.created DESC", new String[]{q,q})) {
            while (c.moveToNext()) { Doc d = read(c); d.pages = c.getInt(6); list.add(d); }
        }
        return list;
    }
    Doc get(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,title,body,summary,source,created FROM documents WHERE id=?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }
    private Doc read(Cursor c) {
        Doc d = new Doc(); d.id=c.getLong(0); d.title=c.getString(1); d.text=c.getString(2); d.summary=c.getString(3); d.source=c.getString(4); d.created=c.getLong(5); return d;
    }
    List<Page> pages(long id) {
        List<Page> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT page_number,body,image_path FROM pages WHERE document_id=? ORDER BY page_number", new String[]{String.valueOf(id)})) {
            while(c.moveToNext()) { Page p=new Page(); p.number=c.getInt(0); p.text=c.getString(1); p.image=c.getString(2); list.add(p); }
        }
        return list;
    }
    void updateTitle(long id, String title) { ContentValues v=new ContentValues(); v.put("title",title); getWritableDatabase().update("documents",v,"id=?",new String[]{String.valueOf(id)}); }
    void updateText(long id, String text) { ContentValues v=new ContentValues(); v.put("body",text); v.put("summary",""); getWritableDatabase().update("documents",v,"id=?",new String[]{String.valueOf(id)}); }
    void updatePage(long id, int page, String text) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            ContentValues pv=new ContentValues(); pv.put("body",text);
            db.update("pages",pv,"document_id=? AND page_number=?",new String[]{String.valueOf(id),String.valueOf(page)});
            ContentValues dv=new ContentValues(); dv.put("body",join(pages(id))); dv.put("summary","");
            db.update("documents",dv,"id=?",new String[]{String.valueOf(id)}); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    void updateSummary(long id, String summary) { ContentValues v=new ContentValues(); v.put("summary",summary); getWritableDatabase().update("documents",v,"id=?",new String[]{String.valueOf(id)}); }
    void delete(long id) { SQLiteDatabase db=getWritableDatabase(); db.beginTransaction(); try { db.delete("pages","document_id=?",new String[]{String.valueOf(id)}); db.delete("documents","id=?",new String[]{String.valueOf(id)}); db.setTransactionSuccessful(); } finally { db.endTransaction(); } }
}
