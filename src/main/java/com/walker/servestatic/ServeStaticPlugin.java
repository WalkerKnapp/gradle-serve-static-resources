package com.walker.servestatic;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

public class ServeStaticPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ServeStaticExtension extension = project.getExtensions().create("web", ServeStaticExtension.class);

        project.getPlugins().apply("java-library");
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet mainSources = sourceSets.getByName("main");

        mainSources.getAllJava().srcDir(extension.generatedSourcesDirectory);

        project.getTasks().getByPath("compileJava")
                .dependsOn(project.getTasks().register("generateWebModules", task ->
                        extension.modules.forEach(webModule ->
                                task.dependsOn(project.getTasks().register("generate" + webModule.getName() + "WebModule",
                                        GenerateResourceModuleTask.class, moduleTask -> {
                                            moduleTask.setName(webModule.getName());
                                            moduleTask.setGeneratedDirectory(extension.generatedSourcesDirectory.get());
                                            moduleTask.setWebResources(webModule.getModuleFiles().get());
                                            moduleTask.setEncodings(extension.contentEncodings.get());
                                        })))));
    }
}
