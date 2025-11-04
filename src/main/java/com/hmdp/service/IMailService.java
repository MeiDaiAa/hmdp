package com.hmdp.service;

public interface IMailService {
    void sendMail(String to, String subject, String content);
}
