package com.walker.servestatic;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import java.io.File;

public class ServeStaticPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ServeStaticExtension extension = project.getExtensions().create("serveStaticResources", ServeStaticExtension.class);

        project.getPlugins().apply("java");
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet mainSources = sourceSets.getByName("main");

        if(extension.generatedSourcesDirectory == null) {
            extension.generatedSourcesDirectory = new File(project.getBuildDir(), "generated");
        }

        mainSources.getAllJava().srcDir(extension.generatedSourcesDirectory);

        project.getTasks().register("generateResourceModules", GenerateResourceModulesTask.class);
    }
}
