alter table apzda_audit_log
    add runas  varchar(32) null comment 'the userid of the user who performed this activity' after activity,
    add device varchar(64) null comment 'the device on which the activity performed' after ip;

create index idx_runas on apzda_audit_log (runas, activity);
