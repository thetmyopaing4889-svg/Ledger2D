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
    CaptureStore(Context c) { super(c.getApplicationContext(), "capture_test.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE captures(id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT NOT NULL, captured INTEGER NOT NULL, payload TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
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
    void clear() { getWritableDatabase().delete("captures",null,null); }
}
