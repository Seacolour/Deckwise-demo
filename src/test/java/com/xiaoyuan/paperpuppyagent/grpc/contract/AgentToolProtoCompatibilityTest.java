package com.xiaoyuan.paperpuppyagent.grpc.contract;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.xiaoyuan.paperpuppyagent.grpc.backend.BackendToolServiceProto;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks the Agent Tool wire contract independently of how messages are split across proto files.
 *
 * <p>The Agent repository owns the same test and expected fingerprint. Any RPC, message field,
 * field number, cardinality, or type change must therefore be updated deliberately in both repos.</p>
 */
class AgentToolProtoCompatibilityTest {

    private static final String EXPECTED_CONTRACT_SHA256 =
            "d4d0b9d3d91204b45f84990440ffddd8b0ec9a92674a3a90b221a771febfcffd";

    @Test
    void descriptorContractMatchesSharedFingerprint() {
        assertEquals(EXPECTED_CONTRACT_SHA256, contractSha256());
    }

    private static String contractSha256() {
        String contract = normalizedContract(
                BackendToolServiceProto.getDescriptor().findServiceByName("AgentToolService"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(contract.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String normalizedContract(ServiceDescriptor service) {
        StringBuilder value = new StringBuilder();
        value.append("service:").append(service.getFullName()).append('\n');

        List<MethodDescriptor> methods = new ArrayList<>(service.getMethods());
        methods.sort(Comparator.comparing(MethodDescriptor::getName));
        for (MethodDescriptor method : methods) {
            value.append("rpc:")
                    .append(method.getName()).append(':')
                    .append(method.getInputType().getFullName()).append("->")
                    .append(method.getOutputType().getFullName()).append(':')
                    .append(method.isClientStreaming()).append(':')
                    .append(method.isServerStreaming()).append('\n');
        }

        Map<String, FileDescriptor> files = new TreeMap<>();
        collectFiles(service.getFile(), files);
        List<Descriptor> messages = new ArrayList<>();
        for (FileDescriptor file : files.values()) {
            for (Descriptor message : file.getMessageTypes()) {
                collectMessages(message, messages);
            }
        }
        messages.sort(Comparator.comparing(Descriptor::getFullName));

        for (Descriptor message : messages) {
            value.append("message:").append(message.getFullName()).append('\n');
            List<FieldDescriptor> fields = new ArrayList<>(message.getFields());
            fields.sort(Comparator.comparingInt(FieldDescriptor::getNumber));
            for (FieldDescriptor field : fields) {
                value.append("field:")
                        .append(field.getNumber()).append(':')
                        .append(field.getName()).append(':')
                        .append(field.getType()).append(':')
                        .append(field.isRepeated()).append(':')
                        .append(field.toProto().getProto3Optional()).append(':')
                        .append(field.getContainingOneof() == null ? "" : field.getContainingOneof().getName())
                        .append(':');
                if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
                    value.append(field.getMessageType().getFullName());
                } else if (field.getJavaType() == FieldDescriptor.JavaType.ENUM) {
                    value.append(field.getEnumType().getFullName());
                }
                value.append('\n');
            }
        }
        return value.toString();
    }

    private static void collectFiles(FileDescriptor file, Map<String, FileDescriptor> files) {
        if (files.putIfAbsent(file.getName(), file) != null) {
            return;
        }
        for (FileDescriptor dependency : file.getDependencies()) {
            collectFiles(dependency, files);
        }
    }

    private static void collectMessages(Descriptor message, List<Descriptor> messages) {
        messages.add(message);
        for (Descriptor nested : message.getNestedTypes()) {
            collectMessages(nested, messages);
        }
    }
}
