package com.xiaoyuan.paperpuppybackendintl.grpc.client.model;

import lombok.Value;

@Value
public class AiChatLink {
    String title;
    String url;
    String description;
    String resources;
    String publicationInfo;
}


