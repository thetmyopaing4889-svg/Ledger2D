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
    CaptureStore(Context c) { super(c.getApplicationContext(), "capture_test.db", null, 4); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE captures(id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT NOT NULL, captured INTEGER NOT NULL, payload TEXT NOT NULL)");
        createReviewTables(db);
        createBatchTables(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<1 || oldVersion>3 || newVersion!=4) throw new IllegalStateException("Unexpected database version");
        if(oldVersion==1) createReviewTables(db);
        if(oldVersion<3) createBatchTables(db);
        else db.execSQL("ALTER TABLE capture_batches ADD COLUMN chat TEXT NOT NULL DEFAULT ''");
    }
    private void createBatchTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE capture_batches(id TEXT PRIMARY KEY, pkg TEXT NOT NULL, state INTEGER NOT NULL, review TEXT, chat TEXT NOT NULL DEFAULT '' )");
        db.execSQL("CREATE TABLE batch_captures(batch_id TEXT NOT NULL, capture_id INTEGER PRIMARY KEY)");
        db.execSQL("CREATE TABLE structured_drafts(id INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL, batch_id TEXT, provenance TEXT NOT NULL)");
    }
    String beginBatch(String pkg) {
        String id=java.util.UUID.randomUUID().toString();
        SQLiteDatabase db=getWritableDatabase();
        ContentValues stopped=new ContentValues(); stopped.put("state",1);
        db.update("capture_batches",stopped,"state=0",null);
        ContentValues v=new ContentValues();v.put("id",id);v.put("pkg",pkg);v.put("state",0);
        db.insertOrThrow("capture_batches",null,v);return id;
    }
    void stopBatch(String id) {
        ContentValues v=new ContentValues();v.put("state",1);
        getWritableDatabase().update("capture_batches",v,"id=? AND state=0",new String[]{id});
    }
    String latestBatch() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM capture_batches ORDER BY rowid DESC LIMIT 1",null)){
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    int batchState(String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT state FROM capture_batches WHERE id=?",new String[]{id})){
            return c.moveToFirst()?c.getInt(0):-1;
        }
    }
    String batchPackage(String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT pkg FROM capture_batches WHERE id=?",new String[]{id})){
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    String batchChat(String id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT chat FROM capture_batches WHERE id=?",new String[]{id})){
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    void saveBatchChat(String id,String name){
        String value=name.trim();if(value.isEmpty()||value.length()>200)throw new IllegalArgumentException("Chat label");
        ContentValues v=new ContentValues();v.put("chat",value);
        if(getWritableDatabase().update("capture_batches",v,"id=? AND state=1",new String[]{id})!=1)throw new IllegalStateException("Review unavailable");
    }
    long saveBatchCapture(String batch,String pkg,JSONArray nodes) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            if(batchState(batch)!=0 || (!batchPackage(batch).isEmpty()&&!batchPackage(batch).equals(pkg))) throw new IllegalStateException("Inactive batch");
            long id=save(pkg,nodes);
            ContentValues v=new ContentValues();v.put("batch_id",batch);v.put("capture_id",id);
            db.insertOrThrow("batch_captures",null,v);
            db.setTransactionSuccessful();return id;
        } finally {db.endTransaction();}
    }
    JSONArray batchRaw(String id) throws JSONException {
        JSONArray out=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT c.id,c.payload,c.captured,c.pkg FROM captures c JOIN batch_captures b ON b.capture_id=c.id WHERE b.batch_id=? ORDER BY c.id",new String[]{id})){
            while(c.moveToNext()) {
                JSONObject row=new JSONObject();row.put("capture",c.getLong(0));row.put("nodes",new JSONArray(c.getString(1)));row.put("captured_ms",c.getLong(2));row.put("package",c.getString(3));out.put(row);
            }
        }
        return out;
    }
    String suggestedChat(String id) throws JSONException {return ViberReview.suggestedChat(batchRaw(id));}
    boolean mixedChats(String id) throws JSONException {return ViberReview.mixedChats(batchRaw(id));}
    JSONArray review(String id) throws JSONException {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT review FROM capture_batches WHERE id=?",new String[]{id})){
            if(!c.moveToFirst()) throw new IllegalStateException("Unknown batch");
            if(!c.isNull(0)) return new JSONArray(c.getString(0));
        }
        JSONArray rows=BatchCandidates.build(batchRaw(id));
        saveReview(id,rows);return rows;
    }
    void saveReview(String id,JSONArray rows) {
        if(rows.toString().length()>1000000) throw new IllegalArgumentException("Review size");
        ContentValues v=new ContentValues();v.put("review",rows.toString());
        if(getWritableDatabase().update("capture_batches",v,"id=? AND state=1",new String[]{id})!=1) throw new IllegalStateException("Review unavailable");
    }
    void discardBatch(String id) {
        ContentValues v=new ContentValues();v.put("state",3);
        getWritableDatabase().update("capture_batches",v,"id=? AND state=1",new String[]{id});
    }
    JSONArray entries() throws JSONException {
        JSONArray out=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,body FROM structured_drafts ORDER BY id",null)){
            while(c.moveToNext()){JSONObject row=new JSONObject();row.put("id",c.getLong(0));row.put("body",c.getString(1));out.put(row);}
        }return out;
    }
    private void insertDraft(SQLiteDatabase db,String body,String batch,String provenance) {
        BatchLogic.requireBody(body);
        ContentValues v=new ContentValues();v.put("body",body);v.put("batch_id",batch);v.put("provenance",provenance);
        db.insertOrThrow("structured_drafts",null,v);
    }
    void addManual(String body){insertDraft(getWritableDatabase(),body,null,"manual");}
    void editDraft(long id,String body){BatchLogic.requireBody(body);ContentValues v=new ContentValues();v.put("body",body);
        if(getWritableDatabase().update("structured_drafts",v,"id=?",new String[]{""+id})!=1)throw new IllegalStateException("Missing draft");}
    void deleteDraft(long id){getWritableDatabase().delete("structured_drafts","id=?",new String[]{""+id});}
    int transferBatch(String id) throws JSONException {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            if(batchState(id)==2){db.setTransactionSuccessful();return 0;}
            if(batchState(id)!=1)throw new IllegalStateException("Batch not stopped");
            if(batchChat(id).isEmpty())throw new IllegalStateException("Chat not confirmed");
            if(mixedChats(id))throw new IllegalStateException("Mixed chat titles");
            JSONArray rows=review(id);
            java.util.List<String> bodies=new java.util.ArrayList<>();
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.optBoolean("include"))bodies.add(row.getString("body"));}
            BatchLogic.validate(bodies);
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.optBoolean("include"))insertDraft(db,row.getString("body"),id,row.toString());}
            ContentValues v=new ContentValues();v.put("state",2);
            if(db.update("capture_batches",v,"id=? AND state=1",new String[]{id})!=1)throw new IllegalStateException("Already transferred");
            db.setTransactionSuccessful();return bodies.size();
        } finally {db.endTransaction();}
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
            db.delete("structured_drafts",null,null);db.delete("batch_captures",null,null);db.delete("capture_batches",null,null);
            db.delete("review_items",null,null);db.delete("capture_labels",null,null);db.delete("captures",null,null);
            ContentValues v=new ContentValues();v.put("body","");db.update("test_draft",v,"id=1",null);
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
}
