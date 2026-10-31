package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

import java.util.function.Consumer;

public interface StreamingAiExecutor<P extends AiParam> {

    String stream(P param, Consumer<String> deltaConsumer);
}
