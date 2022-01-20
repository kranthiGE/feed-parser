package com.sahikran.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sahikran.exception.ObjectStorageException;
import com.sahikran.keygenservice.UniqueKeyGenerator;
import com.sahikran.model.FeedItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ObjectStorageService implements StorageService<FeedItem> {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

    private final String outputFilePath;

    private UniqueKeyGenerator rateKeyGenerator;

    private ObjectMapper objectMapper;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper){
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setUniqueKeyGenerator(UniqueKeyGenerator rateKeyGenerator) {
        this.rateKeyGenerator = rateKeyGenerator;
    }

    @Autowired
    public ObjectStorageService(@Value("${feed.files.directory}") String outputFilePath ){
        Objects.requireNonNull(outputFilePath, "no value to the output file directory");
        this.outputFilePath = outputFilePath;
    }

    @Override
    public String save(FeedItem feedItem) {
        long key = rateKeyGenerator.generateUniqueKey();
        String fileName = String.valueOf(key);
        final Path outputFolder;
        try {
            outputFolder = createOutputPath();
        } catch (IOException e) {
            throw new ObjectStorageException("Exception when creating output folder in save() " + feedItem.toString(), e);
        }
        log.info("created output folder");
        saveFeedItemIntoFile(outputFolder, fileName, feedItem);
        log.info("FeedItem saved");
        return fileName;
    }

    public void saveFeedItemIntoFile(Path outputFolder, String fileName, FeedItem feedItem){
        try {
            // create file in the output directory
            Path output = Path.of(outputFolder.toString(), fileName);
            //objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            objectMapper.writeValue(Files.newBufferedWriter(output), feedItem);
        } catch (IOException e) {
            throw new ObjectStorageException("Exception in save() " + feedItem.toString(), e);
        }
    }

    @Override
    public boolean save(List<FeedItem> feedItems) {
        log.info("Received feeds " + feedItems.size());
        final Path outputFolder;
        try {
            outputFolder = createOutputPath();
        } catch (IOException e) {
            throw new ObjectStorageException("Exception when creating output folder in saveAll() ", e);
        }
        log.info("created output folder");
        // create individual files for each feeditem and save them locally
        // once they reach a maximum, push them to s3 asynchronously using spring event
        feedItems.stream().parallel().forEach(
            feedItem -> {
                long fileNameKey = rateKeyGenerator.generateUniqueKey();
                saveFeedItemIntoFile(outputFolder, String.valueOf(fileNameKey), feedItem);
            }
        );
        return true;
    }

    public Path createOutputPath() throws IOException{
        return Files.createDirectories(Path.of(outputFilePath));
    }

}
