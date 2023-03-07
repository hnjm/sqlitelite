/*
 * Copyright 2016 requery.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.requery.android.database.benchmark;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;
import android.util.Log;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import io.requery.android.database.sqlite.SQLiteCursor;
import io.requery.android.database.sqlite.SQLitePreparedStatement;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class BenchmarkTest {
    private static final String TAG = "SQLite";

    private PlatformSQLite platformSQLite;
    private RequerySQLite requerySQLite;

    @Test
    public void runBenchmark() {
        final int runs = 5;
        final ArrayList<String> logs = new ArrayList<>();
        for (int count : new int[]{100, 1000, 10_000, 100_000}) {
            Statistics android = new Statistics();
            Statistics requery = new Statistics();
            for (int i = 0; i < runs; i++) {
                Context context = ApplicationProvider.getApplicationContext();
                String dbName = "testAndroid.db";
                context.deleteDatabase(dbName);
                platformSQLite = new PlatformSQLite(context, dbName);
                dbName = "testRequery.db";
                context.deleteDatabase(dbName);
                requerySQLite = new RequerySQLite(context, dbName);

                testAndroidSQLiteRead(android, count);
                testRequerySQLiteRead(requery, count);

                if (platformSQLite != null) {
                    platformSQLite.close();
                }
                if (requerySQLite != null) {
                    requerySQLite.close();
                }
            }
            logs.add("Android (" + count + "): " + android.toString(count));
            logs.add("requery (" + count + "): " + requery.toString(count));
        }
        for (String log : logs) {
            Log.i(TAG, log);
        }
    }

    private void testAndroidSQLiteRead(Statistics statistics, int count) {
        testAndroidSQLiteWrite(statistics, count);
        Trace trace = new Trace("Android Read");
        Cursor cursor = null;
        try {
            SQLiteDatabase db = platformSQLite.getReadableDatabase();
            String[] projection = new String[] {
                Record.COLUMN_ID, Record.COLUMN_CONTENT, Record.COLUMN_CREATED_TIME, };
            cursor = db.query(Record.TABLE_NAME, projection, null, null, null, null, null);

            if (cursor != null) {
                int indexId = cursor.getColumnIndexOrThrow(Record.COLUMN_ID);
                int indexContent = cursor.getColumnIndexOrThrow(Record.COLUMN_CONTENT);
                int indexCreatedTime = cursor.getColumnIndexOrThrow(Record.COLUMN_CREATED_TIME);
                while (cursor.moveToNext()) {
                    Record record = new Record();
                    record.setId(cursor.getLong(indexId));
                    record.setContent(cursor.getString(indexContent));
                    record.setCreatedTime(cursor.getLong(indexCreatedTime));
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        statistics.read( trace.exit() );
    }

    private void testAndroidSQLiteWrite(Statistics statistics, int count) {
        Trace trace = new Trace("Android Write");
        SQLiteDatabase db = platformSQLite.getReadableDatabase();
        SQLiteStatement statement = db.compileStatement(
            String.format("insert into %s (%s, %s) values (?,?)",
                Record.TABLE_NAME,
                Record.COLUMN_CONTENT,
                Record.COLUMN_CREATED_TIME));
        try {
            db.beginTransaction();
            for (int i = 0; i < count; i++) {
                Record record = Record.create(i);
                statement.bindString(1, record.getContent());
                statement.bindDouble(2, record.getCreatedTime());
                long id = statement.executeInsert();
                record.setId(id);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        statistics.write( trace.exit() );
    }

    private void testRequerySQLiteWrite(Statistics statistics, int count) {
        Trace trace = new Trace("requery Write");
        io.requery.android.database.sqlite.SQLiteDatabase db = requerySQLite.getWritableDatabase();
        try (SQLitePreparedStatement statement = db.compileStatement(
                String.format("insert into %s (%s, %s) values (?,?)",
                        Record.TABLE_NAME,
                        Record.COLUMN_CONTENT,
                        Record.COLUMN_CREATED_TIME))) {
            try {
                db.beginTransactionExclusive();
                for (int i = 0; i < count; i++) {
                    Record record = Record.create(i);
                    statement.bindString(1, record.getContent());
                    statement.bindDouble(2, record.getCreatedTime());
                    long id = statement.executeInsert();
                    record.setId(id);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
        statistics.write( trace.exit() );
    }

    private void testRequerySQLiteRead(Statistics statistics, int count) {
        testRequerySQLiteWrite(statistics, count);
        Trace trace = new Trace("requery Read");
        SQLiteCursor cursor = null;
        try {
            io.requery.android.database.sqlite.SQLiteDatabase db = requerySQLite.getWritableDatabase();
            cursor = db.query("SELECT "+Record.COLUMN_ID+", "+Record.COLUMN_CONTENT+", "+Record.COLUMN_CREATED_TIME +" FROM "+Record.TABLE_NAME);
            if(cursor != null) {
                int indexId = 0;
                int indexContent = 1;
                int indexCreatedTime = 2;
                while (cursor.moveToNext()) {
                    Record record = new Record();
                    record.setId(cursor.getLong(indexId));
                    record.setContent(cursor.getString(indexContent));
                    record.setCreatedTime(cursor.getLong(indexCreatedTime));
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        statistics.read( trace.exit() );
    }

    private static class Record {

        private final static String CREATE_STATEMENT =
            "CREATE TABLE '" + Record.TABLE_NAME +
                "' ('" + BaseColumns._ID +
                "' INTEGER PRIMARY KEY AUTOINCREMENT, '" +
                Record.COLUMN_CONTENT + "' TEXT, '" +
                Record.COLUMN_CREATED_TIME + "' INTEGER);";

        static final String TABLE_NAME = "record";

        static final String COLUMN_ID = BaseColumns._ID;

        static final String COLUMN_CONTENT = "content";

        static final String COLUMN_CREATED_TIME = "created";

        static Record create(int index) {
            Record record = new Record();
            record.setContent("position" + String.valueOf(index));
            record.setCreatedTime(System.currentTimeMillis());
            return record;
        }

        private long id;

        private String content;

        private long createdTime;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public long getCreatedTime() {
            return createdTime;
        }

        public void setCreatedTime(long createdTime) {
            this.createdTime = createdTime;
        }
    }

    private static class PlatformSQLite extends SQLiteOpenHelper {
        public PlatformSQLite(Context context, String name) {
            super(context, name, null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(Record.CREATE_STATEMENT);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            throw new UnsupportedOperationException();
        }
    }

    private static class RequerySQLite extends
        io.requery.android.database.sqlite.SQLiteOpenHelper {
        public RequerySQLite(Context context, String name) {
            super(context, name, 1);
        }

        @Override
        public void onCreate(io.requery.android.database.sqlite.SQLiteDatabase db) {
            db.execSQL(Record.CREATE_STATEMENT);
        }

        @Override
        public void onUpgrade(io.requery.android.database.sqlite.SQLiteDatabase db,
                              int oldVersion, int newVersion) {
            throw new UnsupportedOperationException();
        }
    }

    private static class Statistics {
        private long readsSum = 0;
        private int readsCount = 0;
        private long writesSum = 0;
        private int writesCount = 0;

        void read(long elapsedMS) {
            readsSum += elapsedMS;
            readsCount++;
        }

        void write(long elapsedMS) {
            writesSum += elapsedMS;
            writesCount++;
        }

        float readAverageMS() {
            return readsSum / (float) readsCount;
        }

        float writeAverageMS() {
            return writesSum / (float) writesCount;
        }

        public String toString(int count) {
            return "Read AVG " + readAverageMS() +
                " Write AVG " + writeAverageMS() + "\n" +
                " Rows/sec " + count / readAverageMS() * 1000f +
                " Inserts/sec " + count / writeAverageMS() * 1000f;
        }
    }

    private static class Trace {
        private String source;
        private long start;

        public Trace(String source) {
            this.source = source;
            enter();
        }

        public void enter() {
            //Log.i(TAG, "enter " + source);
            start = System.nanoTime();
        }

        public long exit() {
            //Log.i(TAG, "exit " + source);
            long stop = System.nanoTime();
            long elapsed = stop - start;
            long elapsedMS = TimeUnit.NANOSECONDS.toMillis(elapsed);
            Log.i(TAG, source + " " + elapsedMS + "ms");
            return elapsedMS;
        }
    }
}
