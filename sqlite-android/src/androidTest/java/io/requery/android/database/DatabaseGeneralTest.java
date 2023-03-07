/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// modified from original source see README at the top level of this project

package io.requery.android.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.CharArrayBuffer;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteException;
import android.os.Parcel;
import android.util.Log;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;
import androidx.test.filters.Suppress;
import io.requery.android.database.sqlite.SQLiteCursor;
import io.requery.android.database.sqlite.SQLiteDatabase;
import io.requery.android.database.sqlite.SQLiteProgram;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SuppressWarnings({"deprecated", "ResultOfMethodCallIgnored", "deprecation"})
@RunWith(AndroidJUnit4.class)
public class DatabaseGeneralTest {
    private static final String TAG = "DatabaseGeneralTest";

    private static final String sString1 = "this is a test";
    private static final String sString2 = "and yet another test";
    private static final String sString3 = "this string is a little longer, but still a test";

    private static final int CURRENT_DATABASE_VERSION = 42;
    private SQLiteDatabase mDatabase;
    private File mDatabaseFile;

    @Before
    public void setUp() {
        File dbDir = ApplicationProvider.getApplicationContext().getDir(this.getClass().getName(), Context.MODE_PRIVATE);
        mDatabaseFile = new File(dbDir, "database_test.db");
        if (mDatabaseFile.exists()) {
            mDatabaseFile.delete();
        }
        mDatabase = SQLiteDatabase.openOrCreateDatabase(mDatabaseFile.getPath(), null);
        assertNotNull(mDatabase);
        mDatabase.setVersion(CURRENT_DATABASE_VERSION);
    }

    @After
    public void tearDown() {
        mDatabase.close();
        mDatabaseFile.delete();
    }

    public boolean isPerformanceOnly() {
        return false;
    }

    private void populateDefaultTable() {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, data TEXT);");

