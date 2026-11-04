package com.example.base.service.report;

import com.example.base.storage.*;
import com.example.base.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component @RequiredArgsConstructor @Slf4j
public class WorkflowImages {
    private final ImageValidator validator;
    private final ImageStorage storage;
    public record Image(String key,String name,String mime,long size,String hash,int order) {}
    public record Prepared(List<ImageValidator.ValidatedImage> images,List<String> names,String hash) {}
    public Prepared prepare(String payload,List<MultipartFile> files) {
        var images=validator.validate(files); var names=new ArrayList<String>();
        var digest=new StringBuilder(payload);
        for(int i=0;i<images.size();i++) {
            String raw=Objects.toString(files.get(i).getOriginalFilename(),"image").replace('\\','/');
            String name=raw.substring(raw.lastIndexOf('/')+1); name=name.substring(0,Math.min(255,name.length()));
            names.add(name); digest.append('|').append(images.get(i).hash()).append('|').append(name);
        }
        try {return new Prepared(images,names,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(digest.toString().getBytes(StandardCharsets.UTF_8))));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    public List<Image> upload(String prefix,Prepared prepared) {
        List<Image> uploaded=new ArrayList<>();
        try {for(int i=0;i<prepared.images().size();i++) {
            var image=prepared.images().get(i); String key=prefix+"/"+UUID.randomUUID()+"."+image.extension();
            uploaded.add(new Image(key,prepared.names().get(i),image.mime(),image.bytes().length,image.hash(),i));
            storage.put(key,image.bytes(),image.mime());
        } return uploaded;} catch(RuntimeException ex){cleanup(uploaded);throw ex;}
    }
    public void cleanup(List<Image> images) {for(var image:images)try{storage.delete(image.key());}catch(RuntimeException ex){log.warn("Could not remove workflow image {}",image.key());}}
    public String read(String key) {return storage.readUrl(key,null);}
}
