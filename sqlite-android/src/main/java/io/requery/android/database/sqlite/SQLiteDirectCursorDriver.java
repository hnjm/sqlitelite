/*
 * Copyright (C) 2007 The Android Open Source Project
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

package io.requery.android.database.sqlite;

import android.os.CancellationSignal;

/**
 * A cursor driver that uses the given query directly.
 */
public final class SQLiteDirectCursorDriver implements SQLiteCursorDriver {
    private final SQLiteDatabase mDatabase;
    private final String mEditTable; 
    private final String mSql;
    private final CancellationSignal mCancellationSignal;

    public SQLiteDirectCursorDriver(SQLiteDatabase db, String sql, String editTable,
            CancellationSignal cancellationSignal) {
        mDatabase = db;
        mEditTable = editTable;
        mSql = sql;
        mCancellationSignal = cancellationSignal;
    }

    public SQLiteCursor query(Object[] selectionArgs) {
        SQLiteQuery query = new SQLiteQuery(mDatabase, mSql, selectionArgs, mCancellationSignal);
        final SQLiteCursor cursor;
        try {
            cursor = new SQLiteCursor(this, mEditTable, query);
        } catch (RuntimeException ex) {
            query.close();
            throw ex;
        }

        return cursor;
    }

    @Override
    public void cursorClosed() {
        // Do nothing
    }

    @Override
    public void cursorDeactivated() {
        // Do nothing
    }

    @Override
    public void cursorRequeried(SQLiteCursor cursor) {
        // Do nothing
    }

    @Override
    public String toString() {
        return "SQLiteDirectCursorDriver: " + mSql;
    }
}
