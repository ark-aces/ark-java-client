package ark_java_client.lib;

import org.springframework.core.io.DefaultResourceLoader;

import java.io.InputStream;

public class ResourceUtils {
    
    public static InputStream getInputStream(String filename) {
        InputStream fileInputStream;
        try {
            fileInputStream = new DefaultResourceLoader().getResource(filename).getInputStream();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load file: " + filename, e);
        }
        return fileInputStream;
    }
    
}
