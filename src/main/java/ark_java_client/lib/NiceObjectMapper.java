package ark_java_client.lib;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class NiceObjectMapper {

    private final ObjectMapper objectMapper;

    public NiceObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String writeValueAsString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write object as json: " + object, e);
        }
    }
}
