package com.xiaoyuan.paperpuppybackendintl.grpc.client.model;

import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
public class AiChatMetadata {
    String keyword;
    Map<String, String> info;
    List<AiChatLink> links;
}


