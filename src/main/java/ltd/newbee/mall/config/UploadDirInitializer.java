package ltd.newbee.mall.config;

import ltd.newbee.mall.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 首次启动时把 classpath:upload.zip 中的演示商品图片解压到上传目录。
 * 这样新电脑/服务器不需要手动拷贝上传目录，图片开箱即用。
 */
@Component
public class UploadDirInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UploadDirInitializer.class);
    private static final String ZIP_NAME = "upload.zip";
    private static final String ZIP_PREFIX = "upload/";

    @Override
    public void run(ApplicationArguments args) {
        Path uploadDir = Paths.get(Constants.FILE_UPLOAD_DIC).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(uploadDir)) {
                if (stream.iterator().hasNext()) {
                    log.info("Upload directory already initialized: {}", uploadDir);
                    return;
                }
            }

            ClassPathResource resource = new ClassPathResource(ZIP_NAME);
            if (!resource.exists()) {
                log.warn("{} not found, skip demo image extraction", ZIP_NAME);
                return;
            }

            int count = 0;
            try (InputStream in = resource.getInputStream();
                 ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.startsWith(ZIP_PREFIX)) {
                        name = name.substring(ZIP_PREFIX.length());
                    }
                    if (name.isBlank()) {
                        continue;
                    }
                    Path target = uploadDir.resolve(name).normalize();
                    // 防止 zip slip
                    if (!target.startsWith(uploadDir)) {
                        continue;
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                        count++;
                    }
                }
            }
            log.info("Extracted {} demo product images to {}", count, uploadDir);
        } catch (Exception e) {
            log.warn("Failed to initialize upload directory: {}", e.getMessage(), e);
        }
    }
}
