package com.walker.servestatic;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

import java.util.ArrayList;
import java.util.List;

public class ServeStaticPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ServeStaticExtension extension = project.getExtensions().create("web", ServeStaticExtension.class, project);

        project.getPlugins().apply("java-library");
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet mainSources = sourceSets.getByName("main");

        mainSources.getAllJava().srcDir(extension.generatedSourcesDirectory);
        mainSources.getResources().srcDir(extension.moduleDirectory);

        ArrayList<TaskProvider<GenerateResourceModuleTask>> generateTasks = new ArrayList<>();

        extension.modules.all(webModule ->
                generateTasks.add(project.getTasks().register("generate" + webModule.getName() + "WebModule",
                        GenerateResourceModuleTask.class, moduleTask -> {
                            moduleTask.setName(webModule.getName());
                            moduleTask.setSourcePackage(extension.sourcePackage.get());
                            moduleTask.setModuleDirectory(extension.moduleDirectory.get());
                            moduleTask.setPackageDirectory(extension.generatedSourcesDirectory.get());
                            moduleTask.setWebResources(webModule.getModuleFiles().get());
                            moduleTask.setEncodings(List.of(extension.contentEncodings.get()));
                        })));

        project.getTasks().register("generateWebModules").configure(task -> {
            task.doFirst(t -> {

            });

            for(TaskProvider<GenerateResourceModuleTask> tp : generateTasks) {
                task.dependsOn(tp.get());
            }

            project.getTasks().getByName("compileJava").dependsOn(task);
        });
    }
}