        mDatabase.execSQL("INSERT INTO test (data) VALUES ('" + sString1 + "');");
        mDatabase.execSQL("INSERT INTO test (data) VALUES ('" + sString2 + "');");
        mDatabase.execSQL("INSERT INTO test (data) VALUES ('" + sString3 + "');");
    }

    @MediumTest
    @Test
    public void testVersion() {
        assertEquals(CURRENT_DATABASE_VERSION, mDatabase.getVersion());
        mDatabase.setVersion(11);
        assertEquals(11, mDatabase.getVersion());
    }

    @MediumTest
    @Test
    public void testUpdate() {
        populateDefaultTable();

        ContentValues values = new ContentValues(1);
        values.put("data", "this is an updated test");
        assertEquals(1, mDatabase.update("test", values, "_id=1", null));
        SQLiteCursor c = mDatabase.query("SELECT data FROM test WHERE _id=1");
        assertNotNull(c);
        assertEquals(1, c.getCount());
        c.moveToFirst();
        String value = c.getString(0);
        assertEquals("this is an updated test", value);
    }

    @MediumTest
    @Test
    public void testSupportUpdate() {
        populateDefaultTable();

        ContentValues values = new ContentValues(1);
        values.put("data", "this is an updated test");
        assertEquals(1, mDatabase.update("test", SQLiteDatabase.CONFLICT_NONE, values,
                "_id=?", new Object[] { 1 }));
        SQLiteCursor c = mDatabase.query("SELECT data FROM test WHERE _id=1");
        assertNotNull(c);
        assertEquals(1, c.getCount());
        c.moveToFirst();
        String value = c.getString(0);
        assertEquals("this is an updated test", value);
    }

    @MediumTest
    @Test
    public void testSupportDelete() {
        populateDefaultTable();

        assertEquals(1, mDatabase.delete("test", "_id=?", new Object[] { 1 }));
        SQLiteCursor c = mDatabase.query("SELECT * FROM test WHERE _id=1");
        assertNotNull(c);
        assertEquals(0, c.getCount());
    }

    private void phoneNumberCompare(String phone1, String phone2, boolean equal, 
            boolean useStrictComparation) {
        String[] temporalPhoneNumbers = new String[2];
        temporalPhoneNumbers[0] = phone1;
        temporalPhoneNumbers[1] = phone2;

        SQLiteCursor cursor = mDatabase.rawQuery(
                String.format(Locale.ROOT,
                        "SELECT CASE WHEN PHONE_NUMBERS_EQUAL(?, ?, %d) " +
                        "THEN 'equal' ELSE 'not equal' END",
                        (useStrictComparation ? 1 : 0)),
                temporalPhoneNumbers);
        try {
            assertNotNull(cursor);
            assertTrue(cursor.moveToFirst());
            if (equal) {
                assertEquals(String.format("Unexpectedly, \"%s != %s\".", phone1, phone2),
                        "equal", cursor.getString(0));
            } else {
                assertEquals(String.format("Unexpectedly, \"%s\" == \"%s\".", phone1, phone2),
                        "not equal", cursor.getString(0));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void assertPhoneNumberEqual(String phone1, String phone2) {
        assertPhoneNumberEqual(phone1, phone2, true);
        assertPhoneNumberEqual(phone1, phone2, false);
    }
    
    private void assertPhoneNumberEqual(String phone1, String phone2, boolean useStrict) {
        phoneNumberCompare(phone1, phone2, true, useStrict);
    }

    private void assertPhoneNumberNotEqual(String phone1, String phone2) {
        assertPhoneNumberNotEqual(phone1, phone2, true);
        assertPhoneNumberNotEqual(phone1, phone2, false);
    }
    
    private void assertPhoneNumberNotEqual(String phone1, String phone2, boolean useStrict) {
        phoneNumberCompare(phone1, phone2, false, useStrict);
    }

    /**
     * Tests international matching issues for the PHONE_NUMBERS_EQUAL function.
     */
    @Suppress // PHONE_NUMBERS_EQUAL not supported
    @SmallTest
    @Test
    public void testPhoneNumbersEqualInternationl() {
        assertPhoneNumberEqual("1", "1");
        assertPhoneNumberEqual("123123", "123123");
        assertPhoneNumberNotEqual("123123", "923123");
        assertPhoneNumberNotEqual("123123", "123129");
        assertPhoneNumberNotEqual("123123", "1231234");
        assertPhoneNumberNotEqual("123123", "0123123", false);
        assertPhoneNumberNotEqual("123123", "0123123", true);
        assertPhoneNumberEqual("650-253-0000", "6502530000");
        assertPhoneNumberEqual("650-253-0000", "650 253 0000");
        assertPhoneNumberEqual("650 253 0000", "6502530000");
        assertPhoneNumberEqual("+1 650-253-0000", "6502530000");
        assertPhoneNumberEqual("001 650-253-0000", "6502530000");
        assertPhoneNumberEqual("0111 650-253-0000", "6502530000");

        // Russian trunk digit
        assertPhoneNumberEqual("+79161234567", "89161234567");

        // French trunk digit
        assertPhoneNumberEqual("+33123456789", "0123456789");

        // Trunk digit for city codes in the Netherlands
        assertPhoneNumberEqual("+31771234567", "0771234567");

        // Test broken caller ID seen on call from Thailand to the US
        assertPhoneNumberEqual("+66811234567", "166811234567");

        // Test the same in-country number with different country codes
        assertPhoneNumberNotEqual("+33123456789", "+1123456789");

        // Test one number with country code and the other without
        assertPhoneNumberEqual("5125551212", "+15125551212");

        // Test two NANP numbers that only differ in the area code
        assertPhoneNumberNotEqual("5125551212", "6505551212");

        // Japanese phone numbers
        assertPhoneNumberEqual("090-1234-5678", "+819012345678");
        assertPhoneNumberEqual("090(1234)5678", "+819012345678");
        assertPhoneNumberEqual("090-1234-5678", "+81-90-1234-5678");

        // Equador
        assertPhoneNumberEqual("+593(800)123-1234", "8001231234");
        assertPhoneNumberEqual("+593-2-1234-123", "21234123");

        // Two continuous 0 at the beginning of the phone string should not be
        // treated as trunk prefix in the strict comparation.
        assertPhoneNumberEqual("008001231234", "8001231234", false);
        assertPhoneNumberNotEqual("008001231234", "8001231234", true);

        // Confirm that the bug found before does not re-appear in the strict compalation
        assertPhoneNumberEqual("080-1234-5678", "+819012345678", false);
        assertPhoneNumberNotEqual("080-1234-5678", "+819012345678", true);
    }

    @MediumTest
    @Test
    public void testCopyString() {
        mDatabase.execSQL("CREATE TABLE guess (numi INTEGER, numf FLOAT, str TEXT);");
        mDatabase.execSQL(
                "INSERT INTO guess (numi,numf,str) VALUES (0,0.0,'ZoomZoomZoomZoom');");
        mDatabase.execSQL("INSERT INTO guess (numi,numf,str) VALUES (2000000000,3.1415926535,'');");
        String chinese = "京仅 尽径惊";
        String[] arr = new String[1];
        arr[0] = chinese;
        mDatabase.execSQL("INSERT INTO guess (numi,numf,str) VALUES (-32768,-1.0,?)", arr);

        SQLiteCursor c;

        c = mDatabase.rawQuery("SELECT numi, numf, str FROM guess", null);
        
        c.moveToFirst();
        
        CharArrayBuffer buf = new CharArrayBuffer(14);
        
        int numiIdx = 0;
        String compareTo = c.getString(numiIdx);
        int numfIdx = 1;
        int strIdx = 2;
        
        c.copyStringToBuffer(numiIdx, buf);
        assertEquals(1, buf.sizeCopied);
        assertEquals(compareTo, new String(buf.data, 0, buf.sizeCopied));
        
        c.copyStringToBuffer(strIdx, buf);
        assertEquals("ZoomZoomZoomZoom", new String(buf.data, 0, buf.sizeCopied));
        
        c.moveToNext();
        compareTo = c.getString(numfIdx);
        
        c.copyStringToBuffer(numfIdx, buf);
        assertEquals(compareTo, new String(buf.data, 0, buf.sizeCopied));
        c.copyStringToBuffer(strIdx, buf);
        assertEquals(0, buf.sizeCopied);
        
        c.moveToNext();
        c.copyStringToBuffer(numfIdx, buf);
        assertEquals(Double.valueOf(-1.0), Double.valueOf(
            new String(buf.data, 0, buf.sizeCopied)));
        
        c.copyStringToBuffer(strIdx, buf);
        compareTo = c.getString(strIdx);
        assertEquals(chinese, compareTo);
       
        assertEquals(chinese, new String(buf.data, 0, buf.sizeCopied));
        c.close();
    }
    
    @MediumTest
    @Test
    public void testSchemaChange1() {
        SQLiteDatabase db1 = mDatabase;
        SQLiteCursor cursor;

        db1.execSQL("CREATE TABLE db1 (_id INTEGER PRIMARY KEY, data TEXT);");

        cursor = db1.query("SELECT * FROM db1");
        assertNotNull("Cursor is null", cursor);

        db1.execSQL("CREATE TABLE db2 (_id INTEGER PRIMARY KEY, data TEXT);");

        assertEquals(0, cursor.getCount());
        cursor.close();
    }

    @MediumTest
    @Test
    public void testSchemaChange2() {
        mDatabase.execSQL("CREATE TABLE db1 (_id INTEGER PRIMARY KEY, data TEXT);");
        SQLiteCursor cursor = mDatabase.query("SELECT * FROM db1");
        assertNotNull(cursor);
        assertEquals(0, cursor.getCount());
        cursor.close();
    }

    @MediumTest
    @Test
    public void testSchemaChange3() {
        mDatabase.execSQL("CREATE TABLE db1 (_id INTEGER PRIMARY KEY, data TEXT);");
        mDatabase.execSQL("INSERT INTO db1 (data) VALUES ('test');");
        mDatabase.execSQL("ALTER TABLE db1 ADD COLUMN blah int;");
        try (SQLiteCursor ignored = mDatabase.rawQuery("select blah from db1", null)) {
        } catch (SQLiteException e) {
            fail("unexpected exception: " + e.getMessage());
        }
    }

    @MediumTest
    @Test
    public void testSelectionArgs() {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, data TEXT);");
        ContentValues values = new ContentValues(1);
        values.put("data", "don't forget to handled 's");
        mDatabase.insert("test", "data", values);
        values.clear();
        values.put("data", "no apostrophes here");
        mDatabase.insert("test", "data", values);
        SQLiteCursor c = mDatabase.query("SELECT * FROM test WHERE data GLOB ?", new Object[]{"*'*"});
        assertEquals(1, c.getCount());
        assertTrue(c.moveToFirst());
        assertEquals("don't forget to handled 's", c.getString(1));
        c.close();
    }

    @Suppress // unicode collator not supported yet
    @MediumTest
    @Test
    public void testTokenize() {
        mDatabase.execSQL("CREATE TABLE tokens (" +
                "token TEXT COLLATE unicode," +
                "source INTEGER," +
                "token_index INTEGER," +
                "tag TEXT" +
                ");");
        mDatabase.execSQL("CREATE TABLE tokens_no_index (" +
                "token TEXT COLLATE unicode," +
                "source INTEGER" +
                ");");
        
        Assert.assertEquals(0, longForQuery(mDatabase,
                "SELECT _TOKENIZE(NULL, NULL, NULL, NULL)", null));
        Assert.assertEquals(0, longForQuery(mDatabase,
                "SELECT _TOKENIZE('tokens', NULL, NULL, NULL)", null));
        Assert.assertEquals(0, longForQuery(mDatabase,
                "SELECT _TOKENIZE('tokens', 10, NULL, NULL)", null));
        Assert.assertEquals(0, longForQuery(mDatabase,
                "SELECT _TOKENIZE('tokens', 10, 'some string', NULL)", null));
     
        Assert.assertEquals(3, longForQuery(mDatabase,
                "SELECT _TOKENIZE('tokens', 11, 'some string ok', ' ', 1, 'foo')", null));
        Assert.assertEquals(2, longForQuery(mDatabase,
                "SELECT _TOKENIZE('tokens', 11, 'second field', ' ', 1, 'bar')", null));

        Assert.assertEquals(3, longForQuery(mDatabase,
                "SELECT _TOKENIZE('tokens_no_index', 20, 'some string ok', ' ')", null));
        Assert.assertEquals(3, longForQuery(mDatabase,
                "SELECT _TOKENIZE('tokens_no_index', 21, 'foo bar baz', ' ', 0)", null));

        // test Chinese
        String chinese = "京仅 尽径惊";
        Assert.assertEquals(2, longForQuery(mDatabase,
                "SELECT _TOKENIZE('tokens', 12,'" + chinese + "', ' ', 1)", null));
        
        String icustr = "Frédéric Hjønnevåg";
        
        Assert.assertEquals(2, longForQuery(mDatabase,
                "SELECT _TOKENIZE('tokens', 13, '" + icustr + "', ' ', 1)", null));
        
        Assert.assertEquals(9, longForQuery(mDatabase,
                "SELECT count(*) from tokens;", null));      

        String key = DatabaseUtils.getHexCollationKey("Frederic Hjonneva");
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT count(*) from tokens where token GLOB '" + key + "*'", null));      
        Assert.assertEquals(13, longForQuery(mDatabase,
                "SELECT source from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(0, longForQuery(mDatabase,
                "SELECT token_index from tokens where token GLOB '" + key + "*'", null));
        key = DatabaseUtils.getHexCollationKey("Hjonneva");
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT count(*) from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(13, longForQuery(mDatabase,
                "SELECT source from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT token_index from tokens where token GLOB '" + key + "*'", null));
        
        key = DatabaseUtils.getHexCollationKey("some string ok");
        Assert.assertEquals(1,  longForQuery(mDatabase,
                "SELECT count(*) from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(11, longForQuery(mDatabase,
                "SELECT source from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(0, longForQuery(mDatabase,
                "SELECT token_index from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals("foo", stringForQuery(mDatabase,
                "SELECT tag from tokens where token GLOB '" + key + "*'", null));
        key = DatabaseUtils.getHexCollationKey("string");
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT count(*) from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(11, longForQuery(mDatabase,
                "SELECT source from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT token_index from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals("foo", stringForQuery(mDatabase,
                "SELECT tag from tokens where token GLOB '" + key + "*'", null));
        key = DatabaseUtils.getHexCollationKey("ok");
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT count(*) from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(11, longForQuery(mDatabase,
                "SELECT source from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(2, longForQuery(mDatabase,
                "SELECT token_index from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals("foo", stringForQuery(mDatabase,
                "SELECT tag from tokens where token GLOB '" + key + "*'", null));

        key = DatabaseUtils.getHexCollationKey("second field");
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT count(*) from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(11, longForQuery(mDatabase,
                "SELECT source from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(0, longForQuery(mDatabase,
                "SELECT token_index from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals("bar", stringForQuery(mDatabase,
                "SELECT tag from tokens where token GLOB '" + key + "*'", null));
        key = DatabaseUtils.getHexCollationKey("field");
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT count(*) from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(11, longForQuery(mDatabase,
                "SELECT source from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT token_index from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals("bar", stringForQuery(mDatabase,
                "SELECT tag from tokens where token GLOB '" + key + "*'", null));

        key = DatabaseUtils.getHexCollationKey(chinese);
        String[] a = new String[1];
        a[0] = key;
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT count(*) from tokens where token= ?", a));
        Assert.assertEquals(12, longForQuery(mDatabase,
                "SELECT source from tokens where token= ?", a));
        Assert.assertEquals(0, longForQuery(mDatabase,
                "SELECT token_index from tokens where token= ?", a));
        a[0] += "*";
        Assert.assertEquals(1, longForQuery(mDatabase,
             "SELECT count(*) from tokens where token GLOB ?", a));        
        Assert.assertEquals(12, longForQuery(mDatabase,
                "SELECT source from tokens where token GLOB ?", a));
        Assert.assertEquals(0, longForQuery(mDatabase,
                "SELECT token_index from tokens where token GLOB ?", a));

       Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT count(*) from tokens where token= '" + key + "'", null));
       Assert.assertEquals(12, longForQuery(mDatabase,
               "SELECT source from tokens where token= '" + key + "'", null));
       Assert.assertEquals(0, longForQuery(mDatabase,
               "SELECT token_index from tokens where token= '" + key + "'", null));
        
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT count(*) from tokens where token GLOB '" + key + "*'", null));        
        Assert.assertEquals(12, longForQuery(mDatabase,
                "SELECT source from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(0, longForQuery(mDatabase,
                "SELECT token_index from tokens where token GLOB '" + key + "*'", null));
        
        key = DatabaseUtils.getHexCollationKey("京仅");
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT count(*) from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(12, longForQuery(mDatabase,
                "SELECT source from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(0, longForQuery(mDatabase,
                "SELECT token_index from tokens where token GLOB '" + key + "*'", null));
        
        key = DatabaseUtils.getHexCollationKey("尽径惊");
        Log.d("DatabaseGeneralTest", "key = " + key);
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT count(*) from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(12, longForQuery(mDatabase,
                "SELECT source from tokens where token GLOB '" + key + "*'", null));
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT token_index from tokens where token GLOB '" + key + "*'", null));
        
        Assert.assertEquals(0, longForQuery(mDatabase,
                "SELECT count(*) from tokens where token GLOB 'ab*'", null));        

        key = DatabaseUtils.getHexCollationKey("some string ok");
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT count(*) from tokens_no_index where token GLOB '" + key + "*'", null));
        Assert.assertEquals(20, longForQuery(mDatabase,
                "SELECT source from tokens_no_index where token GLOB '" + key + "*'", null));

        key = DatabaseUtils.getHexCollationKey("bar");
        Assert.assertEquals(1, longForQuery(mDatabase,
                "SELECT count(*) from tokens_no_index where token GLOB '" + key + "*'", null));
        Assert.assertEquals(21, longForQuery(mDatabase,
                "SELECT source from tokens_no_index where token GLOB '" + key + "*'", null));
    }
    
    @MediumTest
    @Test
    public void testTransactions() {
        mDatabase.execSQL("CREATE TABLE test (num INTEGER);");
        mDatabase.execSQL("INSERT INTO test (num) VALUES (0)");

        // Make sure that things work outside an explicit transaction.
        setNum(1);
        checkNum(1);

        // Test a single-level transaction.
        setNum(0);
        mDatabase.beginTransaction();
        setNum(1);
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
        checkNum(1);

        // Test a rolled-back transaction.
        setNum(0);
        mDatabase.beginTransaction();
        setNum(1);
        mDatabase.endTransaction();
        checkNum(0);

        // We should get an error if we end a non-existent transaction.
        assertThrowsIllegalState(() -> mDatabase.endTransaction());

        // We should get an error if a set a non-existent transaction as clean.
        assertThrowsIllegalState(() -> mDatabase.setTransactionSuccessful());

        mDatabase.beginTransaction();
        mDatabase.setTransactionSuccessful();
        // We should get an error if we mark a transaction as clean twice.
        assertThrowsIllegalState(() -> mDatabase.setTransactionSuccessful());
        // We should get an error if we begin a transaction after marking the parent as clean.
        assertThrowsIllegalState(() -> mDatabase.beginTransaction());
        mDatabase.endTransaction();

        // Test a two-level transaction.
        setNum(0);
        mDatabase.beginTransaction();
        mDatabase.beginTransaction();
        setNum(1);
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
        checkNum(1);

        // Test rolling back an inner transaction.
        setNum(0);
        mDatabase.beginTransaction();
        mDatabase.beginTransaction();
        setNum(1);
        mDatabase.endTransaction();
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
        checkNum(0);

        // Test rolling back an outer transaction.
        setNum(0);
        mDatabase.beginTransaction();
        mDatabase.beginTransaction();
        setNum(1);
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
        mDatabase.endTransaction();
        checkNum(0);
    }

    private void setNum(int num) {
        mDatabase.execSQL("UPDATE test SET num = " + num);
    }

    private void checkNum(int num) {
        Assert.assertEquals(
                num, longForQuery(mDatabase, "SELECT num FROM test", null));
    }

    private void assertThrowsIllegalState(Runnable r) {
        boolean ok = false;
        try {
            r.run();
        } catch (IllegalStateException e) {
            ok = true;
        }
        Assert.assertTrue(ok);
    }

    @MediumTest
    @Test
    public void testContentValues() {
        ContentValues values = new ContentValues();
        values.put("string", "value");
        assertEquals("value", values.getAsString("string"));
        byte[] bytes = new byte[42];
        Arrays.fill(bytes, (byte) 0x28);
        values.put("byteArray", bytes);
        assertArrayEquals(bytes, values.getAsByteArray("byteArray"));

        // Write the ContentValues to a Parcel and then read them out
        Parcel p = Parcel.obtain();
        values.writeToParcel(p, 0);
        p.setDataPosition(0);
        values = ContentValues.CREATOR.createFromParcel(p);

        // Read the values out again and make sure they're the same
        assertArrayEquals(bytes, values.getAsByteArray("byteArray"));
        assertEquals("value", values.get("string"));
    }

    public static final int TABLE_INFO_PRAGMA_COLUMNNAME_INDEX = 1;
    public static final int TABLE_INFO_PRAGMA_DEFAULT_INDEX = 4;

    @MediumTest
    @Test
    public void testTableInfoPragma() {
        mDatabase.execSQL("CREATE TABLE pragma_test (" +
                "i INTEGER DEFAULT 1234, " +
                "j INTEGER, " +
                "s TEXT DEFAULT 'hello', " +
                "t TEXT, " +
                "'select' TEXT DEFAULT \"hello\")");
        try {
            SQLiteCursor cur = mDatabase.rawQuery("PRAGMA table_info(pragma_test)", null);
            Assert.assertEquals(5, cur.getCount());

            Assert.assertTrue(cur.moveToNext());
            Assert.assertEquals("i",
                    cur.getString(TABLE_INFO_PRAGMA_COLUMNNAME_INDEX));
            Assert.assertEquals("1234",
                    cur.getString(TABLE_INFO_PRAGMA_DEFAULT_INDEX));

            Assert.assertTrue(cur.moveToNext());
            Assert.assertEquals("j",
                    cur.getString(TABLE_INFO_PRAGMA_COLUMNNAME_INDEX));
            Assert.assertNull(cur.getString(TABLE_INFO_PRAGMA_DEFAULT_INDEX));

            Assert.assertTrue(cur.moveToNext());
            Assert.assertEquals("s",
                    cur.getString(TABLE_INFO_PRAGMA_COLUMNNAME_INDEX));
            Assert.assertEquals("'hello'",
                    cur.getString(TABLE_INFO_PRAGMA_DEFAULT_INDEX));

            Assert.assertTrue(cur.moveToNext());
            Assert.assertEquals("t",
                    cur.getString(TABLE_INFO_PRAGMA_COLUMNNAME_INDEX));
            Assert.assertNull(cur.getString(TABLE_INFO_PRAGMA_DEFAULT_INDEX));

            Assert.assertTrue(cur.moveToNext());
            Assert.assertEquals("select",
                    cur.getString(TABLE_INFO_PRAGMA_COLUMNNAME_INDEX));
            Assert.assertEquals("\"hello\"",
                    cur.getString(TABLE_INFO_PRAGMA_DEFAULT_INDEX));

            cur.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(
                    "If you see this test fail, it's likely that something about " +
                    "sqlite's PRAGMA table_info(...) command has changed.", t);
        }
    }

    @MediumTest
    @Test
    public void testSemicolonsInStatements() {
        mDatabase.execSQL("CREATE TABLE pragma_test (" +
                "i INTEGER DEFAULT 1234, " +
                "j INTEGER, " +
                "s TEXT DEFAULT 'hello', " +
                "t TEXT, " +
                "'select' TEXT DEFAULT \"hello\")");
        try {
            // ending the sql statement with  semicolons shouldn't be a problem.
            SQLiteCursor cur = mDatabase.rawQuery("PRAGMA database_list;", null);
            cur.close();
            // two semicolons in the statement shouldn't be a problem.
            cur = mDatabase.rawQuery("PRAGMA database_list;;", null);
            cur.close();
        } catch (Throwable t) {
            fail("unexpected, of course");
        }
    }

    @MediumTest
    @Test
    public void testUnionsWithBindArgs() {
        /* make sure unions with bindargs work http://b/issue?id=1061291 */
        mDatabase.execSQL("CREATE TABLE A (i int);");
        mDatabase.execSQL("create table B (k int);");
        mDatabase.execSQL("create table C (n int);");
        mDatabase.execSQL("insert into A values(1);");
        mDatabase.execSQL("insert into A values(2);");
        mDatabase.execSQL("insert into A values(3);");
        mDatabase.execSQL("insert into B values(201);");
        mDatabase.execSQL("insert into B values(202);");
        mDatabase.execSQL("insert into B values(203);");
        mDatabase.execSQL("insert into C values(901);");
        mDatabase.execSQL("insert into C values(902);");
        String s = "select i from A where i > 2 " +
                "UNION select k from B where k > 201 " +
                "UNION select n from C where n !=900;";
        SQLiteCursor c = mDatabase.rawQuery(s, null);
        int n = c.getCount();
        c.close();
        String s1 = "select i from A where i > ? " +
                "UNION select k from B where k > ? " +
                "UNION select n from C where n != ?;";
        SQLiteCursor c1 = mDatabase.rawQuery(s1, new String[]{"2", "201", "900"});
        assertEquals(n, c1.getCount());
        c1.close();
    }

    /**
     * This test is available only when the platform has a locale with the language "ja".
     * It finishes without failure when it is not available.  
     */
    @Suppress
    @MediumTest
    @Test
    public void testCollateLocalizedForJapanese() {
        final String testName = "DatabaseGeneralTest#testCollateLocalizedForJapanese()";
        final Locale[] localeArray = Locale.getAvailableLocales();
        final String japanese = Locale.JAPANESE.getLanguage();
        final String english = Locale.ENGLISH.getLanguage();
        Locale japaneseLocale = null;
        Locale englishLocale = null;
        for (Locale locale : localeArray) {
            if (locale != null) {
                final String language = locale.getLanguage();
                if (language.equals(japanese)) {
                    japaneseLocale = locale;
                } else if (language.equals(english)) {
                    englishLocale = locale;
                }
            }
            
            if (japaneseLocale != null && englishLocale != null) {
                break;
            }
        }

        if (japaneseLocale == null || englishLocale == null) {
            Log.d(TAG, testName + "n is silently skipped since " +
                    (englishLocale == null ?
                            japaneseLocale == null ?
                                    "Both English and Japanese locales do not exist." :
                                    "English locale does not exist." :
                            "Japanese locale does not exist."));
            return;
        }

        Locale originalLocale = Locale.getDefault();
        try {

            final String dbName = "collate_localized_test";
            mDatabase.execSQL("CREATE TABLE " + dbName + " (" +
                    "_id INTEGER PRIMARY KEY, " +
                    "s TEXT COLLATE LOCALIZED) ");
            //DatabaseUtils.InsertHelper ih =
            //    new DatabaseUtils.InsertHelper(mDatabase, dbName);
            ContentValues cv;

            cv = new ContentValues();  //
            cv.put("s", "ｵｷﾅﾜ");  // O-ki-na-wa in half-width Katakana
            //ih.insert(cv);

            cv = new ContentValues();  //
            cv.put("s", "にほん");  // Ni-ho-n in Hiragana
            //ih.insert(cv);

            cv = new ContentValues();  //
            cv.put("s", "アメリカ");  // A-me-ri-ca in hull-width Katakana
            //ih.insert(cv);

            // Assume setLocale() does REINDEX and an English locale does not consider
            // Japanese-specific LOCALIZED order.
            Locale.setDefault(englishLocale);
            Locale.setDefault(japaneseLocale);

            SQLiteCursor cur = mDatabase.rawQuery(
                    "SELECT * FROM " + dbName + " ORDER BY s", null);
            assertTrue(cur.moveToFirst());
            assertEquals("アメリカ", cur.getString(1));
            assertTrue(cur.moveToNext());
            assertEquals("ｵｷﾅﾜ", cur.getString(1));
            assertTrue(cur.moveToNext());
            assertEquals("にほん", cur.getString(1));
        } finally {
            try {
                Locale.setDefault(originalLocale);
            } catch (Exception ignored) {
            }
        }
    }

    @LargeTest
    @Test
    public void testDefaultDatabaseErrorHandler() {
        DefaultDatabaseErrorHandler errorHandler = new DefaultDatabaseErrorHandler();

        // close the database. and call corruption handler.
        // it should delete the database file.
        File dbfile = new File(mDatabase.getPath());
        mDatabase.close();
        assertFalse(mDatabase.isOpen());
        assertTrue(dbfile.exists());
        try {
            errorHandler.onCorruption(mDatabase);
            assertFalse(dbfile.exists());
        } catch (Exception e) {
            fail("unexpected");
        }

        // create an in-memory database. and corruption handler shouldn't try to delete it
        SQLiteDatabase memoryDb = SQLiteDatabase.openOrCreateDatabase(":memory:", null);
        assertNotNull(memoryDb);
        memoryDb.close();
        assertFalse(memoryDb.isOpen());
        try {
            errorHandler.onCorruption(memoryDb);
        } catch (Exception e) {
            throw new AssertionError("unexpected", e);
        }

        // create a database, keep it open, call corruption handler. database file should be deleted
        SQLiteDatabase dbObj = SQLiteDatabase.openOrCreateDatabase(mDatabase.getPath(), null);
        assertTrue(dbfile.exists());
        assertNotNull(dbObj);
        assertTrue(dbObj.isOpen());
        try {
            errorHandler.onCorruption(dbObj);
            assertFalse(dbfile.exists());
        } catch (Exception e) {
            throw new AssertionError("unexpected", e);
        }
    }

    /**
     * Utility method to run the query on the db and return the value in the
     * first column of the first row.
     */
    public static long longForQuery(SQLiteDatabase db, String query, String[] selectionArgs) {
        try (SQLiteProgram prog = db.compileStatement(query)) {
            return longForQuery(prog, selectionArgs);
        }
    }

    /**
     * Utility method to run the pre-compiled query and return the value in the
     * first column of the first row.
     */
    public static long longForQuery(SQLiteProgram prog, String[] selectionArgs) {
        prog.bindAllArgsAsStrings(selectionArgs);
        return prog.simpleQueryForLong();
    }

    /**
     * Utility method to run the query on the db and return the value in the
     * first column of the first row.
     */
    public static String stringForQuery(SQLiteDatabase db, String query, String[] selectionArgs) {
        try (SQLiteProgram prog = db.compileStatement(query)) {
            return stringForQuery(prog, selectionArgs);
        }
    }

    /**
     * Utility method to run the pre-compiled query and return the value in the
     * first column of the first row.
     */
    public static String stringForQuery(SQLiteProgram prog, String[] selectionArgs) {
        prog.bindAllArgsAsStrings(selectionArgs);
        return prog.simpleQueryForString();
    }

}
