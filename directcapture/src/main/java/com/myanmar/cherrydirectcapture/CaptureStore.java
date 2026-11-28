package com.myanmar.cherrydirectcapture;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class CaptureStore extends SQLiteOpenHelper {
    CaptureStore(Context c) { super(c.getApplicationContext(), "capture_test.db", null, 2); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE captures(id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT NOT NULL, captured INTEGER NOT NULL, payload TEXT NOT NULL)");
        createReviewTables(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion==1 && newVersion==2) {createReviewTables(db);return;}
        throw new IllegalStateException("Unexpected database version");
    }
    long save(String pkg, JSONArray nodes) {
        ContentValues v=new ContentValues();
        v.put("pkg",pkg); v.put("captured",System.currentTimeMillis()); v.put("payload",nodes.toString());
        return getWritableDatabase().insertOrThrow("captures",null,v);
    }
    int count() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM captures",null)) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    JSONArray page(int offset) throws JSONException {
        JSONArray result=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,pkg,captured,payload FROM captures ORDER BY id DESC LIMIT 5 OFFSET ?",new String[]{String.valueOf(offset)})) {
            while(c.moveToNext()) {
                JSONObject x=new JSONObject();
                x.put("id",c.getLong(0)); x.put("package",c.getString(1)); x.put("captured_ms",c.getLong(2));
                x.put("nodes",new JSONArray(c.getString(3))); result.put(x);
            }
        }
        return result;
    }
    private void createReviewTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE capture_labels(capture_id INTEGER PRIMARY KEY, chat TEXT NOT NULL)");
        db.execSQL("CREATE TABLE review_items(capture_id INTEGER NOT NULL, node_index INTEGER NOT NULL, original TEXT NOT NULL, edited TEXT NOT NULL, stamp TEXT NOT NULL, selected INTEGER NOT NULL, transferred INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(capture_id,node_index))");
        db.execSQL("CREATE TABLE test_draft(id INTEGER PRIMARY KEY, body TEXT NOT NULL)");
        db.execSQL("INSERT INTO test_draft(id,body) VALUES(1,'')");
    }
    String chat(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT chat FROM capture_labels WHERE capture_id=?",new String[]{""+id})) {
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    void saveChat(long id,String chat) {
        ContentValues v=new ContentValues();v.put("capture_id",id);v.put("chat",chat);
        if(getWritableDatabase().insertWithOnConflict("capture_labels",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0) throw new IllegalStateException("Label write failed");
    }
    static final class Decision {
        String edited,stamp; boolean selected,transferred;
        Decision(String edited,String stamp,boolean selected,boolean transferred) {
            this.edited=edited;this.stamp=stamp;this.selected=selected;this.transferred=transferred;
        }
    }
    Decision decision(long id,int index,String original) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT edited,stamp,selected,transferred FROM review_items WHERE capture_id=? AND node_index=?",new String[]{""+id,""+index})) {
            return c.moveToFirst()?new Decision(c.getString(0),c.getString(1),c.getInt(2)!=0,c.getInt(3)!=0):new Decision(original,"",false,false);
        }
    }
    void saveDecision(long id,int index,String original,Decision d) {
        if(d.transferred) return;
        ContentValues v=new ContentValues();v.put("capture_id",id);v.put("node_index",index);
        v.put("original",original);v.put("edited",d.edited);v.put("stamp",d.stamp);
        v.put("selected",d.selected?1:0);v.put("transferred",0);
        if(getWritableDatabase().insertWithOnConflict("review_items",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0) throw new IllegalStateException("Review write failed");
    }
    JSONObject previous(long id,String pkg,String chat) throws JSONException {
        if(chat.trim().isEmpty()) return null;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT c.id,c.payload FROM captures c JOIN capture_labels l ON l.capture_id=c.id WHERE c.id<? AND c.pkg=? AND l.chat=? ORDER BY c.id DESC LIMIT 1",new String[]{""+id,pkg,chat})) {
            if(!c.moveToFirst()) return null;
            JSONObject x=new JSONObject();x.put("id",c.getLong(0));x.put("nodes",new JSONArray(c.getString(1)));return x;
        }
    }
    int selectedCount() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM review_items WHERE selected=1 AND transferred=0",null)) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    String draft() {return draft(getReadableDatabase());}
    private String draft(SQLiteDatabase db) {
        try(Cursor c=db.rawQuery("SELECT body FROM test_draft WHERE id=1",null)) {
            if(!c.moveToFirst()) throw new IllegalStateException("Missing draft");
            return c.getString(0);
        }
    }
    void saveDraft(String body) {
        ContentValues v=new ContentValues();v.put("body",body);
        if(getWritableDatabase().update("test_draft",v,"id=1",null)!=1) throw new IllegalStateException("Draft write failed");
    }
    int transfer() {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            java.util.List<String> texts=new java.util.ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT edited FROM review_items WHERE selected=1 AND transferred=0 ORDER BY capture_id,node_index",null)) {
                while(c.moveToNext()) texts.add(c.getString(0));
            }
            if(texts.isEmpty()) {db.setTransactionSuccessful();return 0;}
            String body=InboxLogic.append(draft(db),texts);
            if(body.length()>250000) throw new IllegalStateException("Draft size limit");
            ContentValues v=new ContentValues();v.put("body",body);
            if(db.update("test_draft",v,"id=1",null)!=1) throw new IllegalStateException("Draft write failed");
            v.clear();v.put("transferred",1);
            if(db.update("review_items",v,"selected=1 AND transferred=0",null)!=texts.size()) throw new IllegalStateException("Transfer count mismatch");
            db.setTransactionSuccessful();return texts.size();
        } finally {db.endTransaction();}
    }
    void clear() {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            db.delete("review_items",null,null);db.delete("capture_labels",null,null);db.delete("captures",null,null);
            ContentValues v=new ContentValues();v.put("body","");db.update("test_draft",v,"id=1",null);
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
}
