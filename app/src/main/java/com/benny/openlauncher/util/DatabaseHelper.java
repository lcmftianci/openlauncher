package com.benny.openlauncher.util;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.benny.openlauncher.widget.Desktop;

import java.util.ArrayList;

public class DatabaseHelper extends SQLiteOpenHelper {
  private static final String TABLE_DESKTOP = "desktop";
  private static final String TABLE_DOCK = "dock";
  private static final String TABLE_ITEM = "item";

  private static final String COLUMN_ID = "id";
  private static final String COLUMN_TYPE = "type";
  private static final String COLUMN_LABEL = "label";
  private static final String COLUMN_X_POS = "xPosition";
  private static final String COLUMN_Y_POS = "yPosition";
  private static final String COLUMN_DATA = "data";

  private static final String SQL_CREATE_DESKTOP =
      "CREATE TABLE " + TABLE_DESKTOP + " (" +
          COLUMN_ID + " INTEGER PRIMARY KEY)";
  private static final String SQL_CREATE_DOCK =
      "CREATE TABLE " + TABLE_DOCK + " (" +
          COLUMN_ID + " INTEGER PRIMARY KEY)";
  private static final String SQL_CREATE_ITEM =
      "CREATE TABLE " + TABLE_DESKTOP + " (" +
          COLUMN_ID + " INTEGER PRIMARY KEY," +
          COLUMN_TYPE + " TEXT," +
          COLUMN_LABEL + " TEXT," +
          COLUMN_X_POS + " INTEGER," +
          COLUMN_Y_POS + " INTEGER," +
          COLUMN_DATA + " TEXT)";
  private static final String SQL_DELETE = "DROP TABLE IF EXISTS ";
  private static final String SQL_QUERY = "SELECT * FROM ";
  private SQLiteDatabase db;

  public DatabaseHelper(Context context) {
    super(context, "launcher.db", null, 1);
    db = getWritableDatabase();
  }

