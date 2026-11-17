package com.example.appic.database.models;

import androidx.room.ColumnInfo;
import androidx.room.Embedded;

public class GroupWithUnread {

    @Embedded
    public Group group;

    @ColumnInfo(name = "has_unread")
    public boolean hasUnread;
}