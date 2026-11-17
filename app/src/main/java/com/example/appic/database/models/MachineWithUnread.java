package com.example.appic.database.models;

import androidx.room.ColumnInfo;
import androidx.room.Embedded;

public class MachineWithUnread {

    @Embedded
    public Machine machine;

    @ColumnInfo(name = "has_unread")
    public boolean hasUnread;
}