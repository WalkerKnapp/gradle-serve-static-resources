package com.walker.servestatic;

import com.fasterxml.jackson.databind.annotation.JsonAppend;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.model.ObjectFactory;

public class WebModule {
    private final String name;
    private Property<FileCollection> moduleFiles;

    public WebModule(String name, ObjectFactory objectFactory) {
        this.name = name;
        this.moduleFiles = objectFactory.property(FileCollection.class);
    }

    public void setModuleFiles(FileCollection files) {
        this.moduleFiles.set(files);
    }

    public Property<FileCollection> getModuleFiles() {
        return this.moduleFiles;
    }

    public String getName() {
        return this.name;
    }
}
