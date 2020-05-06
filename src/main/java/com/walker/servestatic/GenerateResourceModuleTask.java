package com.walker.servestatic;

import org.gradle.api.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;


public class GenerateResourceModuleTask extends DefaultTask {
    private FileCollection webResources;
    private File generatedDirectory;
    private String name;
    private String[] encodings;

    @InputFiles
    public FileCollection getWebResources() {
        return webResources;
    }

    public void setWebResources(FileCollection webResources) {
        this.webResources = webResources;
    }

    @OutputDirectory
    public File getGeneratedDirectory() {
        return generatedDirectory;
    }

    public void setGeneratedDirectory(File generatedDirectory) {
        this.generatedDirectory = generatedDirectory;
    }

    public String getNameProp() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getEncodings() {
        return encodings;
    }

    public void setEncodings(String[] encodings) {
        this.encodings = encodings;
    }

    @TaskAction
    public void generateSources() {
        System.out.println("Generating " + name);
        /*File generatedDirectoryPackage = new File(generatedDirectory, "servestatic/generated");

        if(generatedDirectoryPackage.exists()) {
            generatedDirectoryPackage.delete();
        }
        generatedDirectoryPackage.mkdirs();

        webResources.forEach(file -> {

        });*/
    }
}
