package com.walker.servestatic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import io.vertx.core.AsyncResult;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.gradle.api.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.impldep.org.mozilla.javascript.ast.NewExpression;
import org.gradle.internal.impldep.org.mozilla.javascript.ast.VariableDeclaration;

import javax.annotation.processing.Generated;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class GenerateResourceModuleTask extends DefaultTask {
    private FileCollection webResources;
    private File packageDirectory;
    private File moduleDirectory;
    private String sourcePackage;
    private String name;
    private List<String> encodings;

    @InputFiles
    public FileCollection getWebResources() {
        return webResources;
    }

    public void setWebResources(FileCollection webResources) {
        this.webResources = webResources;
    }

    @OutputDirectory
    public File getPackageDirectory() {
        return packageDirectory;
    }

    public void setPackageDirectory(File packageDirectory) {
        this.packageDirectory = packageDirectory;
    }

    @InputDirectory
    public File getModuleDirectory() {
        return moduleDirectory;
    }

    public void setModuleDirectory(File moduleDirectory) {
        this.moduleDirectory = moduleDirectory;
    }

    public String getNameProp() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourcePackage() {
        return sourcePackage;
    }

    public void setSourcePackage(String sourcePackage) {
        this.sourcePackage = sourcePackage;
    }

    public List<String> getEncodings() {
        return encodings;
    }

    public void setEncodings(List<String> encodings) {
        this.encodings = encodings;
    }

    private String generatedClassName() {
        return name.substring(0, 1).toUpperCase() + name.substring(1) + "Module";
    }

    private String filenameToBufferName(String filename) {
        int index;
        while((index = filename.indexOf('.')) != 1 || (index = filename.indexOf('-')) != -1) {
            filename = filename.substring(0, index) + filename.substring(index + 1, index + 2).toUpperCase() + filename.substring(index + 2);
        }
        return filename.replace("@", "");
    }

    private MethodDeclaration generateLoadClasspathResourceToBufferMethod(CompilationUnit moduleCompilationUnit, ClassOrInterfaceDeclaration moduleClass) {
        MethodDeclaration method = moduleClass.addMethod("loadClasspathResourceToBuffer", Modifier.PRIVATE, Modifier.STATIC)
                .setType(Buffer.class);

        Parameter pathParam = method.addAndGetParameter(String.class, "path");

        TryStmt tryStmt = method.createBody().addAndGetStatement(new TryStmt()).asTryStmt();
        VariableDeclarator dataDeclarator = tryStmt.getTryBlock()
                .addAndGetStatement(new VariableDeclarationExpr(new VariableDeclarator(
                        new ArrayType(new PrimitiveType(PrimitiveType.Primitive.BYTE)),
                        "data",
                        new MethodCallExpr(
                                new MethodCallExpr(
                                        new MethodCallExpr(
                                                new MethodCallExpr(
                                                        new ClassExpr(JavaParser.parseClassOrInterfaceType("Thread")),
                                                        "currentThread"
                                                ),
                                                "getContextClassLoader"
                                        ),
                                        "getResourceAsStream",
                                        new NodeList<>(pathParam.getNameAsExpression())
                                ),
                                "readAllBytes"
                        ))))
                .asExpressionStmt().getExpression().asVariableDeclarationExpr().getVariable(0);
        tryStmt.getTryBlock()
                .addStatement(new ReturnStmt(
                        new MethodCallExpr(
                                new ClassExpr(JavaParser.parseClassOrInterfaceType("Buffer")),
                                "buffer",
                                new NodeList<>(dataDeclarator.getNameAsExpression())
                        )
                ));

        CatchClause catchClause = new CatchClause();
        Parameter ioExceptionParam = catchClause.getParameter().setType(IOException.class).setName("e");
        catchClause.createBody().addStatement(new ThrowStmt(
                new ObjectCreationExpr(
                        null,
                        JavaParser.parseClassOrInterfaceType("IOException"),
                        new NodeList<>(new BinaryExpr(new StringLiteralExpr("Failed to load resource from classpath: "), pathParam.getNameAsExpression(), BinaryExpr.Operator.PLUS),
                                ioExceptionParam.getNameAsExpression())
                )
        ));

        tryStmt.getCatchClauses().add(catchClause);

        return method;
    }

    @TaskAction
    public void generateSources() {
        System.out.println("Generating " + name);

        Path generatedJavaPath = packageDirectory.toPath().resolve(generatedClassName() + ".java");

        CompilationUnit moduleCompilationUnit = new CompilationUnit(sourcePackage)
                .setStorage(generatedJavaPath)
                .addImport(Buffer.class)
                .addImport(HttpHeaders.class)
                .addImport(Router.class)
                .addImport(RoutingContext.class)
                .addImport(IOException.class)
                .addImport(HttpMethod.class)
                .addImport(AsyncResult.class);

        ClassOrInterfaceDeclaration moduleClass = moduleCompilationUnit
                .addClass(generatedClassName())
                .setPublic(true)
                .addSingleMemberAnnotation(Generated.class, "\"serve-static-resources\"")
                .setJavadocComment("Generated class to serve static files from the " + name + " web module.");

        MethodDeclaration loadResourceMethod = generateLoadClasspathResourceToBufferMethod(moduleCompilationUnit, moduleClass);
        BlockStmt staticInitializer = moduleClass.addStaticInitializer();

        // Extension -> Uncompressed filepath -> Encoding -> Buffer Name
        HashMap<String, HashMap<Path, HashMap<String, NameExpr>>> resourcesMap = new HashMap<>();

        webResources.forEach(file -> {
            String resourceName = file.getName();
            String encoding = encodings.stream().filter(enc -> file.getName().endsWith(enc)).findFirst().orElse("");

            // Get the raw filepath and extension
            Path uncompressedFilepath = file.toPath();
            if(!encoding.isEmpty()) {
                uncompressedFilepath = uncompressedFilepath.getParent()
                        .resolve(resourceName.substring(0, resourceName.length() - (encoding.length() + 1)));
            }
            String rawFilename = uncompressedFilepath.getFileName().toString();
            String rawExtension = rawFilename.substring(rawFilename.indexOf('.') + 1);

            // Add field to the class to store a buffer
            String fieldName = filenameToBufferName(resourceName);
            FieldDeclaration field = moduleClass.addField(Buffer.class, fieldName, Modifier.PUBLIC, Modifier.STATIC);

            // Add file to the resources map, to later use in the addRoutes() method
            resourcesMap.computeIfAbsent(rawExtension, ext -> new HashMap<>())
                    .computeIfAbsent(uncompressedFilepath, path -> new HashMap<>())
                    .put(encoding, field.getVariable(0).getNameAsExpression());

            // Add the file loading to the static initializer
            staticInitializer.addStatement(new AssignExpr(
                    field.getVariable(0).getNameAsExpression(),
                    new MethodCallExpr(
                            null,
                            loadResourceMethod.getName(),
                            new NodeList<>(new StringLiteralExpr(moduleDirectory.toPath().relativize(file.toPath()).toString().replace('\\', '/')))
                    ),
                    AssignExpr.Operator.ASSIGN
            ));
        });
    }
}
