package com.xiaoyuan.paperpuppyagent.core.tool;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final Map<String,Tool> toolsByName = new ConcurrentHashMap<>();
    private final ListableBeanFactory beanFactory;

    @PostConstruct
    private void init(){
        Collection <Tool> tools = beanFactory.getBeansOfType(Tool.class).values();
        for (Tool tool:tools) register(tool);
    }

    public void register(Tool tool) {
        Objects.requireNonNull(tool,"tool");
        String name=Objects.toString(tool.name(), "").trim();
        if (name.isBlank()) throw new IllegalArgumentException("工具名不能为空");
        Tool prev=toolsByName.putIfAbsent(name, tool);
        if (prev!=null) throw new IllegalStateException("同名工具已存在: " + name);
    }

    public Tool getToolByName(String name) {
        if (name == null) return null;
        return toolsByName.get(name);
    }

    public List<Tool> list() {
        ArrayList<Tool> out = new ArrayList<>(toolsByName.values());
        out.sort(Comparator.comparing(Tool::name));
        return List.copyOf(out);
    }
}
