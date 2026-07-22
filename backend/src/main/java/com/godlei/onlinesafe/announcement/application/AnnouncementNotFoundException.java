package com.godlei.onlinesafe.announcement.application;

public class AnnouncementNotFoundException extends RuntimeException {

    public AnnouncementNotFoundException() {
        super("公告不存在");
    }
}