  public void onCreate(SQLiteDatabase db) {
    // create tables for desktop and dock
    db.execSQL(SQL_CREATE_DESKTOP);
    db.execSQL(SQL_CREATE_DOCK);
    // create table for all items
    db.execSQL(SQL_CREATE_ITEM);
  }

  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    // discard the data and start over
    db.execSQL(SQL_DELETE + TABLE_DESKTOP);
    db.execSQL(SQL_DELETE + TABLE_DOCK);
    db.execSQL(SQL_DELETE + TABLE_ITEM);
    onCreate(db);
  }

  public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    onUpgrade(db, oldVersion, newVersion);
  }

  public void addItem(boolean desktop, Desktop.Item item) {
    ContentValues itemValues = new ContentValues();
    itemValues.put(COLUMN_ID, item.idValue);
    if (item.type != Desktop.Item.Type.APP) {
      itemValues.put(COLUMN_TYPE, item.type.toString());
    } else {
      itemValues.put(COLUMN_TYPE, Desktop.Item.Type.SHORTCUT.toString());
    }
    itemValues.put(COLUMN_LABEL, item.name);
    itemValues.put(COLUMN_X_POS, item.x);
    itemValues.put(COLUMN_Y_POS, item.y);

    String concat = "";
    switch (item.type) {
      case APP:
        itemValues.put(COLUMN_DATA, item.appIntent.toString());
        break;
      case GROUP:
        // update individual items in group
        String[] array = item.items.toArray(new String[0]);
        removeItem(array);
        for (String tmp : item.items) {
          concat += tmp + "#";
        }
        itemValues.put(COLUMN_DATA, concat);
        break;
      case ACTION:
        itemValues.put(COLUMN_DATA, item.actionValue);
        break;
      case WIDGET:
        concat = Integer.toString(item.widgetID) + "#"
            + Integer.toString(item.spanX) + "#"
            + Integer.toString(item.spanY);
        itemValues.put(COLUMN_DATA, concat);
        break;
    }
    db.insert(TABLE_ITEM, null, itemValues);

    // add reference to home
    ContentValues homeValues = new ContentValues();
    homeValues.put(COLUMN_ID, item.idValue);
    if (desktop) {
      db.insert(TABLE_DESKTOP, null, homeValues);
    } {
      db.insert(TABLE_DOCK, null, homeValues);
    }
  }

  public void removeItem(String[] item) {
    db.delete(TABLE_DESKTOP, COLUMN_ID + " = ?", item);
    db.delete(TABLE_DOCK, COLUMN_ID + " = ?", item);
    db.close();
  }

  public void deleteItem(Desktop.Item item) {
    db.delete(TABLE_ITEM, COLUMN_ID + " = ?", new String[]{String.valueOf(item.idValue)});
    db.delete(TABLE_DESKTOP, COLUMN_ID + " = ?", new String[]{String.valueOf(item.idValue)});
    db.delete(TABLE_DOCK, COLUMN_ID + " = ?", new String[]{String.valueOf(item.idValue)});
  }

  public ArrayList<Desktop.Item> getDesktop(PackageManager pm) {
    String SQL_QUERY_DESKTOP = SQL_QUERY + TABLE_DESKTOP;
    Cursor cursor = db.rawQuery(SQL_QUERY_DESKTOP, null);
    return getSelection(cursor, pm);
  }

  public ArrayList<Desktop.Item> getDock(PackageManager pm) {
    String SQL_QUERY_DESKTOP = SQL_QUERY + TABLE_DOCK;
    Cursor cursor = db.rawQuery(SQL_QUERY_DESKTOP, null);
    return getSelection(cursor, pm);
  }

  public ArrayList<Desktop.Item> getSelection(Cursor cursorSelection, PackageManager pm) {
    ArrayList<Desktop.Item> selection = new ArrayList<>();
    if (cursorSelection.getCount() != 0) {
      cursorSelection.moveToFirst();
      do {
        selection.add(getItem(cursorSelection.getString(0), pm));
      } while (cursorSelection.moveToNext());
      cursorSelection.close();
    }
    return selection;
  }

  public Desktop.Item getItem(String itemID, PackageManager pm) {
    String SQL_QUERY_SPECIFIC = SQL_QUERY + TABLE_ITEM + " WHERE " + COLUMN_ID + " = " + itemID;
    Cursor cursorItem = db.rawQuery(SQL_QUERY_SPECIFIC, null);

    int id = Integer.parseInt(cursorItem.getString(0));
    String type = cursorItem.getString(1);
    String label = cursorItem.getString(2);
    int x = Integer.parseInt(cursorItem.getString(3));
    int y = Integer.parseInt(cursorItem.getString(4));
    String data = cursorItem.getString(5);
    cursorItem.close();

    Desktop.Item tmp = new Desktop.Item();
    tmp.idValue = id;
    tmp.name = label;
    tmp.x = x;
    tmp.y = y;

    // TODO: add data to item
    String[] dataSplit;
    switch (type) {
      case "SHORTCUT":
        tmp.type = Desktop.Item.Type.SHORTCUT;
        tmp.appIntent = pm.getLaunchIntentForPackage(data);
        break;
      case "GROUP":
        tmp.type = Desktop.Item.Type.GROUP;
        dataSplit = data.split("#");
        for (String tmpString : dataSplit) {
          tmp.items.add(tmpString);
        }
        break;
      case "ACTION":
        tmp.type = Desktop.Item.Type.ACTION;
        // grab the action value
        tmp.actionValue = Integer.parseInt(data);
        break;
      case "WIDGET":
        tmp.type = Desktop.Item.Type.WIDGET;
        // split the data into widgetID, spanX, and spanY
        dataSplit = data.split("#");
        tmp.widgetID = Integer.parseInt(dataSplit[0]);
        tmp.spanX = Integer.parseInt(dataSplit[1]);
        tmp.spanY = Integer.parseInt(dataSplit[2]);
        break;
    }
    return tmp;
  }
}