package com.walker.servestatic;

import org.gradle.api.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;


public class GenerateResourceModulesTask extends DefaultTask {
    private FileCollection webResources;
    private File generatedDirectory;

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

    @TaskAction
    public void generateSources() {

    }
}
