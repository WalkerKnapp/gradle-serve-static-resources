package com.walker.servestatic;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.provider.Property;

import java.io.File;

public class ServeStaticExtension {
    public final NamedDomainObjectContainer<WebModule> modules;

    public final Property<String[]> contentEncodings;

    public final Property<File> generatedSourcesDirectory;
    public final Property<String> sourcePackage;

    public final Property<File> moduleDirectory;

    public ServeStaticExtension(Project project) {
        this.contentEncodings = project.getObjects().property(String[].class);
        this.contentEncodings.set(new String[]{"br", "gzip"});

        this.generatedSourcesDirectory = project.getObjects().property(File.class);
        this.generatedSourcesDirectory.set(new File(project.getBuildDir(), "generated"));

        this.sourcePackage = project.getObjects().property(String.class);
        this.sourcePackage.set("servestatic.generated");

        this.moduleDirectory = project.getObjects().property(File.class);

        this.modules = project.container(WebModule.class, name -> new WebModule(name, project.getObjects()));
    }

    public void setContentEncodings(String[] encodings) {
        this.contentEncodings.set(encodings);
    }

    public void setGeneratedSourcesDirectory(File generatedSourcesDirectory) {
        this.generatedSourcesDirectory.set(generatedSourcesDirectory);
    }

    public void setModuleDirectory(File moduleDirectory) {
        this.moduleDirectory.set(moduleDirectory);
    }

    public File getPackageDirectory() {
        return new File(generatedSourcesDirectory.get(), sourcePackage.get().replace('.', File.pathSeparatorChar));
    }
}
